#!/usr/bin/env bash
# Builds build/distributions/psiche.AppImage.
#
# Usage: ./build_appimage.sh [path/to/appimagetool]
#
# appimagetool is looked for, in order: the first argument, $APPIMAGETOOL, then appimagetool on the PATH.
set -euo pipefail

cd "$(dirname "$0")"

# find appimagetool
tool="${1:-${APPIMAGETOOL:-}}"
if [ -z "$tool" ]; then
    tool="$(command -v appimagetool || true)"
fi
if [ -z "$tool" ] || [ ! -x "$tool" ]; then
    echo "appimagetool not found: pass its path, set APPIMAGETOOL, or put appimagetool on the PATH." >&2
    echo "Get it from https://github.com/AppImage/appimagetool/releases" >&2
    exit 1
fi

# build
./gradlew appImage -Pappimagetool="$tool"

echo "Built $(pwd)/build/distributions/psiche.AppImage"
