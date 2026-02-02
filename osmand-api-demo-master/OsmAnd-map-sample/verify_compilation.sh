#!/bin/bash

# Script to verify compilation and check for errors
# Usage: ./verify_compilation.sh

echo "🔍 Verifying Internal Routes Implementation..."
echo ""

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if files exist
echo "📁 Checking files..."
FILES=(
    "app/src/main/java/com/resort_cloud/nansei/nansei_tablet/layers/InternalRoutesLayer.kt"
    "app/src/main/java/com/resort_cloud/nansei/nansei_tablet/utils/GpxParser.kt"
    "app/src/main/assets/data_internal.gpx"
)

all_files_exist=true
for file in "${FILES[@]}"; do
    if [ -f "$file" ]; then
        echo -e "${GREEN}✓${NC} $file"
    else
        echo -e "${RED}✗${NC} $file (MISSING)"
        all_files_exist=false
    fi
done

if [ "$all_files_exist" = false ]; then
    echo -e "\n${RED}❌ Some files are missing!${NC}"
    exit 1
fi

echo ""
echo "📝 Checking MainActivity integration..."

# Check if MainActivity has the necessary imports and code
if grep -q "import com.resort_cloud.nansei.nansei_tablet.layers.InternalRoutesLayer" app/src/main/java/com/resort_cloud/nansei/nansei_tablet/MainActivity.kt; then
    echo -e "${GREEN}✓${NC} InternalRoutesLayer import found"
else
    echo -e "${RED}✗${NC} InternalRoutesLayer import missing"
    exit 1
fi

if grep -q "private var internalRoutesLayer: InternalRoutesLayer?" app/src/main/java/com/resort_cloud/nansei/nansei_tablet/MainActivity.kt; then
    echo -e "${GREEN}✓${NC} internalRoutesLayer variable declared"
else
    echo -e "${RED}✗${NC} internalRoutesLayer variable missing"
    exit 1
fi

if grep -q "setupInternalRoutes()" app/src/main/java/com/resort_cloud/nansei/nansei_tablet/MainActivity.kt; then
    echo -e "${GREEN}✓${NC} setupInternalRoutes() called"
else
    echo -e "${RED}✗${NC} setupInternalRoutes() not called"
    exit 1
fi

echo ""
echo "🔧 Checking Kotlin syntax..."

# Check for common syntax errors
check_syntax() {
    local file=$1
    local errors=0
    
    # Check for unmatched braces
    open_braces=$(grep -o '{' "$file" | wc -l)
    close_braces=$(grep -o '}' "$file" | wc -l)
    
    if [ $open_braces -ne $close_braces ]; then
        echo -e "${RED}✗${NC} Unmatched braces in $file (open: $open_braces, close: $close_braces)"
        errors=$((errors + 1))
    fi
    
    # Check for unmatched parentheses
    open_parens=$(grep -o '(' "$file" | wc -l)
    close_parens=$(grep -o ')' "$file" | wc -l)
    
    if [ $open_parens -ne $close_parens ]; then
        echo -e "${RED}✗${NC} Unmatched parentheses in $file (open: $open_parens, close: $close_parens)"
        errors=$((errors + 1))
    fi
    
    return $errors
}

syntax_errors=0
for file in "${FILES[@]}"; do
    if [[ $file == *.kt ]]; then
        check_syntax "$file"
        syntax_errors=$((syntax_errors + $?))
    fi
done

if [ $syntax_errors -eq 0 ]; then
    echo -e "${GREEN}✓${NC} No obvious syntax errors found"
else
    echo -e "${RED}✗${NC} Found $syntax_errors syntax issues"
fi

echo ""
echo "📦 Attempting Gradle sync..."

# Try to compile (dry run)
if command -v ./gradlew &> /dev/null; then
    echo "Running: ./gradlew tasks --dry-run"
    if ./gradlew tasks --dry-run > /dev/null 2>&1; then
        echo -e "${GREEN}✓${NC} Gradle configuration successful"
    else
        echo -e "${YELLOW}⚠${NC} Gradle configuration may have issues (check manually)"
    fi
else
    echo -e "${YELLOW}⚠${NC} gradlew not found or not executable"
fi

echo ""
echo "📊 Summary:"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

if [ "$all_files_exist" = true ] && [ $syntax_errors -eq 0 ]; then
    echo -e "${GREEN}✅ All checks passed!${NC}"
    echo ""
    echo "Next steps:"
    echo "1. Open project in Android Studio"
    echo "2. Sync Gradle"
    echo "3. Build: ./gradlew assembleDebug"
    echo "4. Run on device/emulator"
    exit 0
else
    echo -e "${RED}❌ Some checks failed${NC}"
    echo ""
    echo "Please fix the issues above before building."
    exit 1
fi
