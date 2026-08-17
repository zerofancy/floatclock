#import <AppKit/AppKit.h>
#import <jni.h>

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
