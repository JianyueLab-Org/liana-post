#!/bin/sh
set -eu

run_base_sql_dir() {
  dir="$1"
  if [ ! -d "$dir" ]; then
    return 0
  fi

  for sql_file in "$dir"/*.sql; do
    if [ ! -f "$sql_file" ]; then
      continue
    fi
    db_name="$(basename "$sql_file" .sql)"
    echo "Running ${sql_file}"
    mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -e "CREATE DATABASE IF NOT EXISTS \`${db_name}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
    mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" "${db_name}" < "$sql_file"
  done
}

run_raw_sql_dir() {
  dir="$1"
  if [ ! -d "$dir" ]; then
    return 0
  fi

  for sql_file in "$dir"/*.sql; do
    if [ ! -f "$sql_file" ]; then
      continue
    fi
    echo "Running ${sql_file}"
    mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" < "$sql_file"
  done
}

run_base_sql_dir /demo-db
run_raw_sql_dir /demo-db-revise
