#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
APP_NAME="Dream Archive"
APP_BUNDLE="$SCRIPT_DIR/$APP_NAME.app"
MACOS_DIR="$APP_BUNDLE/Contents/MacOS"
RES_DIR="$APP_BUNDLE/Contents/Resources"

echo "Building $APP_NAME.app..."

mkdir -p "$MACOS_DIR" "$RES_DIR"

# ── Info.plist ──────────────────────────────────────────────────────────────
cat > "$APP_BUNDLE/Contents/Info.plist" << 'PLIST'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>CFBundleExecutable</key>
  <string>launcher</string>
  <key>CFBundleIdentifier</key>
  <string>com.conndreams.dreamarchive</string>
  <key>CFBundleName</key>
  <string>Dream Archive</string>
  <key>CFBundleDisplayName</key>
  <string>Dream Archive</string>
  <key>CFBundleVersion</key>
  <string>1.0</string>
  <key>CFBundleShortVersionString</key>
  <string>1.0</string>
  <key>CFBundlePackageType</key>
  <string>APPL</string>
  <key>CFBundleIconFile</key>
  <string>AppIcon</string>
  <key>LSMinimumSystemVersion</key>
  <string>10.15</string>
  <key>LSUIElement</key>
  <false/>
  <key>NSHighResolutionCapable</key>
  <true/>
</dict>
</plist>
PLIST

# ── Launcher ────────────────────────────────────────────────────────────────
cat > "$MACOS_DIR/launcher" << 'LAUNCHER'
#!/bin/bash

LAUNCHER_PATH="$(cd "$(dirname "$0")" && pwd)/$(basename "$0")"
MACOS_DIR="$(dirname "$LAUNCHER_PATH")"
CONTENTS_DIR="$(dirname "$MACOS_DIR")"
APP_BUNDLE="$(dirname "$CONTENTS_DIR")"
PROJECT_ROOT="$(dirname "$APP_BUNDLE")"
APP_JS="$PROJECT_ROOT/app/app.js"
ENV_FILE="$PROJECT_ROOT/app/.env"
PID_FILE="$HOME/.dream-archive.pid"
LOG_FILE="$HOME/Library/Logs/DreamArchive.log"
NODE_PID=""

# FIX #13: use env var + system attribute so special chars in messages don't break AppleScript
die() {
  DREAM_MSG="$1" osascript -e 'display alert "Dream Archive" message (system attribute "DREAM_MSG") as critical' 2>/dev/null || true
  exit 1
}

