#!/bin/bash
set -e

# check requirements
if ! command -v gh &> /dev/null; then
    echo "Error: GitHub CLI (gh) is not installed."
    echo "Please install it: https://cli.github.com/"
    exit 1
fi

if ! gh auth status &> /dev/null; then
    echo "Error: You are not logged into GitHub CLI."
    echo "Please run 'gh auth login' first."
    exit 1
fi

echo "Building project..."
./gradlew build

echo "Extracting version from CHANGELOG.md..."
# Grep the first header line (e.g. "## v1.1.0") and take the second word ("v1.1.0")
VERSION=$(grep -m 1 "^## " CHANGELOG.md | awk '{print $2}')

if [ -z "$VERSION" ]; then
    echo "Error: Could not extract version from CHANGELOG.md"
    exit 1
fi
echo "Detected version: $VERSION"

echo "Extracting release notes..."
# Extract text starting from the first header until the next header
awk '/^## / { if (p) { exit }; p=1; } p' CHANGELOG.md > release_notes.md

echo "--- Preview Notes ---"
cat release_notes.md
echo "---------------------"

# Find JAR (excluding -plain.jar, -sources.jar, -javadoc.jar)
JAR_PATH=$(find build/libs \( -name "*-plain.jar" -o -name "*-sources.jar" -o -name "*-javadoc.jar" \) -prune -o -name "*.jar" -print | head -n 1)

if [ -z "$JAR_PATH" ]; then
    echo "Error: Could not find built jar in build/libs"
    exit 1
fi
echo "Found JAR: $JAR_PATH"

# Find Sources JAR
SOURCES_JAR_PATH=$(find build/libs -name "*-sources.jar" -print | head -n 1)
if [ -n "$SOURCES_JAR_PATH" ]; then
    echo "Found Sources JAR: $SOURCES_JAR_PATH"
else
    echo "Warning: No sources JAR found."
fi

# Ask for confirmation
read -p "Create GitHub release for $VERSION? (y/N) " confirm
if [[ $confirm != "y" && $confirm != "Y" ]]; then
    echo "Aborted."
    rm -f release_notes.md
    exit 0
fi

echo "Creating release..."
# Create the release. This triggers 'release: types: [published]' workflow.
# Upload JAR, Sources JAR (if exists), and the hytaleServer.version file as assets
FILES_TO_UPLOAD="$JAR_PATH build/generated/hytaleServer.version"
if [ -n "$SOURCES_JAR_PATH" ]; then
    FILES_TO_UPLOAD="$FILES_TO_UPLOAD $SOURCES_JAR_PATH"
fi

gh release create "$VERSION" $FILES_TO_UPLOAD \
    --title "$VERSION" \
    --notes-file release_notes.md

echo "Release created successfully!"
rm -f release_notes.md
