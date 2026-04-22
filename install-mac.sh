#!/bin/bash
DIR="$(cd "$(dirname "$0")" && pwd)"

echo ""
echo "=================================="
echo "  Dream Archive — Mac Setup"
echo "=================================="
echo ""

# ── Node.js ───────────────────────────
if command -v node &>/dev/null; then
  echo "✓ Node.js already installed ($(node -v))"
else
  echo "✗ Node.js not found."
  echo ""
  echo "  Please download and install it from:"
  echo "  https://nodejs.org  (click the big green LTS button)"
  echo ""
  echo "  After installing, run this script again."
  echo ""
  read -p "Press Enter to close..."
  exit 1
fi

echo ""

# ── ffmpeg (local, no password needed) ───
FFMPEG_BIN="$DIR/app/ffmpeg"

if command -v ffmpeg &>/dev/null; then
  echo "✓ ffmpeg already installed"
elif [ -f "$FFMPEG_BIN" ]; then
  echo "✓ ffmpeg already downloaded"
else
  echo "→ Downloading ffmpeg (no password needed)..."
  curl -L "https://evermeet.cx/ffmpeg/getrelease/zip" -o /tmp/ffmpeg.zip
  if [ $? -ne 0 ]; then
    echo ""
    echo "✗ Could not download ffmpeg — check internet connection and try again."
    read -p "Press Enter to close..."
    exit 1
  fi
  unzip -o /tmp/ffmpeg.zip ffmpeg -d /tmp/ffmpeg-out
  cp /tmp/ffmpeg-out/ffmpeg "$FFMPEG_BIN"
  chmod +x "$FFMPEG_BIN"
  rm -rf /tmp/ffmpeg.zip /tmp/ffmpeg-out
  echo "✓ ffmpeg downloaded"
fi

echo ""
echo "=================================="
echo "  All done! You can close this."
echo "  Run the app with: run.sh"
echo "=================================="
echo ""
