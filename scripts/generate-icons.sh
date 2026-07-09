#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ICON_BASENAME="ImgDiff"
SRC_SVG="$ROOT_DIR/frontend/src/assets/${ICON_BASENAME}.svg"
OUT_DIR="$ROOT_DIR/src/main/resources/icons"
REL_OUT_DIR="src/main/resources/icons"
TMP_DIR="$(mktemp -d)"

cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

if [[ ! -f "$SRC_SVG" ]]; then
  echo "Missing source SVG: ${ICON_BASENAME}.svg" >&2
  exit 1
fi

if ! command -v qlmanage >/dev/null 2>&1; then
  echo "qlmanage is required (macOS Quick Look)." >&2
  exit 1
fi

if ! command -v sips >/dev/null 2>&1; then
  echo "sips is required (macOS image tool)." >&2
  exit 1
fi

if ! command -v iconutil >/dev/null 2>&1; then
  echo "iconutil is required (macOS icon tool)." >&2
  exit 1
fi

mkdir -p "$OUT_DIR"
PNG_WORK_DIR="$TMP_DIR/png"
mkdir -p "$PNG_WORK_DIR"

# Render a large PNG from SVG once, then downscale into platform sizes.
qlmanage -t -s 1024 -o "$TMP_DIR" "$SRC_SVG" >/dev/null 2>&1
MASTER_PNG="$(find "$TMP_DIR" -maxdepth 1 -type f -name '*.png' | head -n 1)"

if [[ -z "$MASTER_PNG" || ! -f "$MASTER_PNG" ]]; then
  echo "Failed to render PNG from SVG with qlmanage." >&2
  exit 1
fi

for size in 16 32 48 64 128 256 512 1024; do
  sips -z "$size" "$size" "$MASTER_PNG" --out "$PNG_WORK_DIR/icon-${size}.png" >/dev/null
done

cp "$PNG_WORK_DIR/icon-512.png" "$OUT_DIR/${ICON_BASENAME}.png"

ICONSET_DIR="$TMP_DIR/${ICON_BASENAME}.iconset"
mkdir -p "$ICONSET_DIR"
cp "$PNG_WORK_DIR/icon-16.png" "$ICONSET_DIR/icon_16x16.png"
cp "$PNG_WORK_DIR/icon-32.png" "$ICONSET_DIR/icon_16x16@2x.png"
cp "$PNG_WORK_DIR/icon-32.png" "$ICONSET_DIR/icon_32x32.png"
cp "$PNG_WORK_DIR/icon-64.png" "$ICONSET_DIR/icon_32x32@2x.png"
cp "$PNG_WORK_DIR/icon-128.png" "$ICONSET_DIR/icon_128x128.png"
cp "$PNG_WORK_DIR/icon-256.png" "$ICONSET_DIR/icon_128x128@2x.png"
cp "$PNG_WORK_DIR/icon-256.png" "$ICONSET_DIR/icon_256x256.png"
cp "$PNG_WORK_DIR/icon-512.png" "$ICONSET_DIR/icon_256x256@2x.png"
cp "$PNG_WORK_DIR/icon-512.png" "$ICONSET_DIR/icon_512x512.png"
cp "$PNG_WORK_DIR/icon-1024.png" "$ICONSET_DIR/icon_512x512@2x.png"
iconutil -c icns "$ICONSET_DIR" -o "$OUT_DIR/${ICON_BASENAME}.icns"

if command -v magick >/dev/null 2>&1; then
  magick \
    "$PNG_WORK_DIR/icon-16.png" \
    "$PNG_WORK_DIR/icon-32.png" \
    "$PNG_WORK_DIR/icon-48.png" \
    "$PNG_WORK_DIR/icon-64.png" \
    "$PNG_WORK_DIR/icon-128.png" \
    "$PNG_WORK_DIR/icon-256.png" \
    "$OUT_DIR/${ICON_BASENAME}.ico"
else
  echo "Skipping ${ICON_BASENAME}.ico generation (install ImageMagick 'magick')." >&2
fi

echo "Generated icon assets for: ${ICON_BASENAME}"
echo "Output directory: ${REL_OUT_DIR}"
ls -1 "$OUT_DIR" | sed "s|^| - ${REL_OUT_DIR}/|"

