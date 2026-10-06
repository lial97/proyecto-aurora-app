#!/usr/bin/env bash
# Instaladores de Aurora para Linux, con VLC adentro (no hay que instalar nada aparte):
#   dist/Aurora-<versión>-x86_64.AppImage   → cualquier distribución: doble clic y listo
#   dist/aurora_<versión>_amd64.deb         → Ubuntu, Debian, Mint: doble clic → Instalar
# Necesita Docker. Se compila sobre Ubuntu 22.04 para que funcione también en distribuciones viejas.
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT="$PWD"
IMAGE=aurora-empaquetar-linux

echo "==> [1/3] Preparando el entorno (solo tarda la primera vez)"
docker build -q -t "$IMAGE" scripts/linux >/dev/null

# El contenedor usa la misma caché de Gradle que este equipo: se cierran los procesos de Gradle abiertos.
./gradlew --stop -q >/dev/null 2>&1 || true

echo "==> [2/3] Copiando VLC y compilando (puede tardar varios minutos)"
# Mismo usuario y misma ruta que fuera: los archivos quedan a tu nombre y Gradle reutiliza lo ya compilado.
docker run --rm -u "$(id -u):$(id -g)" \
    -v "$ROOT:$ROOT" -w "$ROOT" \
    -v "$HOME/.gradle:/gradle" -e GRADLE_USER_HOME=/gradle -e HOME=/tmp \
    "$IMAGE" bash scripts/linux/dentro.sh

echo "==> [3/3] Listo"
ls -lh dist/*.AppImage dist/*.deb 2>/dev/null
