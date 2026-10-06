#!/usr/bin/env bash
# Pasos dentro del contenedor (ver scripts/empaquetar-linux.sh).
set -euo pipefail
VERSION=$(grep -oP 'packageVersion = "\K[^"]+' composeApp/build.gradle.kts)

bash scripts/linux/copiar-vlc.sh composeApp/resources/linux/vlc

./gradlew --no-daemon -q :composeApp:createReleaseDistributable :composeApp:packageReleaseDeb

mkdir -p dist
cp composeApp/build/compose/binaries/main-release/deb/*.deb dist/

# AppImage: la misma app (con su Java y su VLC) en un solo archivo ejecutable.
APPDIR=$(mktemp -d)/Aurora.AppDir
mkdir -p "$APPDIR"
cp -a composeApp/build/compose/binaries/main-release/app/Aurora "$APPDIR/Aurora"
cat > "$APPDIR/AppRun" <<'RUN'
#!/bin/sh
HERE="$(dirname "$(readlink -f "$0")")"
# Al abrir el AppImage, Aurora se agrega sola al menú de aplicaciones (y se actualiza si el archivo
# cambió de lugar). Para quitarla del menú: borrar ~/.local/share/applications/aurora.desktop.
if [ -n "$APPIMAGE" ]; then
    DATA="${XDG_DATA_HOME:-$HOME/.local/share}"
    DESK="$DATA/applications/aurora.desktop"
    if ! grep -qsF "Exec=\"$APPIMAGE\"" "$DESK"; then
        mkdir -p "$DATA/applications" "$DATA/aurora"
        cp -f "$HERE/aurora.png" "$DATA/aurora/aurora.png"
        cat > "$DESK" <<DESK
[Desktop Entry]
Type=Application
Name=Aurora
GenericName=Reproductor de música
Comment=Reproductor de música y video
Exec="$APPIMAGE" %F
Icon=$DATA/aurora/aurora.png
Categories=AudioVideo;Audio;Video;Player;
MimeType=audio/mpeg;audio/flac;audio/ogg;audio/mp4;audio/x-wav;video/mp4;video/x-matroska;video/webm;
StartupWMClass=app-aurora-MainKt
Terminal=false
DESK
        update-desktop-database "$DATA/applications" >/dev/null 2>&1 || true
    fi
fi
exec "$HERE/Aurora/bin/Aurora" "$@"
RUN
chmod +x "$APPDIR/AppRun"
cp composeApp/icons/aurora.png "$APPDIR/aurora.png"
cat > "$APPDIR/aurora.desktop" <<DESK
[Desktop Entry]
Type=Application
Name=Aurora
Comment=Reproductor de música y video
Exec=Aurora
Icon=aurora
Categories=AudioVideo;Audio;Video;Player;
Terminal=false
DESK
ARCH=x86_64 appimagetool --appimage-extract-and-run --no-appstream "$APPDIR" "dist/Aurora-$VERSION-x86_64.AppImage" >/dev/null
echo "AppImage y .deb en dist/"
