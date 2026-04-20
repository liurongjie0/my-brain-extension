#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
PLIST_NAME="com.user.ai-daily"
PLIST_PATH="$HOME/Library/LaunchAgents/${PLIST_NAME}.plist"

# 创建 LaunchAgent
mkdir -p "$HOME/Library/LaunchAgents"

cat > "$PLIST_PATH" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>${PLIST_NAME}</string>
    <key>ProgramArguments</key>
    <array>
        <string>$(which python3)</string>
        <string>${PROJECT_DIR}/run.py</string>
    </array>
    <key>WorkingDirectory</key>
    <string>${PROJECT_DIR}</string>
    <key>StartCalendarInterval</key>
    <dict>
        <key>Hour</key>
        <integer>8</integer>
        <key>Minute</key>
        <integer>0</integer>
    </dict>
    <key>StandardOutPath</key>
    <string>${PROJECT_DIR}/data/stdout.log</string>
    <key>StandardErrorPath</key>
    <string>${PROJECT_DIR}/data/stderr.log</string>
    <key>EnvironmentVariables</key>
    <dict>
        <key>PATH</key>
        <string>/usr/local/bin:/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin</string>
    </dict>
</dict>
</plist>
EOF

# 加载
launchctl unload "$PLIST_PATH" 2>/dev/null || true
launchctl load "$PLIST_PATH"

echo "✅ 定时任务已安装"
echo "   每天早 8:00 自动运行"
echo "   plist 文件: $PLIST_PATH"
echo ""
echo "常用命令:"
echo "   手动运行一次: launchctl start $PLIST_NAME"
echo "   查看状态:     launchctl list | grep $PLIST_NAME"
echo "   卸载:         launchctl unload $PLIST_PATH"
