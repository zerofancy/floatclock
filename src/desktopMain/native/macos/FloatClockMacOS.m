#import <AppKit/AppKit.h>
#import <ServiceManagement/ServiceManagement.h>
#import <jni.h>
#import <unistd.h>

static NSString *FloatClockStringFromJString(JNIEnv *env, jstring value) {
    if (value == NULL) {
        return nil;
    }

    const jchar *characters = (*env)->GetStringChars(env, value, NULL);
    if (characters == NULL) {
        return nil;
    }

    const jsize length = (*env)->GetStringLength(env, value);
    NSString *result = [[NSString alloc] initWithCharacters:(const unichar *)characters
                                                     length:(NSUInteger)length];
    (*env)->ReleaseStringChars(env, value, characters);
    return result;
}

static BOOL FloatClockConfigureWindow(NSString *windowTitle) {
    for (NSWindow *window in NSApp.windows) {
        if (![window.title isEqualToString:windowTitle]) {
            continue;
        }

        NSWindowCollectionBehavior behavior = window.collectionBehavior;

        // Clear mutually exclusive Space/full-screen roles that AWT may have installed.
        behavior &= ~(NSWindowCollectionBehaviorMoveToActiveSpace |
                      NSWindowCollectionBehaviorFullScreenPrimary |
                      NSWindowCollectionBehaviorFullScreenAuxiliary |
                      NSWindowCollectionBehaviorFullScreenNone);
        behavior |= NSWindowCollectionBehaviorCanJoinAllSpaces;

        if (@available(macOS 13.0, *)) {
            behavior &= ~(NSWindowCollectionBehaviorPrimary |
                          NSWindowCollectionBehaviorAuxiliary |
                          NSWindowCollectionBehaviorCanJoinAllApplications);
            behavior |= NSWindowCollectionBehaviorCanJoinAllApplications;
        } else {
            behavior |= NSWindowCollectionBehaviorFullScreenAuxiliary;
        }

        window.collectionBehavior = behavior;
        window.level = NSFloatingWindowLevel;
        window.hidesOnDeactivate = NO;
        [window orderFrontRegardless];
        return YES;
    }

    return NO;
}

#pragma mark - Login Item (SMAppService, macOS 13+)

static BOOL FloatClockIsLoginItemEnabledSM(void) {
    if (@available(macOS 13.0, *)) {
        SMAppServiceStatus status = [SMAppService mainAppService].status;
        return status == SMAppServiceStatusEnabled;
    }
    return NO;
}

static BOOL FloatClockSetLoginItemEnabledSM(BOOL enabled, NSError **error) {
    if (@available(macOS 13.0, *)) {
        SMAppService *service = [SMAppService mainAppService];
        if (enabled) {
            return [service registerAndReturnError:error];
        } else {
            return [service unregisterAndReturnError:error];
        }
    }
    return NO;
}

JNIEXPORT jboolean JNICALL
Java_top_ntutn_floatclock_macos_MacOSWindowBridge_configureWindow(
    JNIEnv *env,
    jobject receiver,
    jstring windowTitle
) {
    (void)receiver;
    NSString *title = FloatClockStringFromJString(env, windowTitle);
    if (title == nil) {
        return JNI_FALSE;
    }

    __block BOOL configured = NO;
    void (^configure)(void) = ^{
        configured = FloatClockConfigureWindow(title);
    };

    if (NSThread.isMainThread) {
        configure();
    } else {
        dispatch_sync(dispatch_get_main_queue(), configure);
    }

    return configured ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_top_ntutn_floatclock_macos_MacOSWindowBridge_isLoginItemEnabledSMNative(
    JNIEnv *env,
    jobject receiver
) {
    (void)env;
    (void)receiver;
    __block BOOL enabled = NO;
    void (^check)(void) = ^{
        enabled = FloatClockIsLoginItemEnabledSM();
    };
    if (NSThread.isMainThread) {
        check();
    } else {
        dispatch_sync(dispatch_get_main_queue(), check);
    }
    return enabled ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_top_ntutn_floatclock_macos_MacOSWindowBridge_setLoginItemEnabledSMNative(
    JNIEnv *env,
    jobject receiver,
    jboolean enabled
) {
    (void)env;
    (void)receiver;
    __block BOOL success = NO;
    void (^set)(void) = ^{
        NSError *error = nil;
        success = FloatClockSetLoginItemEnabledSM(enabled == JNI_TRUE, &error);
        if (!success && error) {
            fprintf(stderr, "[FloatClock] SMAppService %@ failed: %s\n",
                    (enabled == JNI_TRUE) ? @"register" : @"unregister",
                    error.localizedDescription.UTF8String ?: "unknown error");
        }
    };
    if (NSThread.isMainThread) {
        set();
    } else {
        dispatch_sync(dispatch_get_main_queue(), set);
    }
    return success ? JNI_TRUE : JNI_FALSE;
}
