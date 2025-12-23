# Add project specific ProGuard rules here.

# Keep TensorFlow Lite classes
-keep class org.tensorflow.lite.** { *; }
-keep interface org.tensorflow.lite.** { *; }

# Keep application classes
-keep class com.gamebot.ai.** { *; }

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }

# Keep Coroutines
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
