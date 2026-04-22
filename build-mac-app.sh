# RETIRED — use `npm run dist:mac` instead (electron-builder).
# This script built the old shell-launcher .app bundle and is no longer needed.
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

# ── App Icon (iconutil) ──────────────────────────────────────────────────────
ICONSET_DIR=$(mktemp -d /tmp/AppIcon_XXXXXX.iconset)
MASTER_PNG="$SCRIPT_DIR/AppIcon.png"

if [ ! -f "$MASTER_PNG" ]; then
  echo "Error: AppIcon.png not found at $MASTER_PNG"
  exit 1
fi

: << 'PYEOF'  # kept for reference — icon is now a committed PNG (AppIcon.png)
import struct, zlib, sys, math

out_path = sys.argv[1]
W, H = 1024, 1024
CX, CY = W // 2, H // 2

# Background: #04050c
img = bytearray(b'\x04\x05\x0c\xff' * (W * H))

def put(x, y, r, g, b, a):
    if x < 0 or x >= W or y < 0 or y >= H or a <= 0: return
    idx = (y * W + x) * 4
    a = min(1.0, a)
    img[idx]   = int(img[idx]   + (r - img[idx])   * a)
    img[idx+1] = int(img[idx+1] + (g - img[idx+1]) * a)
    img[idx+2] = int(img[idx+2] + (b - img[idx+2]) * a)

GOLD  = (196, 144, 96)
GOLD2 = (224, 184, 128)

def circle(cx, cy, rad, w, col, op=1.0, dash=0):
    cr, cg, cb = col
    hw = w / 2.0
    y0 = max(0, int(cy - rad - hw - 2))
    y1 = min(H - 1, int(cy + rad + hw + 2))
    for y in range(y0, y1 + 1):
        dy = y - cy
        out_sq = (rad + hw + 1) ** 2
        inn_sq = max(0.0, (rad - hw - 1) ** 2)
        if dy * dy > out_sq: continue
        xo = math.sqrt(out_sq - dy * dy)
        xi = math.sqrt(inn_sq - dy * dy) if dy * dy < inn_sq else 0.0
        for xa, xb in [
            (max(0, int(cx - xo) - 1), min(W - 1, int(cx - xi) + 2)),
            (max(0, int(cx + xi) - 1), min(W - 1, int(cx + xo) + 2)),
        ]:
            for x in range(xa, xb + 1):
                dx = x - cx
                d = abs(math.sqrt(dx * dx + dy * dy) - rad)
                a = max(0.0, 1.0 - max(0.0, d - hw)) * op
                if a > 0:
                    if dash:
                        ang = math.atan2(dy, dx) % (2 * math.pi)
                        if int(ang / (2 * math.pi / dash)) % 2 == 0: continue
                    put(x, y, cr, cg, cb, a)

def line(x1, y1, x2, y2, w, col, op=1.0):
    cr, cg, cb = col
    hw = w / 2.0
    ddx = x2 - x1; ddy = y2 - y1
    L2 = ddx * ddx + ddy * ddy
    if L2 == 0: return
    L = math.sqrt(L2)
    margin = hw + 1.5
    ly0 = max(0, int(min(y1, y2) - margin))
    ly1 = min(H - 1, int(max(y1, y2) + margin))
    for y in range(ly0, ly1 + 1):
        if ddy != 0:
            xc = x1 + (y - y1) * ddx / ddy
            xs = margin * L / abs(ddy)
            xa = max(0, int(xc - xs) - 1)
            xb = min(W - 1, int(xc + xs) + 2)
        else:
            xa = max(0, int(min(x1, x2) - margin))
            xb = min(W - 1, int(max(x1, x2) + margin))
        for x in range(xa, xb + 1):
            t = max(0.0, min(1.0, ((x - x1) * ddx + (y - y1) * ddy) / L2))
            px = x1 + t * ddx; py = y1 + t * ddy
            d = math.sqrt((x - px) ** 2 + (y - py) ** 2)
            a = max(0.0, 1.0 - max(0.0, d - hw)) * op
            if a > 0: put(x, y, cr, cg, cb, a)

def dot(cx, cy, rad, col, op=1.0):
    cr, cg, cb = col
    for y in range(max(0, int(cy - rad - 1)), min(H, int(cy + rad + 2))):
        for x in range(max(0, int(cx - rad - 1)), min(W, int(cx + rad + 2))):
            d = math.sqrt((x - cx) ** 2 + (y - cy) ** 2)
            a = max(0.0, 1.0 - max(0.0, d - rad)) * op
            if a > 0: put(x, y, cr, cg, cb, a)

# Concentric circles (mirroring the bg-sigil in the HTML)
circle(CX, CY, 380, 1.8, GOLD2, op=0.9)
circle(CX, CY, 320, 1.2, GOLD,  op=0.55, dash=14)
circle(CX, CY, 240, 1.8, GOLD2, op=0.85)
circle(CX, CY, 160, 1.2, GOLD,  op=0.55, dash=12)
circle(CX, CY,  80, 1.8, GOLD,  op=0.8)

# Cardinal axes
line(CX, CY - 380, CX, CY + 380, 1.0, GOLD, op=0.55)
line(CX - 380, CY, CX + 380, CY, 1.0, GOLD, op=0.55)

# Diagonal axes
D = int(380 / math.sqrt(2))
line(CX - D, CY - D, CX + D, CY + D, 0.8, GOLD, op=0.4)
line(CX + D, CY - D, CX - D, CY + D, 0.8, GOLD, op=0.4)

# Hexagram — two overlapping triangles (Star of Solomon)
# Coordinates scaled 2× from the 400px SVG viewBox, centred at 512
TU = [(512, 152), (858, 692), (166, 692)]   # apex up
TD = [(512, 872), (166, 332), (858, 332)]   # apex down
for pts in (TU, TD):
    for i in range(3):
        ax, ay = pts[i]; bx, by = pts[(i + 1) % 3]
        line(ax, ay, bx, by, 1.2, GOLD2, op=0.72)

# Cardinal dots at outer-circle intercepts
for px, py in [(CX, CY - 380), (CX, CY + 380), (CX - 380, CY), (CX + 380, CY)]:
    dot(px, py, 5, GOLD2, op=0.9)

# Subtle inner glow — the dreaming eye at centre
dot(CX, CY, 76, GOLD, op=0.09)

def png_chunk(name, data):
    c = zlib.crc32(name + data) & 0xffffffff
    return struct.pack('>I', len(data)) + name + data + struct.pack('>I', c)

raw = b''
for y in range(H):
    raw += b'\x00' + bytes(img[y * W * 4:(y + 1) * W * 4])

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
rm -rf "$ICONSET_DIR"
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
