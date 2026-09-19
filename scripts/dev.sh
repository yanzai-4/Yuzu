#!/usr/bin/env bash
# v0.0.2 🍊 Local development launcher: project MySQL (port 3307), backend on JDK 21, frontend dev server.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"

start_mysql() {
  "$ROOT/scripts/db.sh" start
}

start_backend() {
  echo "🍊 Starting backend on :8080 (JDK 21)..."
  (cd "$ROOT/backend" && mvn -q spring-boot:run) &
}

start_frontend() {
  if [ -d "$ROOT/frontend" ]; then
    echo "🍊 Starting frontend on :5173..."
    (cd "$ROOT/frontend" && npm run dev) &
  fi
}

trap 'kill 0' INT TERM
start_mysql
start_backend
start_frontend
wait
