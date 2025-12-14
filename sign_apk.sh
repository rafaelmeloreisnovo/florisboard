#!/bin/bash
# FlorisBoard APK Signing Script
# 
# This script signs APK files using apksigner with the configuration from signing.properties
# Usage: ./sign_apk.sh <apk_file>
# 
# Requirements:
# - apksigner (from Android SDK build-tools)
# - signing.properties file with keystore configuration

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SIGNING_CONFIG="$SCRIPT_DIR/signing.properties"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored messages
print_error() {
    echo -e "${RED}ERROR: $1${NC}" >&2
}

print_success() {
    echo -e "${GREEN}SUCCESS: $1${NC}"
}

print_info() {
    echo -e "${YELLOW}INFO: $1${NC}"
}

# Check if APK file is provided
if [ -z "$1" ]; then
    print_error "No APK file specified"
    echo "Usage: $0 <apk_file>"
    echo ""
    echo "Example:"
    echo "  $0 app/build/outputs/apk/release/app-release-unsigned.apk"
    exit 1
fi

APK_FILE="$1"

# Check if APK file exists
if [ ! -f "$APK_FILE" ]; then
    print_error "APK file not found: $APK_FILE"
    exit 1
fi

# Check if signing.properties exists
if [ ! -f "$SIGNING_CONFIG" ]; then
    print_error "Signing configuration not found: $SIGNING_CONFIG"
    echo ""
    echo "Please create signing.properties from the template:"
    echo "  1. cp signing.properties.template signing.properties"
    echo "  2. Edit signing.properties with your keystore information"
    exit 1
fi

# Load signing configuration
print_info "Loading signing configuration..."
source "$SIGNING_CONFIG"

# Validate required variables
if [ -z "$KEYSTORE_FILE" ]; then
    print_error "KEYSTORE_FILE not set in signing.properties"
    exit 1
fi

if [ -z "$KEYSTORE_ALIAS" ]; then
    print_error "KEYSTORE_ALIAS not set in signing.properties"
    exit 1
fi

if [ -z "$KEYSTORE_PASSWORD" ]; then
    print_error "KEYSTORE_PASSWORD not set in signing.properties"
    exit 1
fi

if [ -z "$KEY_PASSWORD" ]; then
    print_error "KEY_PASSWORD not set in signing.properties"
    exit 1
fi

# Resolve keystore path (handle relative paths)
if [[ "$KEYSTORE_FILE" != /* ]]; then
    KEYSTORE_FILE="$SCRIPT_DIR/$KEYSTORE_FILE"
fi

# Check if keystore file exists
if [ ! -f "$KEYSTORE_FILE" ]; then
    print_error "Keystore file not found: $KEYSTORE_FILE"
    exit 1
fi

# Find apksigner
APKSIGNER=""
if command -v apksigner &> /dev/null; then
    APKSIGNER="apksigner"
else
    # Try to find apksigner in common Android SDK locations
    if [ -n "$ANDROID_HOME" ]; then
        APKSIGNER_PATH="$(find "$ANDROID_HOME/build-tools" -name apksigner 2>/dev/null | sort -V | tail -n 1)"
        if [ -n "$APKSIGNER_PATH" ]; then
            APKSIGNER="$APKSIGNER_PATH"
        fi
    fi
fi

if [ -z "$APKSIGNER" ]; then
    print_error "apksigner not found"
    echo ""
    echo "Please ensure apksigner is installed and available:"
    echo "  - Install Android SDK build-tools"
    echo "  - Set ANDROID_HOME environment variable"
    echo "  - Or add apksigner to your PATH"
    exit 1
fi

print_info "Using apksigner: $APKSIGNER"

# Prepare output file path
APK_DIR="$(dirname "$APK_FILE")"
APK_BASENAME="$(basename "$APK_FILE" .apk)"
APK_SIGNED="$APK_DIR/${APK_BASENAME}-signed.apk"

# Remove existing signed APK if it exists
if [ -f "$APK_SIGNED" ]; then
    print_info "Removing existing signed APK..."
    rm "$APK_SIGNED"
fi

# Sign the APK
print_info "Signing APK..."
print_info "Input:  $APK_FILE"
print_info "Output: $APK_SIGNED"

"$APKSIGNER" sign \
    --ks "$KEYSTORE_FILE" \
    --ks-key-alias "$KEYSTORE_ALIAS" \
    --ks-pass "pass:$KEYSTORE_PASSWORD" \
    --key-pass "pass:$KEY_PASSWORD" \
    --out "$APK_SIGNED" \
    "$APK_FILE"

# Verify the signature
print_info "Verifying signature..."
"$APKSIGNER" verify --verbose "$APK_SIGNED"

print_success "APK signed successfully!"
print_info "Signed APK location: $APK_SIGNED"

# Calculate SHA256 checksum
print_info "Calculating SHA256 checksum..."
cd "$APK_DIR"
APK_SIGNED_NAME="$(basename "$APK_SIGNED")"
sha256sum "$APK_SIGNED_NAME" > "${APK_SIGNED_NAME}.sha256"
print_success "Checksum saved to: ${APK_SIGNED}.sha256"

echo ""
print_success "All done!"
echo ""
echo "Signed APK: $APK_SIGNED"
echo "Checksum:   ${APK_SIGNED}.sha256"
