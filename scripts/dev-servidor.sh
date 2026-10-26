#!/bin/bash
# Compila y arranca una copia de desarrollo de Tinta en el puerto 8091, con
# sus propios datos en datos-dev/. Escucha en TINTA_DEV_IP (por defecto
# 127.0.0.1; pon la IP de Tailscale para abrirlo desde otro equipo).
#   scripts/dev-servidor.sh          compila y (re)arranca
#   scripts/dev-servidor.sh parar    lo para
# Claves de desarrollo: web «tinta», app «dev-clave». Sin HTTPS, por eso la
# cookie va sin Secure (TINTA_COOKIE_INSEGURA).
set -euo pipefail
raiz=$(cd "$(dirname "$0")/.." && pwd)
ip=${TINTA_DEV_IP:-127.0.0.1}
docker rm -f tinta-dev >/dev/null 2>&1 || true
[[ "${1:-}" == parar ]] && exit 0
"$raiz/scripts/cargo.sh" build -p tinta-server
mkdir -p "$raiz/datos-dev" "$raiz/web/dist"
docker run -d --name tinta-dev \
  --user "$(id -u):$(id -g)" \
  -p "$ip:8091:8080" \
  -e TINTA_API_KEY=dev-clave -e TINTA_WEB_PASSWORD=tinta -e TINTA_COOKIE_INSEGURA=1 \
  -e RUST_LOG=tinta_server=debug \
  -v "$raiz/target/debug/tinta-server:/usr/local/bin/tinta-server:ro" \
  -v "$raiz/datos-dev:/data" \
  -v "$raiz/web/dist:/srv/web:ro" \
  rust:1.98-slim-trixie tinta-server >/dev/null
for _ in $(seq 20); do
  curl -sf "http://$ip:8091/health" >/dev/null && { echo "tinta-dev en http://$ip:8091"; exit 0; }
  sleep 0.5
done
docker logs tinta-dev; exit 1
