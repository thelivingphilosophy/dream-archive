#!/bin/bash
cd "$(dirname "$0")/app"
echo ""
echo "  Starting Conn's Dream Archive..."
echo "  The browser will open automatically."
echo "  Close this window to stop."
echo ""
node app.js
