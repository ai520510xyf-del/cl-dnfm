# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ================================
# 1. 基础保留规则
# ================================

# 保留注解
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature
-keepattributes Exceptions

# 保留native方法
-keepclasseswithmembernames class * {
    native <methods>;
}

# 保留枚举
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 保留Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# 保留Parcelable
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# ================================
# 2. Android相关
# ================================

# 保留View构造方法
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# 保留Activity的所有子类
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# ================================
# 3. Kotlin相关
# ================================

# Kotlin反射
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ================================
# 4. AndroidX & Material
# ================================

# ViewBinding
-keep class * implements androidx.viewbinding.ViewBinding {
    public static * inflate(android.view.LayoutInflater);
    public static * bind(android.view.View);
}

# Fragment
-keep public class * extends androidx.fragment.app.Fragment

# Lifecycle
-keep class * implements androidx.lifecycle.LifecycleObserver {
    <init>(...);
}
-keep class * extends androidx.lifecycle.ViewModel {
    <init>();
}
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# ================================
# 5. 项目特定规则
# ================================

# 保护游戏机器人核心类
-keep class com.gamebot.ai.** { *; }

# 无障碍服务必须保留
-keep class com.gamebot.ai.service.GameBotAccessibilityService { *; }

# 数据模型类保留（用于JSON序列化/反序列化）
-keep class com.gamebot.ai.data.** { *; }
-keepclassmembers class com.gamebot.ai.data.** {
    <fields>;
    <methods>;
}

# 云端训练相关类
-keep class com.gamebot.ai.cloud.** { *; }
-keepclassmembers class com.gamebot.ai.cloud.** {
    <fields>;
    <methods>;
}

# UI Fragment和Activity
-keep class com.gamebot.ai.ui.** { *; }
-keep class com.gamebot.ai.MainActivity* { *; }

# 检测器类
-keep class com.gamebot.ai.detector.** { *; }

# ================================
# 6. 第三方库规则
# ================================

# TensorFlow Lite
-keep class org.tensorflow.lite.** { *; }
-keepclassmembers class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**

# Supabase
-keep class io.github.jan.supabase.** { *; }
-keepclassmembers class io.github.jan.supabase.** { *; }
-dontwarn io.github.jan.supabase.**

# Ktor (Supabase依赖)
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** {
    volatile <fields>;
}
-dontwarn io.ktor.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Gson - 保留泛型
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ================================
# 7. 安全相关 - 移除日志
# ================================

# 生产环境移除所有Log调用（除了错误日志）
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# ================================
# 8. 优化选项
# ================================

# 混淆类名和方法名
-repackageclasses ''
-allowaccessmodification

# 优化次数
-optimizationpasses 5

# 不跳过非公共库类
-dontskipnonpubliclibraryclasses

# 打印详细信息
-verbose

# ================================
# 9. 其他警告抑制
# ================================

# 忽略javax警告
-dontwarn javax.**
-dontwarn java.lang.management.**

# 忽略缺失的类
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
