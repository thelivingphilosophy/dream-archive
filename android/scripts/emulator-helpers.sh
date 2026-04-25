#!/usr/bin/env bash
# Emulator helper functions for Claude Code testing loop
# Usage: source scripts/emulator-helpers.sh

# Ensure adb is on PATH
export PATH="$PATH:/c/Users/james/AppData/Local/Android/Sdk/platform-tools"

# Target only the emulator — never a physical device
ADB="adb -s emulator-5554"

# Default screenshot directory
EMU_SCREENSHOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/.emu-screenshots"
mkdir -p "$EMU_SCREENSHOT_DIR"

# Take a screenshot and save as PNG
# Usage: emu_screenshot [path]
emu_screenshot() {
  local path="${1:-$EMU_SCREENSHOT_DIR/screenshot_$(date +%s).png}"
  $ADB exec-out screencap -p > "$path"
  echo "$path"
}

# Dump UIAutomator XML hierarchy
# Usage: emu_dump_ui [path]
emu_dump_ui() {
  local path="${1:-$EMU_SCREENSHOT_DIR/ui_dump.xml}"
  $ADB shell uiautomator dump //sdcard/ui_dump.xml 2>/dev/null
  $ADB pull //sdcard/ui_dump.xml "$path" 2>/dev/null
  echo "$path"
}

# List all content-desc (accessibilityLabel) values on screen
# Usage: emu_list_elements
emu_list_elements() {
  local dump_path="$EMU_SCREENSHOT_DIR/_tmp_dump.xml"
  emu_dump_ui "$dump_path" > /dev/null 2>&1
  grep -oP 'content-desc="[^"]*"' "$dump_path" | \
    sed 's/content-desc="//;s/"$//' | \
    sort -u | \
    grep -v '^$'
}

# Tap an element by its accessibilityLabel (content-desc)
# Usage: emu_tap_label "Label Text"
emu_tap_label() {
  local label="$1"
  if [ -z "$label" ]; then
    echo "Usage: emu_tap_label \"Label Text\""
    return 1
  fi

  local dump_path="$EMU_SCREENSHOT_DIR/_tmp_dump.xml"
  emu_dump_ui "$dump_path" > /dev/null 2>&1

  # Find the element with matching content-desc and extract bounds
  local bounds
  bounds=$(grep -oP "content-desc=\"${label}\"[^>]*bounds=\"\[\d+,\d+\]\[\d+,\d+\]\"" "$dump_path" | \
    grep -oP 'bounds="\[\d+,\d+\]\[\d+,\d+\]"' | head -1)

  if [ -z "$bounds" ]; then
    echo "Element with label '${label}' not found"
    echo "Available labels:"
    emu_list_elements
    return 1
  fi

  # Parse bounds [x1,y1][x2,y2] and compute center
  local x1 y1 x2 y2
  x1=$(echo "$bounds" | grep -oP '\[\K\d+' | sed -n '1p')
  y1=$(echo "$bounds" | grep -oP ',\K\d+' | sed -n '1p')
  x2=$(echo "$bounds" | grep -oP '\[\K\d+' | sed -n '2p')
  y2=$(echo "$bounds" | grep -oP ',\K\d+' | sed -n '2p')

  local cx=$(( (x1 + x2) / 2 ))
  local cy=$(( (y1 + y2) / 2 ))

  echo "Tapping '${label}' at (${cx}, ${cy})"
  $ADB shell input tap "$cx" "$cy"
}

# Tap at raw coordinates
# Usage: emu_tap X Y
emu_tap() {
  $ADB shell input tap "$1" "$2"
}

# Type text into the currently focused input
# Usage: emu_type "some text"
emu_type() {
  # Replace spaces with %s for adb input
  local text="${1// /%s}"
  $ADB shell input text "$text"
}

# Press Android back button
# Usage: emu_back
emu_back() {
  $ADB shell input keyevent KEYCODE_BACK
}

# Press Android home button
# Usage: emu_home
emu_home() {
  $ADB shell input keyevent KEYCODE_HOME
}

# Scroll down (swipe up gesture)
# Usage: emu_scroll_down
emu_scroll_down() {
  $ADB shell input swipe 540 1500 540 500 300
}

# Scroll up (swipe down gesture)
# Usage: emu_scroll_up
emu_scroll_up() {
  $ADB shell input swipe 540 500 540 1500 300
}

# Wait for an element to appear on screen
# Usage: emu_wait_for "Label" [timeout_seconds]
emu_wait_for() {
  local label="$1"
  local timeout="${2:-10}"
  local elapsed=0

  while [ "$elapsed" -lt "$timeout" ]; do
    local dump_path="$EMU_SCREENSHOT_DIR/_tmp_dump.xml"
    emu_dump_ui "$dump_path" > /dev/null 2>&1

    if grep -q "content-desc=\"${label}\"" "$dump_path" 2>/dev/null; then
      echo "Found '${label}' after ${elapsed}s"
      return 0
    fi

    sleep 1
    elapsed=$((elapsed + 1))
  done

  echo "Timeout: '${label}' not found after ${timeout}s"
  return 1
}

# Clean up temporary screenshots
# Usage: emu_cleanup
emu_cleanup() {
  rm -f "$EMU_SCREENSHOT_DIR"/_tmp_dump.xml
  rm -f "$EMU_SCREENSHOT_DIR"/screenshot_*.png
  echo "Cleaned up emulator screenshots"
}

echo "Emulator helpers loaded. Available commands:"
echo "  emu_screenshot [path]       - Take PNG screenshot"
echo "  emu_dump_ui [path]          - Dump UIAutomator XML"
echo "  emu_list_elements           - List all accessibility labels"
echo "  emu_tap_label \"Label\"       - Tap element by label"
echo "  emu_tap X Y                 - Tap at coordinates"
echo "  emu_type \"text\"             - Type into focused input"
echo "  emu_back                    - Press back button"
echo "  emu_home                    - Press home button"
echo "  emu_scroll_down             - Scroll down"
echo "  emu_scroll_up               - Scroll up"
echo "  emu_wait_for \"Label\" [sec]  - Wait for element"
echo "  emu_cleanup                 - Clean temp files"
