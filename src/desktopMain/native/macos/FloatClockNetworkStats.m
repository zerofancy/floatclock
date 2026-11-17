#import <Foundation/Foundation.h>
#import <dispatch/dispatch.h>
#import <dlfcn.h>
#import <ifaddrs.h>
#import <jni.h>
#import <net/if.h>
#import <net/if_dl.h>
#import <net/if_types.h>
#import <net/route.h>
#import <netinet/in.h>

// NetworkStatistics is loaded at runtime so unsupported systems can keep running the clock.
typedef struct {
    void *(*create)(CFAllocatorRef, dispatch_queue_t, void (^)(void *));
    void (*destroy)(void *);
    void (*addRoutes)(void *);
    void (*setDescription)(void *, void (^)(CFDictionaryRef));
    void (*setCounts)(void *, void (^)(CFDictionaryRef));
    void (*setRemoved)(void *, void (^)(void));
    void (*queryDescription)(void *);
    void (*queryAll)(void *, void (^)(void));
    CFStringRef routeID, parentID, interface, flags, destination, mask, received, sent;
} FCNetworkAPI;

static FCNetworkAPI api;
static NSString *apiError;

static void loadNetworkAPI(void) {
    static dispatch_once_t once;
    dispatch_once(&once, ^{
        void *library = dlopen("/System/Library/PrivateFrameworks/NetworkStatistics.framework/NetworkStatistics", RTLD_NOW | RTLD_LOCAL);
        if (!library) {
            apiError = @"Unable to load NetworkStatistics";
            return;
        }
#define FC_FUNCTION(field, symbol) \
        api.field = dlsym(library, symbol); \
        if (!api.field) { apiError = @"Missing NetworkStatistics function: " @symbol; return; }
        FC_FUNCTION(create, "NStatManagerCreate");
        FC_FUNCTION(destroy, "NStatManagerDestroy");
        FC_FUNCTION(addRoutes, "NStatManagerAddAllRoutes");
        FC_FUNCTION(setDescription, "NStatSourceSetDescriptionBlock");
        FC_FUNCTION(setCounts, "NStatSourceSetCountsBlock");
        FC_FUNCTION(setRemoved, "NStatSourceSetRemovedBlock");
        FC_FUNCTION(queryDescription, "NStatSourceQueryDescription");
        FC_FUNCTION(queryAll, "NStatManagerQueryAllSourcesUpdate");
#undef FC_FUNCTION
#define FC_KEY(field, symbol) \
        { CFStringRef *address = dlsym(library, symbol); \
          if (!address || !*address) { apiError = @"Missing NetworkStatistics key: " @symbol; return; } \
          api.field = *address; }
        FC_KEY(routeID, "kNStatSrcKeyRouteID");
        FC_KEY(parentID, "kNStatSrcKeyRouteParentID");
        FC_KEY(interface, "kNStatSrcKeyInterface");
        FC_KEY(flags, "kNStatSrcKeyRouteFlags");
        FC_KEY(destination, "kNStatSrcKeyRouteDestination");
        FC_KEY(mask, "kNStatSrcKeyRouteMask");
        FC_KEY(received, "kNStatSrcKeyRxBytes");
        FC_KEY(sent, "kNStatSrcKeyTxBytes");
#undef FC_KEY
    });
}

static id field(NSDictionary *dictionary, CFStringRef key) {
    return dictionary[(__bridge NSString *)key];
}

static BOOL number(id value) {
    return [value isKindOfClass:[NSNumber class]];
}

typedef struct {
    uint32_t family;
    uint32_t scope;
    unsigned char bytes[16];
} FCAddress;

