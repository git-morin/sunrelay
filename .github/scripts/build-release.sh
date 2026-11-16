#!/usr/bin/env bash
set -euo pipefail

version="$1"
code="${VERSION_CODE:?VERSION_CODE is required}"

sed -i -E "s/^( *versionName: ).*/\1\"$version\"/; s/^( *versionCode: ).*/\1$code/" module.yaml
./kotlin build --variant release

apk="$(find build/tasks/_sunrelay_buildAndroidRelease -maxdepth 1 -name '*.apk' | head -n 1)"
[ -n "$apk" ] || { echo "release APK not found" >&2; exit 1; }
case "$apk" in *unsigned*) echo "release APK is unsigned, check the signing secrets" >&2; exit 1 ;; esac

mkdir -p dist
cp "$apk" "dist/sunrelay-$version.apk"
