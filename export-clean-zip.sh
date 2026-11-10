#!/usr/bin/env bash
# Creates a clean distribution ZIP of the URL shortener project.
# Strictly excludes .env (secrets), .git (history), target/ (jars), *.log, and *.zip.
set -euo pipefail
cd "$(dirname "$0")"

OUT_ZIP="../url-shortener-demo-clean.zip"
LOCAL_ZIP="./url-shortener-demo-clean.zip"
rm -f "$OUT_ZIP" "$LOCAL_ZIP"

echo "Creating clean archive..."
zip -q -r "$OUT_ZIP" . \
  -x ".env*" \
  -x ".git/*" \
  -x ".git" \
  -x "target/*" \
  -x "target" \
  -x "*.log" \
  -x "*.zip" \
  -x ".DS_Store" \
  -x "*/.DS_Store" \
  -x "package-lock.json"

cp "$OUT_ZIP" "$LOCAL_ZIP"

echo ""
echo "=========================================================="
echo " Clean archive created successfully!"
echo " Location: $LOCAL_ZIP (and $OUT_ZIP)"
echo " Size: $(du -h "$LOCAL_ZIP" | cut -f1)"
echo "=========================================================="
echo ""
echo "Verifying exclusions:"
if unzip -l "$OUT_ZIP" | grep -E '\.env$|\.git/|target/|\.log$'; then
  echo "ERROR: Sensitive or build files detected in archive!"
  exit 1
else
  echo "PASS: Zero credentials (.env), git history (.git), or build artifacts (target/logs) found."
fi
echo ""
