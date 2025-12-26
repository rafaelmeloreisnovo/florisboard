# FlorisBoard Build Variants Guide

This guide explains the different build variants available for FlorisBoard and when to use each one.

## Overview

FlorisBoard can be built in three different variants, each optimized for different installation scenarios:

1. **Standard Build** - Basic unsigned APK for ARM64
2. **Userland Build** - Explicitly marked for userland installation (no root)
3. **Magisk Build** - System-level installation via Magisk module

---

## Build Variants Comparison

| Feature | Standard | Userland | Magisk |
|---------|----------|----------|---------|
| **Root Required** | No | No | Yes (via Magisk) |
| **Installation Method** | Manual APK | Manual APK | Magisk Manager |
| **System Integration** | User app | User app | System app |
| **Permissions** | Standard | Standard | Privileged |
| **OTA Survival** | No | No | Yes (with Magisk) |
| **Best For** | General use | Unrooted devices | Rooted devices |

---

## 1. Standard Build

### Description
Basic unsigned ARM64 APK with comprehensive validation and monitoring.

### Build Script
```bash
./build_unsigned.sh
```

### Characteristics
- ✅ Works on all ARM64 devices
- ✅ No special requirements
- ✅ Standard user-level app
- ✅ Manual installation via "Install from unknown sources"

### Output
- `app/build/outputs/apk/release/app-release-unsigned.apk`
- `build_report.txt`

### Installation
1. Transfer APK to device
2. Enable "Install from unknown sources"
3. Install the APK
4. Enable FlorisBoard in Settings

### Use Cases
- General testing
- Distribution to end users
- Development builds
- Quick installation

---

## 2. Userland Build

### Description
Unsigned ARM64 APK specifically optimized for userland installation. Identical to standard build but explicitly marked for clarity.

### Build Script
```bash
./build_userland_arm64.sh
```

### Characteristics
- ✅ No root/system access required
- ✅ Explicitly marked as userland build
- ✅ Works on unrooted devices
- ✅ Standard Android app permissions
- ✅ Uses `-PuserlandUnsignedApk=true` flag

### Output
- `app/build/outputs/apk/release/app-release-unsigned.apk`
- `build_userland_report.txt` (with userland-specific info)

### Installation
Same as standard build - no special requirements.

### Use Cases
- Production builds for unrooted devices
- F-Droid or alternative app store distribution
- Users who don't want/need root
- Maximum compatibility

---

## 3. Magisk Build

### Description
Unsigned ARM64 APK packaged as a Magisk module for system-level installation.

### Build Script
```bash
./build_magisk_arm64.sh
```

### Characteristics
- ⚠️ Requires Magisk v20.4 or higher
- ⚠️ Requires root access (via Magisk)
- ✅ Installed as privileged system app
- ✅ Survives OTA updates (when using Magisk)
- ✅ Better system integration
- ✅ System-level permissions available

### Output
- `app/build/outputs/apk/release/app-release-unsigned.apk`
- `magisk_module/` (Magisk module structure)
- `FlorisBoard-Magisk-ARM64-v0.4.0.zip` (flashable module)
- `build_magisk_report.txt`

### Magisk Module Structure
```
magisk_module/
├── META-INF/
│   └── com/google/android/
│       ├── update-binary (install script)
│       └── updater-script
├── system/
│   └── priv-app/
│       └── FlorisBoard/
│           └── FlorisBoard.apk
├── module.prop (module metadata)
└── README.md (installation guide)
```

### Installation
1. Copy `FlorisBoard-Magisk-ARM64-v0.4.0.zip` to device
2. Open Magisk Manager app
3. Go to Modules → Install from storage
4. Select the ZIP file
5. Reboot device
6. Enable FlorisBoard in Settings

### Advantages
- System-level installation without modifying `/system` partition
- Survives OTA updates (Magisk reinstalls modules)
- Can be easily enabled/disabled in Magisk Manager
- Automatic permissions as system app
- Better integration with Android system

