#!/usr/bin/env bash
# Fails if the Android client source (or a built APK) contains anything that looks like an
# AI provider key or a direct call to the AI provider. The app must only talk to our backend.
set -euo pipefail
apks=()
for a in "$@"; do apks+=("$(realpath "$a")"); done
cd "$(dirname "$0")/.."

PATTERN='sk-ant-|ANTHROPIC_API_KEY|x-api-key|api\.anthropic\.com|AKIA[0-9A-Z]{16}|-----BEGIN [A-Z ]*PRIVATE KEY'
status=0

if grep -rEn --include='*.kt' --include='*.kts' --include='*.xml' --include='*.properties' --include='*.json' \
     --exclude-dir=build --exclude-dir=.gradle "$PATTERN" android; then
  echo "ERROR: secret or direct AI-provider reference found in Android sources" >&2
  status=1
fi

for apk in "${apks[@]}"; do
  tmp=$(mktemp -d)
  unzip -q -o "$apk" -d "$tmp"
  if grep -rEla "$PATTERN" "$tmp"; then
    echo "ERROR: secret or direct AI-provider reference found inside $apk" >&2
    status=1
  fi
  rm -rf "$tmp"
done

[ $status -eq 0 ] && echo "No secrets found in client code${1:+ or APK}."
exit $status