// Darwin may encode a link-local zone in either sin6_scope_id or the address itself.
static NSData *addressIdentity(const struct sockaddr *address, size_t length, unsigned int interface) {
    FCAddress result = {0};
    if (length < sizeof(struct sockaddr)) return nil;
    result.family = address->sa_family;
    if (address->sa_family == AF_INET && length >= sizeof(struct sockaddr_in)) {
        memcpy(result.bytes, &((const struct sockaddr_in *)address)->sin_addr, sizeof(struct in_addr));
    } else if (address->sa_family == AF_INET6 && length >= sizeof(struct sockaddr_in6)) {
        const struct sockaddr_in6 *v6 = (const struct sockaddr_in6 *)address;
        memcpy(result.bytes, &v6->sin6_addr, sizeof(struct in6_addr));
        if (IN6_IS_ADDR_LINKLOCAL(&v6->sin6_addr)) {
            result.scope = v6->sin6_scope_id;
            if (!result.scope) result.scope = ((uint32_t)result.bytes[2] << 8) | result.bytes[3];
            if (!result.scope) result.scope = interface;
            result.bytes[2] = result.bytes[3] = 0;
        }
    } else {
        return nil;
    }
    return [NSData dataWithBytes:&result length:sizeof(result)];
}

static BOOL physicalInterface(const struct ifaddrs *address) {
    if (!(address->ifa_flags & IFF_UP) || (address->ifa_flags & IFF_LOOPBACK)) return NO;
    const struct sockaddr_dl *link = (const struct sockaddr_dl *)address->ifa_addr;
    if (link->sdl_type != IFT_ETHER) return NO;
    NSString *name = [NSString stringWithUTF8String:address->ifa_name];
    for (NSString *prefix in @[@"awdl", @"llw", @"utun", @"feth", @"bridge", @"vlan", @"ap", @"p2p", @"gif", @"stf", @"vmenet", @"vmnet", @"vboxnet", @"tap", @"tun", @"nan"]) {
        if ([name hasPrefix:prefix]) return NO;
    }
    return YES;
}

static BOOL hostMask(NSData *destination, id mask) {
    if (![mask isKindOfClass:[NSData class]] || destination.length < sizeof(struct sockaddr)) return NO;
    const struct sockaddr *address = destination.bytes;
    size_t offset, length;
    if (address->sa_family == AF_INET) {
        offset = offsetof(struct sockaddr_in, sin_addr);
        length = sizeof(struct in_addr);
    } else if (address->sa_family == AF_INET6) {
        offset = offsetof(struct sockaddr_in6, sin6_addr);
        length = sizeof(struct in6_addr);
    } else {
        return NO;
    }
    NSData *data = mask;
    if (data.length < offset + length) return NO;
    const unsigned char *bytes = data.bytes;
    for (size_t i = offset; i < offset + length; i++) if (bytes[i] != 255) return NO;
    return YES;
}

@interface FloatClockNetworkRoute : NSObject
@property(nonatomic, strong) NSDictionary *descriptionFields;
@property(nonatomic, strong) NSDictionary *counts;
@property(nonatomic) uint64_t generation;
@end
@implementation FloatClockNetworkRoute
@end

@interface FloatClockNetworkRequest : NSObject
@property(nonatomic, strong) dispatch_semaphore_t done;
@property(nonatomic, strong) NSData *snapshot;
@property(nonatomic, copy) NSString *error;
@end
@implementation FloatClockNetworkRequest
@end

@interface FloatClockNetworkSession : NSObject
@property(nonatomic, strong) dispatch_queue_t queue;
@property(nonatomic, strong) NSMutableDictionary<NSValue *, FloatClockNetworkRoute *> *routes;
@property(nonatomic, strong) FloatClockNetworkRequest *pending;
@property(nonatomic) void *manager;
@property(nonatomic) uint64_t generation;
@property(nonatomic) BOOL stopped;
@property(nonatomic) BOOL timedOut;
- (NSData *)sampleWithError:(NSString **)error;
- (void)stop;
@end