### Use Cases
- Users who want system-level integration
- Rooted devices with Magisk
- Advanced users needing privileged permissions
- Testing system-level features
- Custom ROMs with Magisk

---

## Quick Start

### For Most Users (No Root)
```bash
# Use either standard or userland build
./build_unsigned.sh
# OR
./build_userland_arm64.sh
```

### For Magisk Users (Rooted)
```bash
./build_magisk_arm64.sh
```

### With Comprehensive Verification
```bash
# Standard with full monitoring
./build_and_verify_arm64.sh
```

---

## Build Requirements

### All Variants
- Java 17
- Android SDK
- Gradle (wrapper included)
- ARM64 target device

### Magisk Variant Additional Requirements
- `zip` command (for creating module package)
- Magisk v20.4+ installed on target device

---

## Verification

All build scripts include comprehensive verification:

✅ ARM64 configuration check  
✅ Build prerequisites validation  
✅ APK structure verification  
✅ Native library verification (ARM64)  
✅ Checksum generation (SHA256)  
✅ Detailed reporting  

---

## Choosing the Right Build

### Choose **Standard** if:
- You want the simplest build process
- You don't need special features
- You're testing or developing

### Choose **Userland** if:
- You want to emphasize no root needed
- You're distributing to unrooted devices
- You want explicit userland designation
- You're publishing to alternative stores

### Choose **Magisk** if:
- You have Magisk installed
- You want system-level integration
- You need privileged permissions
- You want to survive OTA updates
- You prefer module-based installation

---

## Technical Details

### Build Flags

**Standard Build:**
```bash
./gradlew :app:assembleRelease --no-daemon -PuserlandUnsignedApk=true
```

**Userland Build:**
```bash
./gradlew :app:assembleRelease --no-daemon -PuserlandUnsignedApk=true
```

**Magisk Build:**
```bash
./gradlew :app:assembleRelease --no-daemon -PuserlandUnsignedApk=true
# Then package into Magisk module structure
```

### Architecture
All builds target ARM64 (arm64-v8a):
```kotlin
ndk {
    abiFilters += listOf("arm64-v8a")
}
```

### Signing
All builds are **unsigned** by default:
- No Google Play Store signature
- Suitable for sideloading
- Users must enable "Install from unknown sources"
- Checksums provided for verification

---

## CI/CD Integration

### GitHub Actions Workflow

The automated workflow supports all build variants:

```yaml
# .github/workflows/build-arm64-verified.yml
# Builds and verifies ARM64 APK automatically
```

**Triggers:**
- Push to main/master
- Pull requests
- Manual dispatch

**Artifacts:**
- `florisboard-arm64-unsigned` (APK files)
- `build-logs` (verification logs)
- 30-day retention

---

## Troubleshooting

### Build Fails
```bash
# Clean and retry
./gradlew clean
./build_unsigned.sh
```

### APK Installation Fails
- Verify ARM64 architecture: `adb shell getprop ro.product.cpu.abi`
- Check Android version: API 26+ required
- Enable "Install from unknown sources"

### Magisk Module Fails
- Verify Magisk version: v20.4+ required
- Check Magisk logs in Magisk Manager
- Try disabling other conflicting modules

---

## Security Considerations

### Unsigned APK
- ✅ Checksums provided for verification
- ⚠️ Users must explicitly enable "Unknown sources"
- ⚠️ No automatic updates
- ✅ Source code is open for inspection

### Magisk Module
- ⚠️ Requires root access
- ⚠️ System-level permissions
- ✅ Managed by Magisk (can be disabled)
- ✅ Doesn't modify system partition directly

---

## Support

For issues or questions:
1. Check this documentation
2. Review troubleshooting section
3. Check build logs (*.txt files)
4. Verify checksums
5. Open GitHub issue with details

---

**Last Updated:** December 26, 2025  
**Build System Version:** 2.0  
**Supported Architectures:** ARM64 (arm64-v8a)
