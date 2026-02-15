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

# Find JAR (excluding -plain.jar since that's usually the one without dependencie/sources)
JAR_PATH=$(find build/libs -name "*-plain.jar" -prune -o -name "*.jar" -print | head -n 1)

if [ -z "$JAR_PATH" ]; then
    echo "Error: Could not find built jar in build/libs"
    exit 1
fi
echo "Found JAR: $JAR_PATH"

# Ask for confirmation
read -p "Create GitHub release for $VERSION? (y/N) " confirm
if [[ $confirm != "y" && $confirm != "Y" ]]; then
    echo "Aborted."
    rm -f release_notes.md
    exit 0
fi

echo "Creating release..."
# Create the release. This triggers 'release: types: [published]' workflow.
gh release create "$VERSION" "$JAR_PATH" \
    --title "$VERSION" \
    --notes-file release_notes.md

echo "Release created successfully!"
rm -f release_notes.md
