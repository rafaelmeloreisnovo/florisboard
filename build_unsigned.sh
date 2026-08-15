#!/usr/bin/env bash
# Build FlorisBoard APKs without custom signing (release + beta unsigned)
# Enhanced for Android ARM (arm64-v8a + armeabi-v7a) with comprehensive validation
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
ARCHITECTURES=("arm64-v8a" "armeabi-v7a")

echo -e "${BLUE}================================================${NC}"
echo -e "${BLUE}FlorisBoard Android ARM Unsigned APK Build System${NC}"
echo -e "${BLUE}================================================${NC}"
echo ""

# Step 1: Verify Android ARM configuration
echo -e "${YELLOW}[1/6] Verifying Android ARM configuration...${NC}"
for architecture in "${ARCHITECTURES[@]}"; do
    if grep -Fq "$architecture" app/build.gradle.kts; then
        echo -e "${GREEN}✓ $architecture configuration found${NC}"
    else
        echo -e "${RED}✗ $architecture configuration not found in app/build.gradle.kts${NC}"
        exit 1
    fi
done

# Step 2: Clean previous builds
echo -e "${YELLOW}[2/6] Cleaning previous builds...${NC}"
./gradlew clean --no-daemon
echo -e "${GREEN}✓ Clean completed${NC}"

# Step 3: Build unsigned release APK
echo -e "${YELLOW}[3/6] Building unsigned release APK for Android ARM...${NC}"
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
        
        # Check for expected Android ARM native libraries
        for architecture in "${ARCHITECTURES[@]}"; do
            if unzip -l "$apk" 2>/dev/null | grep -q "lib/$architecture/"; then
                echo -e "  $architecture libs: ${GREEN}Present${NC}"
            else
                echo -e "  $architecture libs: ${RED}Missing${NC}"
                exit 1
            fi
        done
        
        # Check for META-INF signatures (should be minimal for unsigned)
        SIG_COUNT=$(unzip -l "$apk" 2>/dev/null | grep -c "META-INF/.*\.(RSA\|DSA\|EC)" || true)
        if [ "$SIG_COUNT" -eq 0 ]; then
            echo -e "  Signature: ${GREEN}Unsigned (as expected)${NC}"
        else
            echo -e "  Signature: ${YELLOW}Contains $SIG_COUNT signature file(s)${NC}"
        fi
    fi
done

# Step 6: Generate build report
echo ""
echo -e "${YELLOW}[6/6] Generating build report...${NC}"
REPORT_FILE="build_report.txt"
cat > "$REPORT_FILE" << EOF
FlorisBoard Android ARM Build Report
==============================
Build Date: $(date)
Build Type: $BUILD_TYPE
Architectures: ${ARCHITECTURES[*]}
Output Directory: $APK_OUTPUT_DIR

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
EOF
    fi
done

echo -e "${GREEN}✓ Build report generated: $REPORT_FILE${NC}"

# Final summary
echo ""
echo -e "${BLUE}================================================${NC}"
echo -e "${GREEN}Build completed successfully!${NC}"
echo -e "${BLUE}================================================${NC}"
echo ""
echo -e "Unsigned APKs are in:"
echo -e "  ${GREEN}$APK_OUTPUT_DIR${NC}"
echo ""
echo -e "Build report:"
echo -e "  ${GREEN}$REPORT_FILE${NC}"
echo ""
echo -e "${YELLOW}Installation instructions:${NC}"
echo -e "  1. Transfer APK to an ARMv7 or ARM64 Android device"
echo -e "  2. Enable 'Install from unknown sources' in device settings"
echo -e "  3. Install the APK"
echo ""
