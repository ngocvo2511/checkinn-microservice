#!/usr/bin/env bash
set -euo pipefail
# This worker has no web starter, so it does not expose an HTTP actuator endpoint.
consumers=$(curl -fsS --connect-timeout 3 --max-time 6 \
  -u "${SPRING_RABBITMQ_USERNAME}:${SPRING_RABBITMQ_PASSWORD}" \
  "http://${SPRING_RABBITMQ_HOST}:15672/api/consumers/${SPRING_RABBITMQ_VIRTUALHOST}")
grep -q '"name":"notification.otp.queue"' <<< "$consumers"
grep -q '"name":"notification.payment.queue"' <<< "$consumers"
timeout 5 bash -c 'true > /dev/tcp/$1/$2' _ "$SPRING_MAIL_HOST" "$SPRING_MAIL_PORT"
