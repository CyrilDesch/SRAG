#!/usr/bin/env bash
set -e

# Load .env file if it exists
if [ -f .env ]; then
  set -a
  source .env
  set +a
fi

sbt '~srag-infrastructure/reStart'
