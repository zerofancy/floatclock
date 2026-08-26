# ==============================================================================
# FloatClock (Compose Desktop) release minification / obfuscation rules
#
# 说明：
#   基础规则由 Compose Desktop Gradle 插件在 default-compose-desktop-rules.pro 中
#   自动注入（kotlin/kotlinx-coroutines/kotlinx.serialization @Serializable、skia/skiko
#   等）。本文件只保留 应用自身必须的入口 + 缺 consumer rules 的第三方库保护。
# ==============================================================================

# --- R8 通用保留（注解与元数据）----------------------------------------------
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,AnnotationDefault
-keep class kotlin.Metadata { *; }

# ==============================================================================
# 1. 应用入口：只保留 main 方法
#    注：@file:JvmName("FloatClock") 使 main 所属类名为 FloatClock 而非 FloatClockKt
# ==============================================================================
-keepclassmembers class top.ntutn.floatclock.FloatClock {
    public static void main(java.lang.String[]);
}

# ==============================================================================
# 2. 应用中需要反射 / JNI 访问的类
# ==============================================================================
# MacOSWindowBridge.configureWindow 是 JNI native 方法，类名/方法名不能变
-keep class top.ntutn.floatclock.macos.MacOSWindowBridge { *; }

# JNI 通用：所有 native 方法名保留
-keepclasseswithmembernames class * {
    native <methods>;
}

# ==============================================================================
# 3. 缺 consumer rules 的第三方库（插件默认规则未覆盖，且实际运行期需要）
# ==============================================================================
# okio：自带 consumer rules 只有 -dontwarn animal-sniffer，其它核心反射类会被误改
-keep class okio.** { *; }
-dontwarn okio.**

# androidx.datastore：仅 datastore-preferences-core 带了 GeneratedMessageLite 的规则
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# kotlinx.serialization 核心库（插件规则只覆盖了反射 entry，不是整个库）
-keep class kotlinx.serialization.** { *; }

# JNA：结构体和 native 方法元数据需要保留
-keep class com.sun.jna.** { *; }
-keepclasseswithmembernames class com.sun.jna.** { *; }
-dontwarn com.sun.jna.**

# OSHI：通过 JNA 反射访问硬件/网络信息
-keep class oshi.** { *; }
-dontwarn oshi.**

# Compose 系列：插件默认规则只 keep 了关键 entry，但会让 ProGuard 把内部类的
# Companion 与 TraverseKey 字段都混淆成 "a"，造成冲突。
# 策略：保留字段名（allowshrinking+allowobfuscation=允许压缩但不混淆类/成员名）
-keepnames class androidx.compose.** { *; }

# ==============================================================================
# 4. 精确的 unresolved reference 忽略
#    这些类在 Desktop JVM 上是可选依赖 / 仅在特定平台存在
# ==============================================================================
-dontwarn org.slf4j.**                  # OSHI 可选日志
-dontwarn android.annotation.SuppressLint                # Android-only
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn org.graalvm.compiler.core.aarch64.AArch64NodeMatchRules_MatchStatementSet*
# Kotlin 2.x kotlin.concurrent.atomics — 可选 API，运行期不会走到
-dontwarn kotlin.concurrent.atomics.**
# Compose 对 kotlin.jvm.internal.EnhancedNullability 的内部引用（可选 API）
-dontwarn kotlin.jvm.internal.EnhancedNullability
