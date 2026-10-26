#!/bin/bash
# Corre cargo dentro de Docker, sin instalar Rust en la máquina.
# Uso: scripts/cargo.sh build | test | add ...
# La caché de dependencias vive en ~/.cache/tinta-cargo; target/ en el repo.
set -euo pipefail
raiz=$(cd "$(dirname "$0")/.." && pwd)
mkdir -p "$HOME/.cache/tinta-cargo"
exec docker run --rm -i \
  --user "$(id -u):$(id -g)" \
  -e CARGO_HOME=/cargo -e HOME=/tmp \
  -v "$HOME/.cache/tinta-cargo:/cargo" \
  -v "$raiz:/src" -w /src \
  rust:1.98-slim-trixie cargo "$@"
