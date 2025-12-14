#!/bin/bash

# MCP Server Launcher Script
# This script executes the assembled SRAG MCP Server JAR
# to integrate with Claude Code via stdin/stdout

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Auto-detect the assembled JAR
JAR_DIR="$PROJECT_ROOT/srag-infrastructure/target/scala-3.7.3"
JAR_PATH=$(find "$JAR_DIR" -name "srag-infrastructure-assembly-*.jar" 2>/dev/null | head -n 1)

# Check if JAR exists
if [ -z "$JAR_PATH" ] || [ ! -f "$JAR_PATH" ]; then
  echo "Error: JAR file not found in $JAR_DIR" >&2
  echo "Please run: sbt 'srag-infrastructure/assembly' to build the JAR first" >&2
  exit 1
fi

# Log to file to avoid polluting stdin/stdout
LOG_FILE="/tmp/srag-mcp.log"

# Redirect all logs to file FIRST, then write diagnostic message
exec 2>> "$LOG_FILE"

echo "[$(date '+%Y-%m-%d %H:%M:%S')] Starting SRAG MCP Server..." >&2
echo "[$(date '+%Y-%m-%d %H:%M:%S')] Using JAR: $JAR_PATH" >&2
echo "[$(date '+%Y-%m-%d %H:%M:%S')] Working directory: $PROJECT_ROOT" >&2
echo "[$(date '+%Y-%m-%d %H:%M:%S')] Java version: $(java -version 2>&1 | head -1)" >&2

# Execute the MCP server with MCP-specific logback config (logs to file only)
exec java \
  -Dfile.encoding=UTF-8 \
  -Dlogback.configurationFile="$PROJECT_ROOT/srag-infrastructure/src/main/resources/logback-mcp.xml" \
  -cp "$JAR_PATH" \
  com.cyrelis.srag.infrastructure.MCPMain
