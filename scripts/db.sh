#!/usr/bin/env bash
# v0.0.2 🍊 Manages the project-local MySQL instance used by Yuzu (isolated data dir, port 3307).
#
# Usage: scripts/db.sh start | stop | status | init-db | shell
# The instance never touches the system MySQL data directory. Its data lives in data/mysql (gitignored).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DATA_DIR="$ROOT/data/mysql"
RUN_DIR="$ROOT/data/mysql-run"
CNF="$RUN_DIR/my.cnf"
PORT="${YUZU_DB_PORT:-3307}"

write_cnf() {
  mkdir -p "$RUN_DIR"
  cat > "$CNF" <<EOF
[mysqld]
datadir=$DATA_DIR
port=$PORT
socket=$RUN_DIR/mysql.sock
pid-file=$RUN_DIR/mysql.pid
log-error=$RUN_DIR/mysql.err
bind-address=127.0.0.1
mysqlx=OFF
# ngram FULLTEXT drops every token that CONTAINS a stopword ('a', 'i', ...): disable stopwords globally.
innodb_ft_enable_stopword=OFF
character-set-server=utf8mb4
collation-server=utf8mb4_0900_ai_ci
default-time-zone=+00:00
innodb_buffer_pool_size=256M
max_connections=200

[client]
port=$PORT
socket=$RUN_DIR/mysql.sock
user=root
EOF
}

is_up() {
  mysqladmin --defaults-file="$CNF" ping >/dev/null 2>&1
}

start() {
  write_cnf
  if [ ! -d "$DATA_DIR/mysql" ]; then
    echo "🍊 Initializing a fresh MySQL data directory at $DATA_DIR"
    mkdir -p "$DATA_DIR"
    mysqld --defaults-file="$CNF" --initialize-insecure
  fi
  if is_up; then
    echo "🍊 MySQL already running on 127.0.0.1:$PORT"
    return
  fi
  echo "🍊 Starting MySQL on 127.0.0.1:$PORT"
  nohup mysqld --defaults-file="$CNF" >/dev/null 2>&1 &
  for _ in $(seq 1 60); do
    if is_up; then
      echo "🍊 MySQL is up"
      return
    fi
    sleep 0.5
  done
  echo "MySQL did not start; see $RUN_DIR/mysql.err" >&2
  exit 1
}

stop() {
  write_cnf
  if is_up; then
    mysqladmin --defaults-file="$CNF" shutdown
    echo "🍊 MySQL stopped"
  fi
}

init_db() {
  start
  mysql --defaults-file="$CNF" < "$ROOT/scripts/db-init.sql"
  echo "🍊 Databases yuzu / yuzu_test and user yuzu are ready"
}

case "${1:-start}" in
  start) start ;;
  stop) stop ;;
  status) write_cnf; if is_up; then echo "up"; else echo "down"; fi ;;
  init-db) init_db ;;
  shell) write_cnf; exec mysql --defaults-file="$CNF" "${@:2}" ;;
  *) echo "usage: $0 start|stop|status|init-db|shell" >&2; exit 2 ;;
esac
