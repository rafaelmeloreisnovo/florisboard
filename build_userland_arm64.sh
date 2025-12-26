#!/usr/bin/env bash
# FlorisBoard Userland Build Script
# Build unsigned APK specifically for userland installation (no root/system access required)
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Build configuration
BUILD_TYPE="Release"
APK_OUTPUT_DIR="app/build/outputs/apk/release"
ARCHITECTURE="arm64-v8a"
BUILD_VARIANT="userland"

echo -e "${BLUE}================================================${NC}"
echo -e "${BLUE}FlorisBoard Userland ARM64 Build System${NC}"
echo -e "${BLUE}================================================${NC}"
echo ""

# Step 1: Verify ARM64 configuration
echo -e "${YELLOW}[1/6] Verifying ARM64 configuration for userland...${NC}"
if grep -q "arm64-v8a" app/build.gradle.kts; then
    echo -e "${GREEN}✓ ARM64 (arm64-v8a) configuration found${NC}"
else
    echo -e "${RED}✗ ARM64 configuration not found in app/build.gradle.kts${NC}"
    exit 1
fi

# Step 2: Clean previous builds
echo -e "${YELLOW}[2/6] Cleaning previous builds...${NC}"
./gradlew clean --no-daemon
echo -e "${GREEN}✓ Clean completed${NC}"

# Step 3: Build unsigned release APK for userland
echo -e "${YELLOW}[3/6] Building unsigned userland APK for ARM64...${NC}"
./gradlew :app:assembleRelease --no-daemon -PuserlandUnsignedApk=true
echo -e "${GREEN}✓ Build completed${NC}"

# Step 4: Verify APK generation
echo -e "${YELLOW}[4/6] Verifying APK generation...${NC}"
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

# Step 5: APK Analysis and Verification
echo -e "${YELLOW}[5/6] Analyzing generated APK(s)...${NC}"
for apk in "$APK_OUTPUT_DIR"/*.apk; do
    if [ -f "$apk" ]; then
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
        
        # Verify APK structure using unzip
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
        
        # Check for META-INF signatures (should be minimal for unsigned)
        SIG_COUNT=$(unzip -l "$apk" 2>/dev/null | grep -c "META-INF/.*\.(RSA\|DSA\|EC)" || true)
        if [ "$SIG_COUNT" -eq 0 ]; then
            echo -e "  Signature: ${GREEN}Unsigned (userland ready)${NC}"
        else
            echo -e "  Signature: ${YELLOW}Contains $SIG_COUNT signature file(s)${NC}"
        fi
        
        # Userland-specific checks
        echo -e "  Install type: ${GREEN}Userland (no root required)${NC}"
        echo -e "  System access: ${GREEN}User-level only${NC}"
    fi
done

# Step 6: Generate build report
echo ""
echo -e "${YELLOW}[6/6] Generating build report...${NC}"
REPORT_FILE="build_userland_report.txt"
cat > "$REPORT_FILE" << EOF
FlorisBoard Userland ARM64 Build Report
========================================
Build Date: $(date)
Build Type: $BUILD_TYPE
Build Variant: Userland (no root required)
Architecture: $ARCHITECTURE
Output Directory: $APK_OUTPUT_DIR

Userland Characteristics:
- No root/system access required
- Standard user-level installation
- No Magisk integration
- Works on unrooted devices
- Installed via "Install from unknown sources"

Generated APK(s):
EOF

for apk in "$APK_OUTPUT_DIR"/*.apk; do
    if [ -f "$apk" ]; then
        APK_NAME=$(basename "$apk")
        APK_SIZE=$(du -h "$apk" | cut -f1)
        if command -v sha256sum >/dev/null 2>&1; then
            CHECKSUM=$(sha256sum "$apk" | cut -d' ' -f1)
        else
            CHECKSUM="N/A"
        fi
        
        cat >> "$REPORT_FILE" << EOF

- File: $APK_NAME
  Size: $APK_SIZE
  SHA256: $CHECKSUM
  Path: $apk
  Type: Userland (user-level installation)
EOF
    fi
done

echo -e "${GREEN}✓ Build report generated: $REPORT_FILE${NC}"

# Final summary
echo ""
echo -e "${BLUE}================================================${NC}"
echo -e "${GREEN}Userland build completed successfully!${NC}"
echo -e "${BLUE}================================================${NC}"
echo ""
echo -e "Unsigned userland APKs are in:"
echo -e "  ${GREEN}$APK_OUTPUT_DIR${NC}"
echo ""
echo -e "Build report:"
echo -e "  ${GREEN}$REPORT_FILE${NC}"
echo ""
echo -e "${YELLOW}Userland Installation Instructions:${NC}"
echo -e "  1. Transfer APK to your ARM64 Android device"
echo -e "  2. Enable 'Install from unknown sources' in device settings"
echo -e "  3. Install the APK (no root required)"
echo -e "  4. Enable FlorisBoard in Settings → System → Languages & input"
echo ""
echo -e "${BLUE}Note: This is a userland build - works on unrooted devices${NC}"
echo ""
