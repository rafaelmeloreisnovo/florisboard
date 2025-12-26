#!/usr/bin/env bash
# FlorisBoard Magisk Module Build Script
# Build unsigned APK for Magisk module integration (system-level installation)
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Build configuration
BUILD_TYPE="Release"
APK_OUTPUT_DIR="app/build/outputs/apk/release"
ARCHITECTURE="arm64-v8a"
BUILD_VARIANT="magisk"
MAGISK_MODULE_DIR="magisk_module"

echo -e "${BLUE}================================================${NC}"
echo -e "${BLUE}FlorisBoard Magisk Module Build System${NC}"
echo -e "${BLUE}================================================${NC}"
echo ""

# Step 1: Verify ARM64 configuration
echo -e "${YELLOW}[1/7] Verifying ARM64 configuration for Magisk...${NC}"
if grep -q "arm64-v8a" app/build.gradle.kts; then
    echo -e "${GREEN}✓ ARM64 (arm64-v8a) configuration found${NC}"
else
    echo -e "${RED}✗ ARM64 configuration not found in app/build.gradle.kts${NC}"
    exit 1
fi

# Step 2: Clean previous builds
echo -e "${YELLOW}[2/7] Cleaning previous builds...${NC}"
./gradlew clean --no-daemon
rm -rf "$MAGISK_MODULE_DIR" 2>/dev/null || true
echo -e "${GREEN}✓ Clean completed${NC}"

# Step 3: Build unsigned release APK
echo -e "${YELLOW}[3/7] Building unsigned APK for Magisk module...${NC}"
./gradlew :app:assembleRelease --no-daemon -PuserlandUnsignedApk=true
echo -e "${GREEN}✓ Build completed${NC}"

# Step 4: Verify APK generation
echo -e "${YELLOW}[4/7] Verifying APK generation...${NC}"
if [ -d "$APK_OUTPUT_DIR" ]; then
    APK_COUNT=$(find "$APK_OUTPUT_DIR" -name "*.apk" -type f | wc -l)
    if [ "$APK_COUNT" -gt 0 ]; then
        echo -e "${GREEN}✓ Found $APK_COUNT APK file(s)${NC}"
    else
        echo -e "${RED}✗ No APK files found in $APK_OUTPUT_DIR${NC}"
        exit 1
    fi
else
    echo -e "${RED}✗ APK output directory not found: $APK_OUTPUT_DIR${NC}"
    exit 1
fi