@implementation FloatClockNetworkSession
- (instancetype)init {
    self = [super init];
    if (self) {
        _queue = dispatch_queue_create("top.ntutn.floatclock.network", DISPATCH_QUEUE_SERIAL);
        _routes = [NSMutableDictionary new];
        __weak FloatClockNetworkSession *weakSelf = self;
        dispatch_sync(_queue, ^{
            self.manager = api.create(kCFAllocatorDefault, self.queue, ^(void *source) {
                FloatClockNetworkSession *session = weakSelf;
                if (!session || session.stopped) return;
                NSValue *token = [NSValue valueWithPointer:source];
                FloatClockNetworkRoute *route = [FloatClockNetworkRoute new];
                session.routes[token] = route;
                api.setDescription(source, ^(CFDictionaryRef description) {
                    route.descriptionFields = [(__bridge NSDictionary *)description copy];
                });
                api.setCounts(source, ^(CFDictionaryRef counts) {
                    FloatClockNetworkSession *current = weakSelf;
                    route.counts = [(__bridge NSDictionary *)counts copy];
                    route.generation = current.pending ? current.generation : 0;
                });
                api.setRemoved(source, ^{
                    [weakSelf.routes removeObjectForKey:token];
                });
                api.queryDescription(source);
            });
            if (self.manager) api.addRoutes(self.manager);
        });
    }
    return self;
}

- (NSData *)snapshotWithError:(NSString **)error {
    struct ifaddrs *addresses = NULL;
    if (getifaddrs(&addresses) != 0) {
        *error = @"Unable to query network interface addresses";
        return nil;
    }
    NSMutableSet<NSNumber *> *interfaces = [NSMutableSet new];
    NSMutableSet<NSData *> *localAddresses = [NSMutableSet new];
    for (struct ifaddrs *item = addresses; item; item = item->ifa_next) {
        if (!item->ifa_addr) continue;
        unsigned int index = if_nametoindex(item->ifa_name);
        if (item->ifa_addr->sa_family == AF_LINK && physicalInterface(item)) [interfaces addObject:@(index)];
        NSData *identity = addressIdentity(item->ifa_addr, item->ifa_addr->sa_len, index);
        if (identity) [localAddresses addObject:identity];
    }
    freeifaddrs(addresses);

    NSMutableData *snapshot = [NSMutableData new];
    NSMutableSet *routeIDs = [NSMutableSet new];
    for (FloatClockNetworkRoute *route in self.routes.allValues) {
        id identifier = field(route.descriptionFields, api.routeID);
        if (number(identifier)) [routeIDs addObject:identifier];
    }
    for (FloatClockNetworkRoute *route in self.routes.allValues) {
        NSDictionary *description = route.descriptionFields;
        id identifier = field(description, api.routeID);
        id parent = field(description, api.parentID);
        id interface = field(description, api.interface);
        id flags = field(description, api.flags);
        if (!number(interface) || !number(parent)) {
            *error = @"Network route descriptions are not ready";
            return nil;
        }
        if (![interfaces containsObject:interface]) continue;
        if ([parent unsignedLongLongValue] != 0) {
            if (![routeIDs containsObject:parent]) {
                *error = @"A network route parent is not ready";
                return nil;
            }
            continue;
        }
        id destination = field(description, api.destination);
        id received = field(route.counts, api.received);
        id sent = field(route.counts, api.sent);
        if (!number(identifier) || !number(flags) || ![destination isKindOfClass:[NSData class]]) {
            *error = @"Network route descriptions are incomplete";
            return nil;
        }
        unsigned int routeFlags = [flags unsignedIntValue];
        if (!(routeFlags & RTF_UP) || (routeFlags & RTF_LOCAL)) continue;
        NSData *identity = addressIdentity([destination bytes], [destination length], [interface unsignedIntValue]);
        if (!identity) {
            *error = @"Unsupported network route destination";
            return nil;
        }
        BOOL host = (routeFlags & RTF_HOST) || hostMask(destination, field(description, api.mask));
        if (host && [localAddresses containsObject:identity]) continue;
        if (route.generation != self.generation || !number(received) || !number(sent)) {
            *error = @"Network route counters are not ready";
            return nil;
        }
        jlong values[] = {
            (jlong)[identifier unsignedLongLongValue], (jlong)[interface unsignedIntValue],
            (jlong)[received unsignedLongLongValue], (jlong)[sent unsignedLongLongValue],
        };
        [snapshot appendBytes:values length:sizeof(values)];
    }
    return snapshot;
}

