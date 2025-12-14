#!/bin/bash
# FlorisBoard Build and Sign Script
# 
# This script builds and signs a release APK in one step
# Usage: ./build_and_sign.sh
#
# Prerequisites:
# - signing.properties must be configured (see signing.properties.template)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

cd "$SCRIPT_DIR"

echo "========================================="
echo "FlorisBoard Build and Sign"
echo "========================================="
echo ""

# Check if signing configuration exists
if [ ! -f "signing.properties" ]; then
    echo "ERROR: signing.properties not found"
    echo ""
    echo "Please create signing.properties from the template:"
    echo "  1. cp signing.properties.template signing.properties"
    echo "  2. Edit signing.properties with your keystore information"
    echo ""
    exit 1
fi

# Step 1: Clean previous builds
echo "Step 1: Cleaning previous builds..."
./gradlew clean

# Step 2: Build unsigned release APK
echo ""
echo "Step 2: Building unsigned release APK..."
./gradlew :app:assembleRelease

# Step 3: Find the generated APK
APK_DIR="app/build/outputs/apk/release"
APK_FILE=$(find "$APK_DIR" -name "*.apk" -type f | head -n 1)

if [ -z "$APK_FILE" ]; then
    echo "ERROR: No APK file found in $APK_DIR"
    exit 1
fi

echo "Found APK: $APK_FILE"

# Step 4: Sign the APK
echo ""
echo "Step 3: Signing the APK..."
./sign_apk.sh "$APK_FILE"

echo ""
echo "========================================="
echo "Build and sign completed successfully!"
echo "========================================="
echo ""
echo "Output directory: $APK_DIR"
ls -lh "$APK_DIR"/*.apk "$APK_DIR"/*.sha256 2>/dev/null || true
