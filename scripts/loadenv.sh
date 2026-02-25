#!/usr/bin/env bash

# This script loads environment variables from a .env file into your current shell session.
# Usage:
#   source scripts/loadenv.sh
#   sbt "srag-infrastructure/run"

if [ -f .env ]; then
  # Export all variables defined in .env
  set -a
  source .env
  set +a
  echo ".env variables successfully loaded into the current terminal session."
else
  echo "Error: No .env file found in the current directory."
fi
