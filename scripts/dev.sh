#!/usr/bin/env bash
# v0.0.1 🍊 Local development launcher: MySQL (if installed), backend on JDK 21, frontend dev server.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"

start_mysql() {
  if command -v mysqladmin >/dev/null 2>&1; then
    if ! mysqladmin -uroot ping >/dev/null 2>&1; then
      echo "🍊 Starting MySQL..."
      mysql.server start
    fi
  fi
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
