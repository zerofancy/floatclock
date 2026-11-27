#import <Foundation/Foundation.h>
#import <dispatch/dispatch.h>
#import <ifaddrs.h>
#import <net/if.h>
#import <net/if_dl.h>
#import <net/if_types.h>
#import <netinet/in.h>

// Use a fixed interface inventory and an in-process provider; these tests do not read system traffic.
static struct ifaddrs testEthernet, testLoopback;
static struct sockaddr_dl testEthernetAddress, testLoopbackAddress;

static int testGetifaddrs(struct ifaddrs **addresses) {
    testEthernetAddress.sdl_len = sizeof(testEthernetAddress);
    testEthernetAddress.sdl_family = AF_LINK;
    testEthernetAddress.sdl_type = IFT_ETHER;
    testLoopbackAddress.sdl_len = sizeof(testLoopbackAddress);
    testLoopbackAddress.sdl_family = AF_LINK;
    testLoopbackAddress.sdl_type = IFT_LOOP;
    testEthernet.ifa_name = "en0";
    testEthernet.ifa_flags = IFF_UP;
    testEthernet.ifa_addr = (struct sockaddr *)&testEthernetAddress;
    testEthernet.ifa_next = &testLoopback;
    testLoopback.ifa_name = "lo0";
    testLoopback.ifa_flags = IFF_UP | IFF_LOOPBACK;
    testLoopback.ifa_addr = (struct sockaddr *)&testLoopbackAddress;
    testLoopback.ifa_next = NULL;
    *addresses = &testEthernet;
    return 0;
}

static void testFreeifaddrs(struct ifaddrs *addresses) { (void)addresses; }
static unsigned int testIfNametoindex(const char *name) { return strcmp(name, "en0") == 0 ? 7 : 1; }

#define getifaddrs testGetifaddrs
#define freeifaddrs testFreeifaddrs
#define if_nametoindex testIfNametoindex
#import "../../../desktopMain/native/macos/FloatClockNetworkStats.m"
#undef getifaddrs
#undef freeifaddrs
#undef if_nametoindex

@interface TestSource : NSObject
@property(nonatomic, strong) NSDictionary *descriptionFields;
@property(nonatomic, strong) NSDictionary *counts;
@property(nonatomic) BOOL sendCounts;
@property(nonatomic, copy) void (^onDescription)(CFDictionaryRef);
@property(nonatomic, copy) void (^onCounts)(CFDictionaryRef);
@property(nonatomic, copy) void (^onRemoved)(void);
@end
@implementation TestSource
@end

@interface TestManager : NSObject
@property(nonatomic, strong) NSArray<TestSource *> *sources;
@property(nonatomic, copy) void (^onSource)(void *);
@property(nonatomic, copy) void (^pendingCompletion)(void);
@end
@implementation TestManager
@end

static NSArray<TestSource *> *nextSources;
static BOOL holdQuery;
static NSUInteger queryCalls, destroyCalls;
static __weak TestManager *currentManager;

static void *testCreate(CFAllocatorRef allocator, dispatch_queue_t queue, void (^onSource)(void *)) {
    (void)allocator;
    (void)queue;
    TestManager *manager = [TestManager new];
    manager.sources = nextSources;
    manager.onSource = onSource;
    currentManager = manager;
    return (__bridge_retained void *)manager;
}

static void testDestroy(void *value) {
    TestManager *manager = (__bridge_transfer TestManager *)value;
    manager.pendingCompletion = nil;
    destroyCalls++;
}

static void testAddRoutes(void *value) {
    TestManager *manager = (__bridge TestManager *)value;
    for (TestSource *source in manager.sources) manager.onSource((__bridge void *)source);
}

static void testSetDescription(void *value, void (^callback)(CFDictionaryRef)) {
    ((__bridge TestSource *)value).onDescription = callback;
}

static void testSetCounts(void *value, void (^callback)(CFDictionaryRef)) {
    ((__bridge TestSource *)value).onCounts = callback;
}

static void testSetRemoved(void *value, void (^callback)(void)) {
    ((__bridge TestSource *)value).onRemoved = callback;
}

