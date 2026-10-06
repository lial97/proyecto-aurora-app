# Entorno para empaquetar Aurora para Linux con VLC adentro.
# Ubuntu 22.04 (glibc 2.35): lo que se compila aquí funciona también en distribuciones más nuevas.
FROM eclipse-temurin:21-jdk-jammy
RUN apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        libvlc5 libvlccore9 vlc-plugin-base vlc-bin \
        patchelf fakeroot binutils file wget ca-certificates \
    && rm -rf /var/lib/apt/lists/*
# appimagetool (se ejecuta sin FUSE con --appimage-extract-and-run).
RUN wget -q -O /usr/local/bin/appimagetool https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-x86_64.AppImage \
    && chmod +x /usr/local/bin/appimagetool
