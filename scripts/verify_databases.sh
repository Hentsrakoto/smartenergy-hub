#!/usr/bin/env bash
# Contrôles en lecture seule ; exécutable depuis n'importe quel répertoire.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

check="Redis"
trap 'printf "ÉCHEC — %s\n" "$check" >&2' ERR

printf 'Redis — PING\n'
redis_ping="$(docker compose exec -T smartenergy-redis redis-cli --raw PING)"
[[ "$redis_ping" == "PONG" ]]
printf '%s\n' "$redis_ping"

check="MongoDB"
printf 'MongoDB — ping (ok: 1)\n'
docker compose exec -T smartenergy-mongo mongosh --quiet --eval \
  'const result = db.adminCommand({ping: 1}); printjson(result); if (result.ok !== 1) { quit(1); }'

check="Cassandra — état UN"
printf 'Cassandra — nodetool status (UN)\n'
cassandra_status="$(docker compose exec -T smartenergy-cassandra nodetool status)"
printf '%s\n' "$cassandra_status"
awk '$1 == "UN" { found = 1 } END { exit !found }' <<< "$cassandra_status"

check="Cassandra — table smartenergy.measurements_by_sensor_day"
printf 'Cassandra — table smartenergy.measurements_by_sensor_day\n'
cassandra_schema="$(docker compose exec -T smartenergy-cassandra cqlsh -e \
  "SELECT table_name FROM system_schema.tables WHERE keyspace_name = 'smartenergy' AND table_name = 'measurements_by_sensor_day';")"
printf '%s\n' "$cassandra_schema"
awk '$1 == "measurements_by_sensor_day" && NF == 1 { found = 1 } END { exit !found }' <<< "$cassandra_schema"

printf 'Toutes les bases sont disponibles et le schéma Cassandra existe.\n'
