#!/usr/bin/env bash
# Pasos dentro del contenedor (ver scripts/empaquetar-linux.sh).
set -euo pipefail
VERSION=$(grep -oP 'packageVersion = "\K[^"]+' composeApp/build.gradle.kts)

bash scripts/linux/copiar-vlc.sh composeApp/resources/linux/vlc

./gradlew --no-daemon -q :composeApp:createReleaseDistributable :composeApp:packageReleaseDeb

# .deb con nuestro postrm: al desinstalar borra la configuración y la caché (al actualizar no).
# Compose no deja cambiar los scripts del paquete, así que se abre el .deb, se cambia el postrm y se vuelve a armar.
mkdir -p AURORA-APP
DEB=$(ls composeApp/build/compose/binaries/main-release/deb/*.deb)
DEBDIR=$(mktemp -d)/deb
dpkg-deb -R "$DEB" "$DEBDIR"
install -m 755 scripts/linux/postrm "$DEBDIR/DEBIAN/postrm"
dpkg-deb --root-owner-group -Zxz -b "$DEBDIR" "AURORA-APP/$(basename "$DEB")" >/dev/null

# AppImage: la misma app (con su Java y su VLC) en un solo archivo ejecutable.
APPDIR=$(mktemp -d)/Aurora.AppDir
mkdir -p "$APPDIR"
cp -a composeApp/build/compose/binaries/main-release/app/Aurora "$APPDIR/Aurora"
cat > "$APPDIR/AppRun" <<'RUN'
#!/bin/sh
HERE="$(dirname "$(readlink -f "$0")")"
# Al abrir el AppImage, Aurora se agrega sola al menú de aplicaciones (y se actualiza si el archivo
# cambió de lugar). Para quitarla del menú: borrar ~/.local/share/applications/aurora.desktop.
# "Aurora-x86_64.AppImage --desinstalar" quita Aurora del menú y borra su configuración y su caché
# (después basta con borrar el archivo .AppImage).
if [ "$1" = "--desinstalar" ]; then
    DATA="${XDG_DATA_HOME:-$HOME/.local/share}"
    rm -rf "${XDG_CONFIG_HOME:-$HOME/.config}/aurora" "${XDG_CACHE_HOME:-$HOME/.cache}/aurora" "$DATA/aurora"
    rm -f "$DATA/applications/aurora.desktop"
    update-desktop-database "$DATA/applications" >/dev/null 2>&1 || true
    echo "Aurora: se borraron la configuración, la caché y el acceso del menú. Ya puedes borrar el archivo .AppImage."
    exit 0
fi
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
ARCH=x86_64 appimagetool --appimage-extract-and-run --no-appstream "$APPDIR" "AURORA-APP/Aurora-$VERSION-x86_64.AppImage" >/dev/null
echo "AppImage y .deb en AURORA-APP/"
