#!/bin/bash

# MCP Server Complete Setup
# This script performs all steps needed to set up the MCP server

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "========================================"
echo "SRAG MCP Server - Complete Setup"
echo "========================================"
echo ""

# Step 1: Check Docker
echo "Step 1/4: Checking Docker services..."
if ! docker compose ps --status running | grep -q postgres; then
  echo "Starting Docker services..."
  docker compose up -d --wait
  echo "✅ Docker services started"
else
  echo "✅ Docker services already running"
fi
echo ""

# Step 2: Compile
echo "Step 2/4: Compiling project..."
if sbt 'srag-infrastructure/compile' > /tmp/srag-compile.log 2>&1; then
  echo "✅ Project compiled successfully"
else
  echo "❌ Compilation failed. Check /tmp/srag-compile.log"
  tail -20 /tmp/srag-compile.log
  exit 1
fi
echo ""

# Step 3: Assembly
echo "Step 3/4: Building JAR (this may take 1-2 minutes)..."
if sbt 'srag-infrastructure/assembly' > /tmp/srag-assembly.log 2>&1; then
  echo "✅ JAR built successfully"
  JAR_PATH=$(find "$PROJECT_ROOT/srag-infrastructure/target/scala-3.7.3" -name "srag-infrastructure-assembly-*.jar" 2>/dev/null | head -n 1)
  JAR_SIZE=$(du -h "$JAR_PATH" | cut -f1)
  echo "   Location: $JAR_PATH"
  echo "   Size: $JAR_SIZE"
else
  echo "❌ Assembly failed. Check /tmp/srag-assembly.log"
  tail -20 /tmp/srag-assembly.log
  exit 1
fi
echo ""

# Step 4: Verify
echo "Step 4/4: Running status check..."
if "$SCRIPT_DIR/mcpStatus.sh"; then
  echo ""
  echo "========================================"
  echo "✅ Setup Complete!"
  echo "========================================"
  echo ""
  echo "Next steps:"
  echo "  1. Configure Claude Desktop:"
  echo "     Edit: ~/.config/Claude/claude_desktop_config.json"
  echo "     Add:"
  echo '     {'
  echo '       "mcpServers": {'
  echo '         "srag": {'
  echo "           \"command\": \"$SCRIPT_DIR/mcpServer.sh\","
  echo '           "args": [],'
  echo '           "env": {}'
  echo '         }'
  echo '       }'
  echo '     }'
  echo ""
  echo "  2. Restart Claude Desktop"
  echo ""
  echo "  3. Test with Claude!"
  echo ""
  echo "📖 Documentation: docs/MCP_QUICKSTART.md"
  echo "🔧 Logs: tail -f /tmp/srag-mcp.log"
else
  echo ""
  echo "❌ Setup verification failed"
  exit 1
fi
