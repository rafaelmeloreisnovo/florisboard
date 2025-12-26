# Keep all native methods to prevent JNI UnsatisfiedLinkError
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Keep the entire libnative package to ensure JNI bindings work correctly
-keep class org.florisboard.libnative.** { *; }

# Keep method names and signatures for JNI
-keepclassmembers class org.florisboard.libnative.** {
    native <methods>;
}

# Preserve line numbers for debugging native crashes
-keepattributes SourceFile,LineNumberTable
