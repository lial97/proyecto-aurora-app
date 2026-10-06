# Descargar Aurora

Aurora es un reproductor de música y video. **No hace falta instalar nada más**: cada instalador ya trae
todo lo necesario (Java y VLC van adentro).

Todos los archivos están en la página de descargas:
**https://github.com/lial97/proyecto-aurora-app/releases/latest**

| Sistema | Archivo | Cómo se instala |
|---|---|---|
| **Android** | [Aurora-0.3.0-android.apk](Aurora-0.3.0-android.apk) (está en esta carpeta) | Ábrelo en el celular y toca **Instalar**. Si el celular lo pide, permite "instalar apps de fuentes desconocidas". |
| **Windows** | [Aurora-0.3.0.exe](https://github.com/lial97/proyecto-aurora-app/releases/download/v0.3.0/Aurora-0.3.0.exe) | Doble clic → **Siguiente** → **Siguiente** → **Finalizar**. Si aparece "Windows protegió su PC", toca **Más información** → **Ejecutar de todas formas**. |
| **Ubuntu, Linux Mint, Debian** | [aurora_0.3.0_amd64.deb](https://github.com/lial97/proyecto-aurora-app/releases/download/v0.3.0/aurora_0.3.0_amd64.deb) | Doble clic → **Instalar**. Aurora aparece en el menú de aplicaciones. |
| **Arch y cualquier otro Linux** | [Aurora-0.3.0-x86_64.AppImage](https://github.com/lial97/proyecto-aurora-app/releases/download/v0.3.0/Aurora-0.3.0-x86_64.AppImage) | Clic derecho → **Propiedades** → marca **Permitir ejecutar** → doble clic. La primera vez se agrega solo al menú de aplicaciones. |

Los archivos de Linux y Windows pesan más de 100 MB, por eso están en la página de descargas y no en esta carpeta.

## Para quien desarrolla

- Android: `./gradlew :androidApp:assembleDebug`
- Linux (.deb y AppImage, con Docker): `bash scripts/empaquetar-linux.sh`
- Windows (.exe, en un PC con Windows y Java 21): doble clic en `build-windows.bat`
