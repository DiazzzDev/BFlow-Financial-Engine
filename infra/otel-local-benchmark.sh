#!/usr/bin/env bash
# Local benchmark WITH vs WITHOUT the ADOT agent, approximating Fargate (0.25 vCPU / 1 GB).
# Run from the repo root:  bash ./infra/otel-local-benchmark.sh
# Needs Docker and a git-ignored .env.local (same variable names as the task definition, no quotes around values).
# Env vars: URL, N (requests per case), ROUNDS (sin+con rounds), START_TIMEOUT (seconds),
#           HOST_PORT, ADOT_JAR (local jar to use instead of downloading it inside `docker build`).
set -euo pipefail
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) DEVNULL=NUL ;; *) DEVNULL=/dev/null ;; esac

HOST_PORT="${HOST_PORT:-8080}"
URL="${URL:-http://localhost:${HOST_PORT}/actuator/health}"
N="${N:-300}"
IMG=bflow-otel-test
NAME=bflow-test

[ -f .env.local ] || { echo "Falta .env.local en la raíz del repo"; exit 1; }
trap 'docker rm -f "$NAME" >/dev/null 2>&1 || true' EXIT
rm -f /tmp/bench-summary.txt

if [ -n "${ADOT_JAR:-}" ]; then
  # Solo local: cambia el ADD remoto por un COPY de un jar descargado a mano, verificando el sha256 del Dockerfile
  [ "$(basename "$ADOT_JAR")" = "aws-opentelemetry-agent.jar" ] || { echo "ADOT_JAR debe llamarse aws-opentelemetry-agent.jar"; exit 1; }
  want=$(grep -o 'ADOT_SHA256=[0-9a-f]*' Dockerfile | head -1 | cut -d= -f2)
  have=$(sha256sum "$ADOT_JAR" | cut -d' ' -f1)
  [ -n "$want" ] && [ "$want" = "$have" ] || { echo "sha256 del jar ($have) no coincide con el Dockerfile ($want)"; exit 1; }
  awk '{ sub(/\r$/, "") }
       /^ADD --checksum/ { skip=1; print "COPY --from=agent --chmod=644 aws-opentelemetry-agent.jar /opt/otel/aws-opentelemetry-agent.jar" }
       skip { if ($0 !~ /\\$/) skip=0; next }
       { print }' Dockerfile \
    | docker build --build-context agent="$(dirname "$ADOT_JAR")" -f - -t "$IMG" .
else
  docker build -t "$IMG" .   # also validates the agent version and sha256 in the Dockerfile
fi

pct() { sort -n | awk -v p="$1" '{a[NR]=$1} END{i=int(NR*p/100); if(i<1)i=1; printf "%.0f ms", a[i]*1000}'; }
count() { wc -l < "$1" | tr -d ' '; }

