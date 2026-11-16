#!/usr/bin/env bash
set -euo pipefail

APP_PATHS='^(src/|res/|test/|module\.yaml$|kotlin$|kotlin\.bat$|\.github/workflows/|\.github/scripts/)'

if [ -z "${BASE:-}" ] || [[ "$BASE" =~ ^0+$ ]] || ! git cat-file -e "$BASE^{commit}" 2>/dev/null; then
  echo "No usable base commit, running every job"
  echo "app=true" >> "$GITHUB_OUTPUT"
  exit 0
fi

changed="$(git diff --name-only "$(git merge-base "$BASE" "$HEAD")" "$HEAD")"
echo "Changed files:"
echo "$changed"

if grep -qE "$APP_PATHS" <<< "$changed"; then app=true; else app=false; fi
echo "app=$app"
echo "app=$app" >> "$GITHUB_OUTPUT"
