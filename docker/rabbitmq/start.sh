#!/usr/bin/env bash
set -euo pipefail
rm -f /tmp/checkinn-rabbitmq-ready

# Importing definitions into an empty node suppresses RabbitMQ's default-user seed.
# Keep credentials in runtime environment; never embed them in definitions.json.
docker-entrypoint.sh rabbitmq-server &
broker_pid=$!
stop_broker() {
  kill -TERM "$broker_pid" 2>/dev/null || true
  wait "$broker_pid" || true
}
trap stop_broker EXIT
trap 'exit 143' TERM
trap 'exit 130' INT
until rabbitmq-diagnostics -q check_running >/dev/null 2>&1; do
  if ! kill -0 "$broker_pid" 2>/dev/null; then
    wait "$broker_pid"
    exit 1
  fi
  sleep 2
done

users=$(rabbitmqctl -q list_users --no-table-headers)
if ! printf '%s\n' "$users" | cut -f1 | grep -Fxq -- "$RABBITMQ_DEFAULT_USER"; then
  rabbitmqctl add_user "$RABBITMQ_DEFAULT_USER" "$RABBITMQ_DEFAULT_PASS"
  rabbitmqctl set_user_tags "$RABBITMQ_DEFAULT_USER" administrator
fi
# Grant the new application vhost only when absent; preserve all existing grants.
permissions=$(rabbitmqctl -q list_user_permissions "$RABBITMQ_DEFAULT_USER" --no-table-headers)
if ! printf '%s\n' "$permissions" | cut -f1 | grep -Fxq -- "$RABBITMQ_DEFAULT_VHOST"; then
  rabbitmqctl set_permissions -p "$RABBITMQ_DEFAULT_VHOST" "$RABBITMQ_DEFAULT_USER" '.*' '.*' '.*'
fi
# Do not reset credentials or existing permissions of a persistent user/volume.
rabbitmqctl -q authenticate_user "$RABBITMQ_DEFAULT_USER" "$RABBITMQ_DEFAULT_PASS"
touch /tmp/checkinn-rabbitmq-ready
wait "$broker_pid"