run_case() {
  local mode="$1"; shift
  docker rm -f "$NAME" >/dev/null 2>&1 || true
  docker run -d --name "$NAME" --cpus=0.25 --memory=1g --env-file .env.local -p "${HOST_PORT}:8080" "$@" "$IMG" >/dev/null

  local t0 logs; t0=$(date +%s)
  while true; do
    logs=$(docker logs "$NAME" 2>&1 || true)
    [[ "$logs" =~ Started\ .*\ seconds ]] && break
    if [ "$(docker inspect -f '{{.State.Running}}' "$NAME")" != "true" ]; then
      echo "[$mode] el contenedor murió"; echo "$logs" | tail -30; return 1
    fi
    if [ $(( $(date +%s) - t0 )) -gt "${START_TIMEOUT:-300}" ]; then
      echo "[$mode] no arrancó dentro del tiempo límite"; echo "$logs" | tail -30; return 1
    fi
    sleep 1
  done
  local started; started=$(echo "$logs" | grep -o 'Started .* seconds' | tail -1)
  echo "[$mode] arranque (reloj): $(( $(date +%s) - t0 ))s | $started"
  echo "[$mode] usuario del proceso: $(docker exec "$NAME" id -un)"
  echo "[$mode] HTTP de prueba: $(curl -s -o "$DEVNULL" --max-time 30 -w '%{http_code}' "$URL" || echo "fallo rc=$?")"

  for _ in $(seq 20); do curl -s -o "$DEVNULL" --max-time 30 "$URL" || true; done   # warm-up

  # Carga en segundo plano. Los fallos de curl NO abortan: se registran en err-<modo>.txt
  : > "/tmp/lat-$mode.txt"; : > "/tmp/err-$mode.txt"
  (
    set +e
    for _ in $(seq "$N"); do
      out=$(curl -s -o "$DEVNULL" --max-time 30 -w '%{http_code} %{time_total}' "$URL"); rc=$?
      if [ "$rc" -eq 0 ] && [ "${out%% *}" = "200" ]; then
        echo "${out#* }" >> "/tmp/lat-$mode.txt"
      else
        echo "curl_rc=$rc salida='$out'" >> "/tmp/err-$mode.txt"
      fi
    done
  ) &
  local load=$!
  sleep 5
  local stats; stats=$(docker stats --no-stream --format 'cpu={{.CPUPerc}} mem={{.MemUsage}}' "$NAME" || echo "stats no disponible")
  echo "[$mode] bajo carga: $stats"
  wait "$load" || true

  local ok bad; ok=$(count "/tmp/lat-$mode.txt"); bad=$(count "/tmp/err-$mode.txt")
  echo "[$mode] requests ok=$ok errores=$bad (N=$N)"
  if [ "$bad" -gt 0 ]; then echo "[$mode] primeros errores:"; head -3 "/tmp/err-$mode.txt"; fi
  local p50="n/a" p95="n/a"
  if [ "$ok" -gt 0 ]; then
    p50=$(pct 50 < "/tmp/lat-$mode.txt"); p95=$(pct 95 < "/tmp/lat-$mode.txt")
  fi
  echo "[$mode] p50=$p50  p95=$p95"
  echo "[$mode] OOMKilled=$(docker inspect -f '{{.State.OOMKilled}}' "$NAME")"
  docker logs "$NAME" > "/tmp/log-$mode.txt" 2>&1
  echo "[$mode] líneas de access log JSON: $(grep -c '"type":"access"' "/tmp/log-$mode.txt" || true)"
  grep -m2 '"type":"access"' "/tmp/log-$mode.txt" || true
  echo "$mode | $started | p50=$p50 p95=$p95 | $stats | errores=$bad" >> /tmp/bench-summary.txt
}

AGENT_ENV=(
  -e JAVA_TOOL_OPTIONS=-javaagent:/opt/otel/aws-opentelemetry-agent.jar
  -e OTEL_SERVICE_NAME=bflow-backend
  -e OTEL_RESOURCE_ATTRIBUTES=deployment.environment=dev
  -e OTEL_TRACES_EXPORTER=logging          # si tu versión no lo acepta, prueba "console"
  -e OTEL_METRICS_EXPORTER=none
  -e OTEL_LOGS_EXPORTER=none
  -e OTEL_AWS_APPLICATION_SIGNALS_ENABLED=false
  -e OTEL_TRACES_SAMPLER=parentbased_traceidratio
  -e OTEL_TRACES_SAMPLER_ARG=0.1
)

for _ in $(seq "${ROUNDS:-2}"); do
  run_case sin
  run_case con "${AGENT_ENV[@]}"
done

echo
echo "== Resumen =="
cat /tmp/bench-summary.txt
echo
echo "== Revisión de /tmp/log-con.txt =="
echo "spans exportados (LoggingSpanExporter): $(grep -c 'LoggingSpanExporter' /tmp/log-con.txt || true)"
echo "líneas de log con trace_id: $(grep -c 'trace_id=[0-9a-f]\{32\}' /tmp/log-con.txt || true)  (0 es normal si ningún log de la app ocurre durante las requests)"
echo "posibles datos sensibles (revisa a mano; el arranque puede mencionar nombres de configuración):"
grep -Eio 'bearer [a-z0-9._-]+|authorization|password=[^ ]*' /tmp/log-con.txt | sort | uniq -c | head || true