# ProGuard rules for lib/native module
# This module contains JNI bindings to native (Rust) code

# Keep all native methods
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Keep all classes in the libnative package
-keep class org.florisboard.libnative.** { *; }