- (NSData *)sampleWithError:(NSString **)error {
    FloatClockNetworkRequest *request = [FloatClockNetworkRequest new];
    request.done = dispatch_semaphore_create(0);
    dispatch_async(self.queue, ^{
        if (self.stopped || self.timedOut || !self.manager) {
            request.error = @"The network statistics manager is unavailable";
            dispatch_semaphore_signal(request.done);
            return;
        }
        self.pending = request;
        self.generation++;
        __weak FloatClockNetworkSession *weakSelf = self;
        api.queryAll(self.manager, ^{
            FloatClockNetworkSession *session = weakSelf;
            if (!session || session.pending != request) return;
            NSString *failure = nil;
            request.snapshot = [session snapshotWithError:&failure];
            request.error = failure;
            session.pending = nil;
            dispatch_semaphore_signal(request.done);
        });
    });
    if (dispatch_semaphore_wait(request.done, dispatch_time(DISPATCH_TIME_NOW, 3 * NSEC_PER_SEC)) != 0) {
        dispatch_async(self.queue, ^{ self.timedOut = YES; });
        *error = @"Timed out waiting for network route counters";
        return nil;
    }
    *error = request.error;
    return request.snapshot;
}

- (void)stop {
    dispatch_sync(self.queue, ^{
        self.stopped = YES;
        if (self.manager) {
            api.destroy(self.manager);
            self.manager = NULL;
        }
        [self.routes removeAllObjects];
        if (self.pending) {
            self.pending.error = @"The network statistics manager was stopped";
            dispatch_semaphore_signal(self.pending.done);
            self.pending = nil;
        }
    });
}
@end

static void throwNetworkError(JNIEnv *env, NSString *message) {
    jclass exception = (*env)->FindClass(env, "java/lang/IllegalStateException");
    if (exception) (*env)->ThrowNew(env, exception, message.UTF8String);
}

JNIEXPORT jlong JNICALL Java_top_ntutn_floatclock_macos_MacOSNetworkStats_createNative(JNIEnv *env, jobject object) {
    (void)object;
    @autoreleasepool {
        loadNetworkAPI();
        if (apiError) {
            throwNetworkError(env, apiError);
            return 0;
        }
        FloatClockNetworkSession *session = [FloatClockNetworkSession new];
        if (!session.manager) {
            [session stop];
            throwNetworkError(env, @"Unable to create the network statistics manager");
            return 0;
        }
        return (jlong)(intptr_t)(__bridge_retained void *)session;
    }
}

JNIEXPORT jlongArray JNICALL Java_top_ntutn_floatclock_macos_MacOSNetworkStats_sampleNative(JNIEnv *env, jobject object, jlong handle) {
    (void)object;
    @autoreleasepool {
        FloatClockNetworkSession *session = (__bridge FloatClockNetworkSession *)(void *)(intptr_t)handle;
        NSString *error = nil;
        NSData *snapshot = [session sampleWithError:&error];
        if (!snapshot) {
            throwNetworkError(env, error ?: @"Unable to sample network route counters");
            return NULL;
        }
        jsize count = (jsize)(snapshot.length / sizeof(jlong));
        jlongArray result = (*env)->NewLongArray(env, count);
        if (result && count) (*env)->SetLongArrayRegion(env, result, 0, count, snapshot.bytes);
        return result;
    }
}

JNIEXPORT void JNICALL Java_top_ntutn_floatclock_macos_MacOSNetworkStats_closeNative(JNIEnv *env, jobject object, jlong handle) {
    (void)env;
    (void)object;
    @autoreleasepool {
        FloatClockNetworkSession *session = (__bridge_transfer FloatClockNetworkSession *)(void *)(intptr_t)handle;
        [session stop];
    }
}