# Step 5: APK Analysis
echo -e "${YELLOW}[5/7] Analyzing generated APK(s)...${NC}"
APK_FILE=""
for apk in "$APK_OUTPUT_DIR"/*.apk; do
    if [ -f "$apk" ]; then
        APK_FILE="$apk"
        echo ""
        echo -e "${BLUE}APK: $(basename "$apk")${NC}"
        
        # Get APK size
        APK_SIZE=$(du -h "$apk" | cut -f1)
        echo -e "  Size: ${GREEN}$APK_SIZE${NC}"
        
        # Calculate checksum
        if command -v sha256sum >/dev/null 2>&1; then
            CHECKSUM=$(sha256sum "$apk" | cut -d' ' -f1)
            echo -e "  SHA256: ${GREEN}$CHECKSUM${NC}"
        fi
        
        # Verify APK structure
        if command -v unzip >/dev/null 2>&1; then
            if unzip -t "$apk" >/dev/null 2>&1; then
                echo -e "  Structure: ${GREEN}Valid${NC}"
            else
                echo -e "  Structure: ${RED}Invalid${NC}"
                exit 1
            fi
        fi
        
        # Check for ARM64 native libraries
        if unzip -l "$apk" 2>/dev/null | grep -q "lib/arm64-v8a/"; then
            echo -e "  ARM64 libs: ${GREEN}Present${NC}"
        else
            echo -e "  ARM64 libs: ${YELLOW}Not found (may be Java-only)${NC}"
        fi
        
        # Magisk-specific info
        echo -e "  Install type: ${CYAN}Magisk module (system-level)${NC}"
        echo -e "  System access: ${CYAN}Root via Magisk${NC}"
    fi
done

# Step 6: Create Magisk module structure
echo ""
echo -e "${YELLOW}[6/7] Creating Magisk module structure...${NC}"

# Create module directories
mkdir -p "$MAGISK_MODULE_DIR/system/priv-app/FlorisBoard"
mkdir -p "$MAGISK_MODULE_DIR/META-INF/com/google/android"

# Copy APK to module
if [ -n "$APK_FILE" ]; then
    cp "$APK_FILE" "$MAGISK_MODULE_DIR/system/priv-app/FlorisBoard/FlorisBoard.apk"
    echo -e "${GREEN}✓ APK copied to module structure${NC}"
fi

# Create module.prop
cat > "$MAGISK_MODULE_DIR/module.prop" << 'EOF'
id=florisboard_arm64
name=FlorisBoard ARM64
version=0.4.0
versionCode=85
author=FlorisBoard Contributors
description=FlorisBoard keyboard for ARM64 devices - installed as system app via Magisk
EOF
echo -e "${GREEN}✓ module.prop created${NC}"

# Create install script
cat > "$MAGISK_MODULE_DIR/META-INF/com/google/android/update-binary" << 'EOF'
#!/sbin/sh
##########################################################################################
#
# Magisk Module Installer Script
#
##########################################################################################

OUTFD=$2
ZIPFILE=$3

ui_print() {
  echo "ui_print $1" > /proc/self/fd/$OUTFD
  echo "ui_print" > /proc/self/fd/$OUTFD
}

require_new_magisk() {
  ui_print "***********************************"
  ui_print " Please install Magisk v20.4+! "
  ui_print "***********************************"
  exit 1
}

##########################################################################################
# Environment
##########################################################################################

TMPDIR=/dev/tmp
PERSISTDIR=/sbin/.magisk/mirror/persist

rm -rf $TMPDIR 2>/dev/null
mkdir -p $TMPDIR

# echo before loading util_functions
ui_print "- Mounting /system, /vendor"
mount -o ro /system 2>/dev/null
mount -o ro /vendor 2>/dev/null
mount /data 2>/dev/null

[ -f /system/build.prop ] || require_new_magisk

MAGISKBIN=/data/adb/magisk
[ -d $MAGISKBIN ] || require_new_magisk

# Load utility functions
. $MAGISKBIN/util_functions.sh
[ $MAGISK_VER_CODE -lt 20400 ] && require_new_magisk

ui_print "- Installing FlorisBoard ARM64 Magisk Module"

##########################################################################################
# Install
##########################################################################################

ui_print "- Extracting module files"
unzip -o "$ZIPFILE" -d $TMPDIR >&2

ui_print "- Installing to $MODPATH"
rm -rf $MODPATH 2>/dev/null
mkdir -p $MODPATH

cp -af $TMPDIR/system $MODPATH/
cp -f $TMPDIR/module.prop $MODPATH/module.prop

# Set permissions
ui_print "- Setting permissions"
set_perm_recursive $MODPATH 0 0 0755 0644
set_perm_recursive $MODPATH/system/priv-app 0 0 0755 0644

ui_print "- Installation complete"
ui_print "- Reboot your device to activate FlorisBoard"

##########################################################################################
# Cleanup
##########################################################################################

ui_print "- Cleaning up"
rm -rf $TMPDIR

exit 0
EOF
chmod +x "$MAGISK_MODULE_DIR/META-INF/com/google/android/update-binary"
echo -e "${GREEN}✓ Install script created${NC}"

# Create updater-script (required by some recovery tools)
cat > "$MAGISK_MODULE_DIR/META-INF/com/google/android/updater-script" << 'EOF'
#MAGISK
EOF
echo -e "${GREEN}✓ updater-script created${NC}"

# Create README for module
cat > "$MAGISK_MODULE_DIR/README.md" << 'EOF'
# FlorisBoard ARM64 Magisk Module

This Magisk module installs FlorisBoard as a system application with privileged access.

## Features
- System-level installation via Magisk
- ARM64 optimized
- No signature verification required
- Survives OTA updates (when using Magisk)

## Installation
1. Flash this module via Magisk Manager
2. Reboot your device
3. Enable FlorisBoard in Settings → System → Languages & input

## Uninstallation
1. Disable/remove the module in Magisk Manager
2. Reboot your device

## Requirements
- Magisk v20.4 or higher
- ARM64 device
- Android 8.0+ (API 26+)

## Note
This module installs FlorisBoard as a privileged system app. This provides:
- System-level permissions if needed
- Better integration with system services
- Automatic installation on boot
EOF
echo -e "${GREEN}✓ README created${NC}"

# Create the Magisk module ZIP
MAGISK_ZIP="FlorisBoard-Magisk-ARM64-v0.4.0.zip"
echo -e "${CYAN}Creating Magisk module ZIP...${NC}"
cd "$MAGISK_MODULE_DIR"
if command -v zip >/dev/null 2>&1; then
    zip -r "../$MAGISK_ZIP" . >/dev/null 2>&1
    cd ..
    echo -e "${GREEN}✓ Magisk module ZIP created: $MAGISK_ZIP${NC}"
else
    cd ..
    echo -e "${YELLOW}⚠ zip command not found, module directory created but not packaged${NC}"
    echo -e "${YELLOW}  You can manually zip the contents of $MAGISK_MODULE_DIR${NC}"
fi

# Step 7: Generate build report
echo ""
echo -e "${YELLOW}[7/7] Generating build report...${NC}"
REPORT_FILE="build_magisk_report.txt"
cat > "$REPORT_FILE" << EOF
FlorisBoard Magisk Module Build Report
=======================================
Build Date: $(date)
Build Type: $BUILD_TYPE
Build Variant: Magisk Module (system-level)
Architecture: $ARCHITECTURE
Output Directory: $APK_OUTPUT_DIR
Module Directory: $MAGISK_MODULE_DIR

Magisk Module Characteristics:
- System-level installation via Magisk
- Requires root access (via Magisk)
- Installed as privileged system app
- Survives OTA updates
- ARM64 optimized

Generated APK:
EOF

if [ -f "$APK_FILE" ]; then
    APK_NAME=$(basename "$APK_FILE")
    APK_SIZE=$(du -h "$APK_FILE" | cut -f1)
    if command -v sha256sum >/dev/null 2>&1; then
        CHECKSUM=$(sha256sum "$APK_FILE" | cut -d' ' -f1)
    else
        CHECKSUM="N/A"
    fi
    
    cat >> "$REPORT_FILE" << EOF

- File: $APK_NAME
  Size: $APK_SIZE
  SHA256: $CHECKSUM
  Path: $APK_FILE
  Type: Magisk module system app

Magisk Module:
EOF
    
    if [ -f "$MAGISK_ZIP" ]; then
        MODULE_SIZE=$(du -h "$MAGISK_ZIP" | cut -f1)
        if command -v sha256sum >/dev/null 2>&1; then
            MODULE_CHECKSUM=$(sha256sum "$MAGISK_ZIP" | cut -d' ' -f1)
        else
            MODULE_CHECKSUM="N/A"
        fi
        cat >> "$REPORT_FILE" << EOF
- File: $MAGISK_ZIP
  Size: $MODULE_SIZE
  SHA256: $MODULE_CHECKSUM
  Installation: Flash via Magisk Manager
EOF
    else
        cat >> "$REPORT_FILE" << EOF
- Module structure created in: $MAGISK_MODULE_DIR
  Status: Ready for manual packaging
EOF
    fi
fi

echo -e "${GREEN}✓ Build report generated: $REPORT_FILE${NC}"

# Final summary
echo ""
echo -e "${BLUE}================================================${NC}"
echo -e "${GREEN}Magisk module build completed successfully!${NC}"
echo -e "${BLUE}================================================${NC}"
echo ""
echo -e "Magisk module contents:"
echo -e "  ${GREEN}$MAGISK_MODULE_DIR/${NC}"
if [ -f "$MAGISK_ZIP" ]; then
    echo -e ""
    echo -e "Magisk module ZIP:"
    echo -e "  ${GREEN}$MAGISK_ZIP${NC}"
fi
echo ""
echo -e "Build report:"
echo -e "  ${GREEN}$REPORT_FILE${NC}"
echo ""
echo -e "${YELLOW}Magisk Installation Instructions:${NC}"
echo -e "  1. Copy ${MAGISK_ZIP} to your device"
echo -e "  2. Open Magisk Manager"
echo -e "  3. Go to Modules → Install from storage"
echo -e "  4. Select ${MAGISK_ZIP}"
echo -e "  5. Reboot your device"
echo -e "  6. Enable FlorisBoard in Settings → System → Languages & input"
echo ""
echo -e "${CYAN}Note: Requires Magisk v20.4+ and ARM64 device${NC}"
echo ""
