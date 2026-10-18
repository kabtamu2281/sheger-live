#!/usr/bin/env bash
# Fails if server-side secrets appear in client code (Android app, admin dashboard) or in a built APK/AAB.
# Usage: tools/check-no-secrets.sh            # scan android/ and admin/src
#        tools/check-no-secrets.sh app.apk    # scan a build output
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PATTERNS='sb_secret_|CHASECK-|CHASECK_TEST-|CHASECK_LIVE-|-----BEGIN (RSA |EC )?PRIVATE KEY-----|"private_key"[[:space:]]*:|"type"[[:space:]]*:[[:space:]]*"service_account"|SUPABASE_SERVICE_ROLE_KEY[[:space:]]*=[[:space:]]*[A-Za-z0-9]|AGORA_APP_CERTIFICATE[[:space:]]*=[[:space:]]*[A-Za-z0-9]|sk_live_[A-Za-z0-9]'

scan_dir() {
  grep -rEn --binary-files=without-match --exclude-dir=node_modules --exclude-dir=build --exclude-dir=.gradle --exclude-dir=dist \
    --exclude='*.example' --exclude='check-no-secrets.sh' "$PATTERNS" "$@" || true
}

found=""
if [ "$#" -eq 0 ]; then
  found="$(scan_dir "$ROOT/android" "$ROOT/admin/src")"
  # A service-role JWT carries "role":"service_role" in its payload; flag any JWT whose payload decodes to that.
  while IFS= read -r jwt; do
    payload="$(printf '%s' "$jwt" | cut -d. -f2 | tr '_-' '/+')"
    while [ $(( ${#payload} % 4 )) -ne 0 ]; do payload="$payload="; done
    if printf '%s' "$payload" | base64 -d 2>/dev/null | grep -q '"service_role"'; then found="$found"$'\n'"service-role JWT found"; fi
  done < <(grep -rhoE --exclude-dir=node_modules --exclude-dir=build --exclude-dir=.gradle 'eyJ[A-Za-z0-9_-]{10,}\.eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}' "$ROOT/android" "$ROOT/admin/src" 2>/dev/null || true)
else
  for f in "$@"; do
    tmp="$(mktemp -d)"
    unzip -q -o "$f" -d "$tmp"
    found="$found$(grep -rEal "$PATTERNS" "$tmp" || true)"
    rm -rf "$tmp"
  done
fi

if [ -n "$(printf '%s' "$found" | tr -d '[:space:]')" ]; then
  echo "Possible secrets found in client code or build output:"
  printf '%s\n' "$found"
  exit 1
fi
echo "No server secrets found."
