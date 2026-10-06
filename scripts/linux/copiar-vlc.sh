#!/usr/bin/env bash
# Copia libvlc, los complementos que usa Aurora y todas sus dependencias a composeApp/resources/linux/vlc,
# y ajusta las rutas (RUNPATH) para que se encuentren entre sí sin instalar nada en el sistema.
# Se ejecuta dentro del contenedor (scripts/empaquetar-linux.sh).
set -euo pipefail
SRC=/usr/lib/x86_64-linux-gnu
OUT="$1"
rm -rf "$OUT"; mkdir -p "$OUT/lib" "$OUT/plugins"

cp -L "$SRC/libvlccore.so.9" "$OUT/"
cp -L "$SRC/libvlc.so.5" "$OUT/"
cp -L "$SRC/libvlc.so.5" "$OUT/libvlc.so"

# Solo los complementos necesarios para reproducir audio y video y sacar fotogramas (sin interfaz Qt, Lua, etc.).
for d in access audio_filter audio_mixer audio_output codec demux misc packetizer stream_filter video_chroma video_filter; do
    cp -r "$SRC/vlc/plugins/$d" "$OUT/plugins/"
done
mkdir -p "$OUT/plugins/video_output"
cp "$SRC/vlc/plugins/video_output/libvmem_plugin.so" "$OUT/plugins/video_output/"
# Fuera lo que no se usa y arrastra dependencias grandes (codificadores, hardware, subtítulos de TV, red).
find "$OUT/plugins" \( -name 'libx26*' -o -name 'libqsv*' -o -name 'libcrystalhd*' -o -name 'libzvbi*' -o -name 'libaribsub*' \
    -o -name 'libkate*' -o -name 'libvdpau*' -o -name 'libva*' -o -name 'libdav1d*' -o -name 'libsmb*' -o -name 'libnfs*' \
    -o -name 'libsftp*' -o -name 'libdvb*' -o -name 'libdtv*' -o -name 'libsatip*' -o -name 'libdc1394*' -o -name 'libdv1394*' \
    -o -name 'liblinsys*' -o -name 'libv4l2*' -o -name 'libxcb*' -o -name 'libgl*' -o -name 'libegl*' -o -name 'libvaapi*' \
    -o -name 'libjack*' -o -name 'libshine*' -o -name 'libtwolame*' -o -name 'libvorbis_*enc*' -o -name 'libsecret*' \
    -o -name 'libnotify*' -o -name 'libudev*' -o -name 'libmtp*' -o -name 'libupnp*' -o -name 'libavahi*' \) -delete

# Bibliotecas que deben ser las del sistema: glibc, gráficos, X11/Wayland, sonido del sistema, D-Bus.
EXCLUDE='^(ld-linux|libc|libm|libdl|libpthread|librt|libresolv|libutil|libgcc_s|libstdc\+\+|libGL|libEGL|libGLX|libGLdispatch|libdrm|libgbm|libX|libxcb|libwayland|libasound|libpulse|libdbus-1|libsystemd|libudev|libz|libexpat|libfontconfig|libfreetype)\.'

changed=1
while [ "$changed" = 1 ]; do
    changed=0
    while IFS= read -r so; do
        while IFS= read -r dep; do
            name=$(basename "$dep")
            [[ "$name" =~ $EXCLUDE ]] && continue
            [ -e "$OUT/lib/$name" ] || [ -e "$OUT/$name" ] && continue
            cp -L "$dep" "$OUT/lib/$name"; changed=1
        done < <(ldd "$so" 2>/dev/null | awk '/=> \//{print $3}')
    done < <(find "$OUT" -name '*.so*' -type f)
done

# Cada biblioteca busca a las demás en la carpeta de la app.
for f in "$OUT"/libvlc*.so*; do patchelf --set-rpath '$ORIGIN:$ORIGIN/lib' "$f"; done
for f in "$OUT"/lib/*.so*; do patchelf --set-rpath '$ORIGIN' "$f"; done
find "$OUT/plugins" -name '*.so' -exec patchelf --set-rpath '$ORIGIN/../../lib:$ORIGIN/../..' {} \;

# Índice de complementos (arranque más rápido).
LD_LIBRARY_PATH="$OUT:$OUT/lib" "$SRC/vlc/vlc-cache-gen" "$OUT/plugins" || true

# Licencias de VLC (LGPL/GPL).
cp /usr/share/doc/libvlc5/copyright "$OUT/LICENCIA-VLC.txt" 2>/dev/null || true
echo "VLC copiado: $(du -sh "$OUT" | cut -f1), $(find "$OUT/plugins" -name '*.so' | wc -l) complementos, $(ls "$OUT/lib" | wc -l) dependencias"
