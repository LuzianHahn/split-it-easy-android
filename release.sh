#!/bin/bash

if [ $# -ne 1 ]; then
    echo "Usage: $0 <commit-hash>"
    exit 1
fi

BASE_COMMIT="$1"
CHANGELOG="CHANGELOG.md"
GRADLE_FILE="./app/build.gradle"

# Get commit messages since BASE_COMMIT
COMMITS=$(git log --pretty=format:"%s" "$BASE_COMMIT"..HEAD | grep -E "^(fix:|feat:).*")

# Check for feat: and fix: commits
has_feat=0
has_fix=0

while read -r line; do
    if [[ "$line" == feat:* ]]; then
        has_feat=1
    elif [[ "$line" == fix:* ]]; then
        has_fix=1
    fi
done <<< "$COMMITS"

# Read current version from build.gradle
current_version=$(grep "versionName" $GRADLE_FILE | head -n 1 | xargs | cut -d" " -f2)
current_version_code=$(grep "versionCode" $GRADLE_FILE | head -n 1 | xargs | cut -d" " -f2)
echo "Found current version infos: "
echo "versionName: $current_version"
echo "versionCode: $current_version_code"
IFS='.' read -ra ver <<< "$current_version"
major=${ver[0]}
minor=${ver[1]}
micro=${ver[2]}
new_version_code=$current_version_code

# Bump version
if [ $has_feat -eq 1 ]; then
    minor=$((minor + 1))
    micro=0
    new_version_code=$((current_version_code + 1))    
elif [ $has_fix -eq 1 ]; then
    micro=$((micro + 1))
else
    echo "No feat: or fix: commits detected. Exiting."
    exit 0
fi

new_version="$major.$minor.$micro"

# Update build.gradle
sed -i -E "s/versionName \"[0-9]+\.[0-9]+\.[0-9]+\"/versionName \"$new_version\"/" "$GRADLE_FILE"
sed -i -E "s/versionCode [0-9]+/versionCode $new_version_code/" "$GRADLE_FILE"

# Prepare changelog entry
echo -e "# $new_version\n" > temp_changelog
echo "$COMMITS" | while read -r line; do
    echo "- $line" >> temp_changelog
done
echo -e "\n" >> temp_changelog

# Prepend to Changelog.md
if [ -f "$CHANGELOG" ]; then
    cat "$CHANGELOG" >> temp_changelog
else
    echo "No existing Changelog.md found. Creating one."
fi
mv temp_changelog "$CHANGELOG"

echo "Version bumped to $new_version / $new_version_code and changelog updated."

