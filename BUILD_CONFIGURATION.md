# FlorisBoard Build Configuration Guide

## Overview

This document describes the comprehensive build system refactoring implemented to ensure maximum compatibility, stability, and performance across Android 9 (API 28) to Android 16+ (API 36+), with special focus on Android 15 arm64.

## Android Compatibility

### API Level Support
- **Minimum SDK**: 28 (Android 9.0 Pie)
- **Target SDK**: 35 (Android 15)
- **Compile SDK**: 36 (Android 16 Preview)

The increased minimum SDK from 26 to 28 provides:
- Better stability and crash resistance
- Improved security features
- Enhanced background execution limits
- Better notification management

### Architecture Support
- **Primary**: arm64-v8a (64-bit ARM)
- **Optimized for**: Android 15 on arm64 devices
 - **Packaging**: Only arm64-v8a libraries are shipped; 32-bit and x86 variants are removed to reduce attack surface and bundle size.

## Java and Kotlin Configuration

### Version Standardization
All modules now use consistent versions:
- **Java**: 17 (VERSION_17)
- **Kotlin JVM Target**: 17
- **Gradle**: 8.11.1
- **Kotlin**: 2.2.20
- **Android Gradle Plugin**: 8.9.1

### Compiler Flags
Kotlin compiler flags for optimal performance:
```kotlin
-opt-in=kotlin.RequiresOptIn
-opt-in=kotlin.contracts.ExperimentalContracts
-Xjvm-default=all
-Xwhen-guards (for applicable modules)
```

## Build Performance Optimizations

### Gradle Properties
```properties
org.gradle.jvmargs=-Xmx6144m -XX:+UseG1GC -XX:MaxMetaspaceSize=1536m
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.workers.max=6
```

### Memory Configuration
- **Heap Size**: 6GB (up from 4GB)
- **Garbage Collector**: G1GC (improved from ParallelGC)
- **Metaspace**: 1536MB (up from 1024MB)
- **Parallel Workers**: 6 threads

## Crash Resistance Features

### 1. ProGuard/R8 Optimizations
Enhanced rules for:
- Native method preservation (JNI stability)
- Android component lifecycle protection
- Kotlin Coroutines crash prevention
- Room database integrity
- Compose runtime stability
- Enum value protection

### 2. AndroidManifest Enhancements
```xml
android:hardwareAccelerated="true"
android:largeHeap="true"
android:usesCleartextTraffic="false"
```

### 3. MultiDex Support
Enabled for better class loading and reduced ANR risks.

### 4. Resource Management
- Duplicate resource handling
- META-INF conflict resolution
- Native library optimization
- Debug symbol preservation for crash analysis

## NDK Configuration

### Native Libraries
- **Target ABI**: arm64-v8a only
- **Debug Symbols**: 
  - Release: SYMBOL_TABLE (for crash analysis)
  - Debug: NONE (faster builds)
- **CMake Version**: 4.1.2
- **NDK Version**: 26.3.11579264
- **Rust Toolchain**: 1.83.0

### Native Library Features
- JNI libs packaging optimized
- Legacy packaging disabled for smaller APK
- Debug symbols kept for production crash analysis

## R8/ProGuard Configuration

### Optimization Passes
- **Optimization Passes**: 5
- **Obfuscation**: Disabled (clarity for debugging)
- **Shrinking**: Enabled
- **Resource Shrinking**: Enabled

### Crash Prevention Rules
- Keep all Android lifecycle components
- Preserve serialization classes
- Protect Kotlin metadata
- Maintain Compose runtime
- Keep Room database entities
- Preserve IME service classes

## Build Features

### Enabled
- Compose UI
- BuildConfig
- Resource prefixing (`floris_`)

### Disabled (for faster builds)
- AIDL
- RenderScript
- Shaders
- Jetifier (no longer needed)

## Testing Configuration

### Unit Tests
- Return default values enabled
- Android resources included
- Animations disabled for consistency

### Instrumentation Tests
- JUnit Runner: AndroidJUnitRunner
- Test options optimized

## Dependencies

### Version Catalog
Using `libs.versions.toml` for centralized version management:
- AndroidX Compose BOM: 2025.11.00
- Kotlin: 2.2.20
- Room: 2.7.2
- KSP: 2.2.20-2.0.3

### Internal Libraries
All internal libraries unified:
- lib:android
- lib:color
- lib:compose
- lib:kotlin
- lib:native
- lib:snygg
- lib:zipraf-omega

## Build Variants

### Release
- Minification: Enabled
- Resource Shrinking: Enabled
- Debuggable: False
- ProGuard: Full optimization

### Debug
- Minification: Disabled
- Debuggable: True
- Application ID Suffix: `.debug`
- Version Name Suffix: `-debug`

## Compatibility Notes

### Android 9-16 Support
The build system is designed to work seamlessly across:
- Android 9 (Pie) - API 28
- Android 10 (Q) - API 29
- Android 11 (R) - API 30
- Android 12 (S) - API 31
- Android 13 (T) - API 33
- Android 14 (U) - API 34
- Android 15 (V) - API 35
- Android 16+ (Preview) - API 36+

### Known Issues
- R8 metadata warnings with Kotlin 2.2.20 (non-breaking)
- Some deprecated API usage (planned for future updates)

## Security Features

### Manifest Security
- Cleartext traffic disabled
- Proper permission declarations
- Query intent filters for Android 11+
- Backup rules configured

### ProGuard Security
- Logging removed in release builds
- Sensitive data protection
- Native method preservation

## Build Commands

### Clean Build
```bash
./gradlew clean
```

### Build Release APK
```bash
./gradlew assembleRelease
```

### Build Debug APK
```bash
./gradlew assembleDebug
```

### Run Tests
```bash
./gradlew test
```

### Generate Build Reports
```bash
./gradlew build --scan
```

## Performance Metrics

### Build Times (Approximate)
- Clean build: ~6-7 minutes
- Incremental build: ~30-60 seconds
- Configuration: ~1-2 minutes

### APK Size (Release)
- Base APK: ~35MB
- arm64-v8a native libs: ~6.5MB

## Troubleshooting

### Build Failures
1. Clean build directory: `./gradlew clean`
2. Invalidate caches
3. Check Java 17 is installed
4. Verify Gradle daemon is running

### Memory Issues
- Increase `org.gradle.jvmargs` heap size
- Reduce parallel workers
- Close other applications

### NDK Issues
- Ensure NDK 26.3.11579264 is installed
- Check CMake version 4.1.2
- Verify Rust toolchain 1.83.0

## Future Improvements

- [ ] Further R8 optimization tuning
- [ ] Baseline profiles for better runtime performance
- [ ] Additional crash prevention mechanisms
- [ ] Enhanced build caching strategies
- [ ] Modularization improvements

## References

- [Android Gradle Plugin Release Notes](https://developer.android.com/studio/releases/gradle-plugin)
- [Kotlin Release Notes](https://kotlinlang.org/docs/releases.html)
- [R8 Optimization Guide](https://developer.android.com/studio/build/shrink-code)
- [Android NDK Guide](https://developer.android.com/ndk/guides)

---

Last Updated: December 26, 2025
Version: 0.5.0-rc02