static void testQueryDescription(void *value) {
    TestSource *source = (__bridge TestSource *)value;
    if (source.descriptionFields) source.onDescription((__bridge CFDictionaryRef)source.descriptionFields);
}

static void testQueryAll(void *value, void (^completion)(void)) {
    queryCalls++;
    TestManager *manager = (__bridge TestManager *)value;
    if (holdQuery) {
        manager.pendingCompletion = completion;
        return;
    }
    for (TestSource *source in manager.sources) {
        if (source.sendCounts) source.onCounts((__bridge CFDictionaryRef)source.counts);
    }
    completion();
}

static void setUpProvider(NSArray<TestSource *> *sources) {
    api = (FCNetworkAPI){
        .create = testCreate, .destroy = testDestroy, .addRoutes = testAddRoutes,
        .setDescription = testSetDescription, .setCounts = testSetCounts, .setRemoved = testSetRemoved,
        .queryDescription = testQueryDescription, .queryAll = testQueryAll,
        .routeID = CFSTR("routeID"), .parentID = CFSTR("parentID"), .interface = CFSTR("interface"),
        .flags = CFSTR("flags"), .destination = CFSTR("destination"), .mask = CFSTR("mask"),
        .received = CFSTR("received"), .sent = CFSTR("sent"),
    };
    nextSources = sources;
    holdQuery = NO;
    queryCalls = destroyCalls = 0;
}

static TestSource *source(NSNumber *identifier, NSNumber *parent, NSNumber *interface) {
    struct sockaddr_in destination = {.sin_len = sizeof(destination), .sin_family = AF_INET};
    NSMutableDictionary *description = [@{
        @"flags": @(RTF_UP), @"destination": [NSData dataWithBytes:&destination length:sizeof(destination)],
    } mutableCopy];
    if (identifier) description[@"routeID"] = identifier;
    if (parent) description[@"parentID"] = parent;
    if (interface) description[@"interface"] = interface;
    TestSource *result = [TestSource new];
    result.descriptionFields = description;
    result.counts = @{@"received": @(0x100000000ULL + 1024), @"sent": @(0x100000000ULL + 2048)};
    result.sendCounts = YES;
    return result;
}

static void report(NSString *message) {
    [[NSFileHandle fileHandleWithStandardOutput] writeData:[[message stringByAppendingString:@"\n"] dataUsingEncoding:NSUTF8StringEncoding]];
}

#define REQUIRE(condition, message) do { if (!(condition)) { report(@"FAIL: " @message); return NO; } } while (0)

static BOOL excludedSourcesDoNotBlockRoot(void) {
    TestSource *root = source(@101, @0, @7);
    TestSource *child = source(@102, @999, @7);
    TestSource *virtual = source(@103, nil, @1);
    TestSource *childWithoutInterface = source(@104, @999, nil);
    child.sendCounts = virtual.sendCounts = childWithoutInterface.sendCounts = NO;
    setUpProvider(@[root, child, virtual, childWithoutInterface]);
    FloatClockNetworkSession *session = [FloatClockNetworkSession new];
    NSString *error = nil;
    NSData *snapshot = [session sampleWithError:&error];
    [session stop];
    REQUIRE(snapshot != nil && error == nil, "excluded sources blocked a healthy root");
    REQUIRE(snapshot.length == 4 * sizeof(jlong), "child or virtual counters were included");
    const jlong *values = snapshot.bytes;
    REQUIRE(values[0] == 101 && values[1] == 7, "selected the wrong root");
    REQUIRE(values[2] == 0x100000400LL && values[3] == 0x100000800LL, "truncated 64-bit counters");
    return YES;
}

static BOOL missingRootIdentityFails(void) {
    for (TestSource *unknown in @[source(@101, @0, nil), source(@101, nil, @7)]) {
        setUpProvider(@[unknown]);
        FloatClockNetworkSession *session = [FloatClockNetworkSession new];
        NSString *error = nil;
        NSData *snapshot = [session sampleWithError:&error];
        [session stop];
        REQUIRE(snapshot == nil, "missing interface or parent was treated as a complete root");
        REQUIRE([error hasPrefix:@"Network route descriptions are not ready"], "missing identity lost its diagnostic reason");
    }
    return YES;
}

