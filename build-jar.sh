#!/bin/bash
set -euo pipefail

echo "=== ICU Stats JAR Build Script ==="
echo ""

# Step 1: Run tests
echo ">>> Step 1: Running tests..."
mvn clean test
echo ">>> Tests passed."
echo ""

# Step 2: Build JAR
echo ">>> Step 2: Building JAR..."
mvn clean package -DskipTests
echo ">>> JAR built."
echo ""

# Step 3: Verify JAR exists
JAR_PATH="target/icu-stats-jar-1.0.0.jar"
if [ ! -f "$JAR_PATH" ]; then
    echo "ERROR: JAR not found at $JAR_PATH"
    exit 1
fi
echo ">>> JAR found: $JAR_PATH"
echo ""

# Step 4: Check static resources inside JAR
echo ">>> Step 4: Checking JAR contents..."
echo "Checking static resources..."
jar tf "$JAR_PATH" | grep -E "(static/index\.html|static/js/app\.js|static/css/style\.css|static/vendor/xlsx\.full\.min\.js)" || {
    echo "ERROR: Missing static resources in JAR"
    exit 1
}
echo "All static resources found in JAR."
echo ""

# Step 5: Output JAR info
echo ">>> Step 5: JAR Information"
JAR_SIZE=$(stat -f%z "$JAR_PATH" 2>/dev/null || stat --printf="%s" "$JAR_PATH" 2>/dev/null || wc -c < "$JAR_PATH" | tr -d ' ')
JAR_SHA256=$(sha256sum "$JAR_PATH" 2>/dev/null | cut -d' ' -f1 || shasum -a 256 "$JAR_PATH" 2>/dev/null | cut -d' ' -f1)
echo "JAR Path: $JAR_PATH"
echo "JAR Size: $JAR_SIZE bytes"
echo "SHA-256:  $JAR_SHA256"
echo ""
echo "=== Build Complete ==="
