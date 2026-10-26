#!/usr/bin/env bash
# Compila la app dentro de Docker, sin instalar el SDK de Android.
#
#   android/compilar.sh                  → assembleDebug
#   android/compilar.sh test             → pruebas unitarias y capturas
#   android/compilar.sh <tareas Gradle>  → las tareas que se pidan
#
# Para firmar en local: TINTA_KEYSTORE=<ruta del .jks> y
# TINTA_KEYSTORE_PASSWORD=<contraseña> en el entorno (ver README.md).
# La caché de Gradle vive en el volumen tinta-android-gradle.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
imagen=tinta-android-build:local
docker image inspect "$imagen" >/dev/null 2>&1 || docker build -t "$imagen" -f "$here/build.Dockerfile" "$here"

case "${1:-}" in
  "") tasks=(assembleDebug) ;;
  test) tasks=(testDebugUnitTest) ;;
  *) tasks=("$@") ;;
esac

# Con TINTA_DEV_URL (por ejemplo http://127.0.0.1:8091), las pruebas de
# integración hablan con la copia de desarrollo del servicio: hace falta la red
# de la máquina.
red=()
if [[ -n "${TINTA_DEV_URL:-}" ]]; then
  red=(--network host -e TINTA_DEV_URL)
fi

firma=()
if [[ -n "${TINTA_KEYSTORE:-}" ]]; then
  firma=(-v "$TINTA_KEYSTORE:/firma/tinta.jks:ro" -e TINTA_KEYSTORE=/firma/tinta.jks -e TINTA_KEYSTORE_PASSWORD)
fi

# Mismos límites que la compilación de Atalaya: 6 GB y 4 CPU.
exec docker run --rm -i --memory=6g --cpus=4 \
  --user "$(id -u):$(id -g)" -e HOME=/tmp -e GRADLE_USER_HOME=/gradle \
  -v tinta-android-gradle:/gradle -v "$here":/src -w /src \
  ${firma[@]+"${firma[@]}"} ${red[@]+"${red[@]}"} \
  "$imagen" ./gradlew --no-daemon --max-workers=2 \
  -Dorg.gradle.jvmargs="-Xmx3g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8" \
  -Pkotlin.compiler.execution.strategy=in-process "${tasks[@]}"
