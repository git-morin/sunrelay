#!/usr/bin/env bash
set -euo pipefail

version="$1"

# The version code comes from the version itself, so it only grows and never depends on CI run numbers:
# MAJOR*1000000 + MINOR*10000 + PATCH*100, plus the beta number (1-98) for a prerelease or 99 for a release.
if [[ ! "$version" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)(-[0-9A-Za-z]+\.([0-9]+))?$ ]]; then
  echo "can't derive a version code from $version" >&2
  exit 1
fi
major="${BASH_REMATCH[1]}" minor="${BASH_REMATCH[2]}" patch="${BASH_REMATCH[3]}" pre="${BASH_REMATCH[5]:-}"
if (( minor > 99 || patch > 99 )); then echo "minor and patch must stay below 100 for the version code" >&2; exit 1; fi
if [ -n "$pre" ]; then
  (( pre >= 1 && pre <= 98 )) || { echo "prerelease number $pre is out of range 1-98" >&2; exit 1; }
  suffix="$pre"
else
  suffix=99
fi
code=$(( major * 1000000 + minor * 10000 + patch * 100 + suffix ))
echo "version $version -> version code $code"

sed -i -E "s/^( *versionName: ).*/\1\"$version\"/; s/^( *versionCode: ).*/\1$code/" module.yaml
./kotlin build --variant release

apk="$(find build/tasks/_sunrelay_buildAndroidRelease -maxdepth 1 -name '*.apk' | head -n 1)"
[ -n "$apk" ] || { echo "release APK not found" >&2; exit 1; }
case "$apk" in *unsigned*) echo "release APK is unsigned, check the signing secrets" >&2; exit 1 ;; esac

mkdir -p dist
cp "$apk" "dist/sunrelay-$version.apk"