static BOOL staleRootFailsAndRecovers(void) {
    TestSource *root = source(@101, @0, @7);
    setUpProvider(@[root]);
    FloatClockNetworkSession *session = [FloatClockNetworkSession new];
    NSString *error = nil;
    REQUIRE([session sampleWithError:&error] != nil, "initial root was unavailable");
    root.sendCounts = NO;
    NSData *snapshot = [session sampleWithError:&error];
    REQUIRE(snapshot == nil, "stale root counters were reused");
    NSArray<NSString *> *firstError = [error componentsSeparatedByString:@"\n"];
    REQUIRE(firstError.count == 2, "diagnostic did not separate stable reason from changing context");
    REQUIRE([firstError[0] containsString:@"routeID=101"] && [firstError[0] containsString:@"ifindex=7"], "diagnostic omitted route identity");
    REQUIRE([firstError[1] containsString:@"generation=2 routeGeneration=1"] && [firstError[1] containsString:@"received=yes sent=yes"], "diagnostic omitted stale generation context");
    snapshot = [session sampleWithError:&error];
    NSArray<NSString *> *secondError = [error componentsSeparatedByString:@"\n"];
    REQUIRE(snapshot == nil && [firstError[0] isEqualToString:secondError[0]], "changing generations changed the stable reason");
    REQUIRE([secondError[1] containsString:@"generation=3 routeGeneration=1"], "diagnostic did not advance the request generation");
    root.sendCounts = YES;
    error = nil;
    snapshot = [session sampleWithError:&error];
    [session stop];
    REQUIRE(snapshot != nil && error == nil, "updated root did not recover without rebuilding the manager");
    return YES;
}

static BOOL missingCounterFails(void) {
    TestSource *root = source(@101, @0, @7);
    root.counts = @{@"sent": @2048};
    setUpProvider(@[root]);
    FloatClockNetworkSession *session = [FloatClockNetworkSession new];
    NSString *error = nil;
    NSData *snapshot = [session sampleWithError:&error];
    [session stop];
    REQUIRE(snapshot == nil, "missing receive counter was silently replaced");
    REQUIRE([error containsString:@"received=no sent=yes"], "missing-counter context is incorrect");
    return YES;
}

static BOOL timeoutNeverOverlapsQuery(void) {
    setUpProvider(@[source(@101, @0, @7)]);
    holdQuery = YES;
    FloatClockNetworkSession *session = [FloatClockNetworkSession new];
    NSString *error = nil;
    REQUIRE([session sampleWithError:&error] == nil && [error hasPrefix:@"Timed out waiting"], "query did not time out");
    REQUIRE(queryCalls == 1, "timeout launched another query");
    REQUIRE([session sampleWithError:&error] == nil && [error hasPrefix:@"The network statistics manager is unavailable"], "timed-out manager was reused");
    REQUIRE(queryCalls == 1, "retry overlapped the outstanding query");
    dispatch_sync(session.queue, ^{
        currentManager.pendingCompletion();
        currentManager.pendingCompletion = nil;
    });
    REQUIRE([session sampleWithError:&error] == nil, "late completion cleared the timeout latch");
    REQUIRE(queryCalls == 1, "late completion allowed another query on the old manager");
    [session stop];
    REQUIRE(destroyCalls == 1, "manager was not destroyed once");
    return YES;
}

int main(void) {
    @autoreleasepool {
        NSUInteger passed = 0;
        passed += excludedSourcesDoNotBlockRoot();
        passed += missingRootIdentityFails();
        passed += staleRootFailsAndRecovers();
        passed += missingCounterFails();
        passed += timeoutNeverOverlapsQuery();
        report([NSString stringWithFormat:@"Native network statistics: %lu/5 passed", (unsigned long)passed]);
        return passed == 5 ? 0 : 1;
    }
}
