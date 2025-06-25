#!/bin/bash

# Project structure export script
# Run this from your project root directory

echo "=== PROJECT STRUCTURE ==="
echo "Current directory: $(pwd)"
echo ""

# Show overall structure
echo "=== DIRECTORY TREE ==="
if command -v tree &> /dev/null; then
    tree -I 'target|node_modules|.git|.idea|*.class' -L 4
else
    find . -type d -not -path '*/target/*' -not -path '*/.git/*' -not -path '*/.idea/*' -not -path '*/node_modules/*' | head -50 | sort
fi

echo ""
echo "=== KEY FILES ==="

# Maven/Gradle files
echo "--- Build Files ---"
ls -la pom.xml build.gradle settings.gradle 2>/dev/null || echo "No build files found"

# Application properties
echo "--- Configuration Files ---"
find . -name "application*.properties" -o -name "application*.yml" 2>/dev/null

# Main Java source structure
echo "--- Java Source Structure ---"
if [ -d "src/main/java" ]; then
    find src/main/java -type f -name "*.java" | head -20
else
    echo "No src/main/java directory found"
fi

# Resources
echo "--- Resource Files ---"
if [ -d "src/main/resources" ]; then
    find src/main/resources -type f | head -15
else
    echo "No src/main/resources directory found"
fi

# Templates
echo "--- Template Files ---"
find . -name "*.html" -o -name "*.jsp" -o -name "*.thymeleaf" 2>/dev/null | head -10

# Static files
echo "--- Static Files ---"
find . -path "*/static/*" -type f 2>/dev/null | head -10

echo ""
echo "=== PACKAGE STRUCTURE ==="
if [ -d "src/main/java" ]; then
    find src/main/java -type d | sed 's|src/main/java/||' | grep -v '^$' | sort
fi

echo ""
echo "=== FILE COUNTS ==="
echo "Java files: $(find . -name "*.java" 2>/dev/null | wc -l)"
echo "HTML files: $(find . -name "*.html" 2>/dev/null | wc -l)"
echo "CSS files: $(find . -name "*.css" 2>/dev/null | wc -l)"
echo "JS files: $(find . -name "*.js" 2>/dev/null | wc -l)"