# FIX #24: detect App Translocation (macOS moves un-moved quarantined apps to /private/var/folders/)
if [[ "$LAUNCHER_PATH" == /private/var/folders/* ]]; then
  die "Please move Dream Archive.app out of your Downloads folder to your Desktop or Applications folder, then double-click it again."
fi

# Pre-flight checks
if [ ! -f "$APP_JS" ]; then
  die "Cannot find app/app.js next to Dream Archive.app. Expected: $APP_JS"
fi

if [ ! -f "$ENV_FILE" ]; then
  die "No .env file found. Please create: $ENV_FILE  — and add your OPENAI_API_KEY."
fi

if ! grep -q 'OPENAI_API_KEY' "$ENV_FILE"; then
  die ".env file exists but has no OPENAI_API_KEY entry. File: $ENV_FILE"
fi

# FIX #1: define cleanup and set trap BEFORE starting node
cleanup() {
  if [ -n "${NODE_PID:-}" ]; then
    kill -TERM "$NODE_PID" 2>/dev/null || true
    sleep 0.5
    kill -9 "$NODE_PID" 2>/dev/null || true
  fi
  rm -f "$PID_FILE"
}
trap cleanup EXIT INT TERM QUIT

# Kill any leftover server from prior crash
if [ -f "$PID_FILE" ]; then
  OLD_PID=$(cat "$PID_FILE" 2>/dev/null || true)
  if [ -n "$OLD_PID" ]; then
    # FIX #4: verify it's actually a node process before killing
    OLD_CMD=$(ps -p "$OLD_PID" -o comm= 2>/dev/null || true)
    if [[ "$OLD_CMD" == *node* ]] && kill -0 "$OLD_PID" 2>/dev/null; then
      kill -TERM "$OLD_PID" 2>/dev/null || true
      sleep 0.5
      kill -9 "$OLD_PID" 2>/dev/null || true
    fi
  fi
  rm -f "$PID_FILE"
fi

# FIX #21: single-instance guard — if server already responding, just open browser
if curl -sf http://127.0.0.1:3000/ > /dev/null 2>&1; then
  open "http://127.0.0.1:3000"
  exit 0
fi

# FIX #6/#7: extended Node probe list
NODE_BIN=""
for candidate in \
  "/opt/homebrew/bin/node" \
  "/usr/local/bin/node" \
  "$HOME/.volta/bin/node" \
  "$HOME/.fnm/node-versions/"*/installation/bin/node \
  "$HOME/.nvm/versions/node/"*/bin/node \
  "$HOME/.asdf/shims/node" \
  /opt/homebrew/opt/node@*/bin/node \
  /usr/local/opt/node@*/bin/node \
  "/usr/bin/node"; do
  if [ -x "$candidate" ]; then
    NODE_BIN="$candidate"
    break
  fi
done

if [ -z "$NODE_BIN" ]; then
  NODE_BIN="$(command -v node 2>/dev/null || true)"
fi

if [ -z "$NODE_BIN" ]; then
  DREAM_MSG="Node.js is not installed. Please download and install it from nodejs.org, then try again." \
    osascript -e 'display alert "Dream Archive" message (system attribute "DREAM_MSG") as critical
      open location "https://nodejs.org"' 2>/dev/null || true
  exit 1
fi

# Start server
mkdir -p "$(dirname "$LOG_FILE")"
"$NODE_BIN" "$APP_JS" >> "$LOG_FILE" 2>&1 &
NODE_PID=$!
echo "$NODE_PID" > "$PID_FILE"

# Wait for readiness (10s max)
READY=0
for i in $(seq 1 34); do
  sleep 0.3
  if ! kill -0 "$NODE_PID" 2>/dev/null; then
    LAST_LINES=$(tail -20 "$LOG_FILE" 2>/dev/null || echo "(no log)")
    die "Dream Archive failed to start. Last log: $LAST_LINES"
  fi
  if curl -sf http://127.0.0.1:3000/ > /dev/null 2>&1; then
    READY=1
    break
  fi
done

if [ "$READY" -eq 0 ]; then
  LAST_LINES=$(tail -20 "$LOG_FILE" 2>/dev/null || echo "(no log)")
  die "Dream Archive did not respond after 10 seconds. Last log: $LAST_LINES"
fi

# FIX #10: use 127.0.0.1 not localhost — avoids IPv6 ::1 mismatch on Ventura+
open "http://127.0.0.1:3000"

wait "$NODE_PID"
LAUNCHER

chmod +x "$MACOS_DIR/launcher"

# ── App Icon (Python + iconutil) ────────────────────────────────────────────
ICON_PY=$(mktemp /tmp/gen_icon_XXXXXX.py)
ICONSET_DIR=$(mktemp -d /tmp/AppIcon_XXXXXX.iconset)
MASTER_PNG=$(mktemp /tmp/icon_master_XXXXXX.png)

cat > "$ICON_PY" << 'PYEOF'
import struct, zlib, sys

out_path = sys.argv[1]

W, H = 1024, 1024
img = bytearray(W * H * 4)

BG = (4, 5, 12)
for i in range(W * H):
    img[i*4], img[i*4+1], img[i*4+2], img[i*4+3] = BG[0], BG[1], BG[2], 255

GOLD = (196, 144, 96)
cx, cy, r_outer = W//2, H//2, 340
r_inner = 265
offset_x = 110

def in_circle(px, py, ox, oy, r):
    return (px - ox)**2 + (py - oy)**2 <= r**2

for y in range(H):
    for x in range(W):
        if in_circle(x, y, cx, cy, r_outer) and not in_circle(x, y, cx + offset_x, cy, r_inner):
            i = (y * W + x) * 4
            img[i], img[i+1], img[i+2], img[i+3] = GOLD[0], GOLD[1], GOLD[2], 255

def png_chunk(name, data):
    c = zlib.crc32(name + data) & 0xffffffff
    return struct.pack('>I', len(data)) + name + data + struct.pack('>I', c)

raw = b''
for y in range(H):
    raw += b'\x00' + bytes(img[y*W*4:(y+1)*W*4])

compressed = zlib.compress(raw, 9)
png = (
    b'\x89PNG\r\n\x1a\n'
    + png_chunk(b'IHDR', struct.pack('>IIBBBBB', W, H, 8, 6, 0, 0, 0))
    + png_chunk(b'IDAT', compressed)
    + png_chunk(b'IEND', b'')
)

with open(out_path, 'wb') as f:
    f.write(png)
print(f"Generated {out_path}")
PYEOF

echo "Generating icon..."
python3 "$ICON_PY" "$MASTER_PNG"

# FIX #14: generate source PNGs with src_ prefix, then copy with valid iconutil names only
for SIZE in 16 32 64 128 256 512 1024; do
  sips -z "$SIZE" "$SIZE" "$MASTER_PNG" --out "$ICONSET_DIR/src_${SIZE}.png" > /dev/null 2>&1
done

# Valid iconutil iconset filenames (no spurious 64x64 in the set)
cp "$ICONSET_DIR/src_16.png"   "$ICONSET_DIR/icon_16x16.png"
cp "$ICONSET_DIR/src_32.png"   "$ICONSET_DIR/icon_16x16@2x.png"
cp "$ICONSET_DIR/src_32.png"   "$ICONSET_DIR/icon_32x32.png"
cp "$ICONSET_DIR/src_64.png"   "$ICONSET_DIR/icon_32x32@2x.png"
cp "$ICONSET_DIR/src_128.png"  "$ICONSET_DIR/icon_128x128.png"
cp "$ICONSET_DIR/src_256.png"  "$ICONSET_DIR/icon_128x128@2x.png"
cp "$ICONSET_DIR/src_256.png"  "$ICONSET_DIR/icon_256x256.png"
cp "$ICONSET_DIR/src_512.png"  "$ICONSET_DIR/icon_256x256@2x.png"
cp "$ICONSET_DIR/src_512.png"  "$ICONSET_DIR/icon_512x512.png"
cp "$ICONSET_DIR/src_1024.png" "$ICONSET_DIR/icon_512x512@2x.png"

# Remove the src_ staging files before iconutil sees them
rm "$ICONSET_DIR"/src_*.png

iconutil -c icns "$ICONSET_DIR" -o "$RES_DIR/AppIcon.icns"
rm -rf "$ICONSET_DIR" "$MASTER_PNG" "$ICON_PY"
echo "Icon generated."

# ── Clear quarantine ────────────────────────────────────────────────────────
xattr -cr "$APP_BUNDLE" 2>/dev/null || true

echo ""
echo "Done! Created: $APP_BUNDLE"
echo ""
echo "To send to Conn:"
echo "  - Copy 'Dream Archive.app' via USB or AirDrop to his Mac"
echo "  - Tell him: move it out of Downloads first, then right-click → Open (once)"
echo "  - After that: double-click works forever"
echo ""
echo "If quarantine is re-added after transfer, run on Conn's Mac:"
echo "  xattr -rd com.apple.quarantine ~/Desktop/Dream\\ Archive.app"
