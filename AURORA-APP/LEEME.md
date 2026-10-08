# Descargar Aurora

Aurora es un reproductor de música y video. **No hace falta instalar nada más**: cada instalador ya trae
todo lo necesario (Java y VLC van adentro).

Todos los archivos están en la página de descargas:
**https://github.com/lial97/proyecto-aurora-app/releases** (versión 0.3.1)

| Sistema | Archivo | Cómo se instala |
|---|---|---|
| **Android** | [Aurora-0.3.1-android.apk](Aurora-0.3.1-android.apk) (está en esta carpeta) | Ábrelo en el celular y toca **Instalar**. Si el celular lo pide, permite "instalar apps de fuentes desconocidas". |
| **Windows** | [Aurora-0.3.1.exe](https://github.com/lial97/proyecto-aurora-app/releases/download/v0.3.1/Aurora-0.3.1.exe) | Doble clic → **Siguiente** → **Siguiente** → **Finalizar**. Si aparece "Windows protegió su PC", toca **Más información** → **Ejecutar de todas formas**. |
| **Ubuntu, Linux Mint, Debian** | [aurora_0.3.1_amd64.deb](https://github.com/lial97/proyecto-aurora-app/releases/download/v0.3.1/aurora_0.3.1_amd64.deb) | Doble clic → **Instalar**. Aurora aparece en el menú de aplicaciones. |
| **Arch y cualquier otro Linux** | [Aurora-0.3.1-x86_64.AppImage](https://github.com/lial97/proyecto-aurora-app/releases/download/v0.3.1/Aurora-0.3.1-x86_64.AppImage) | Clic derecho → **Propiedades** → marca **Permitir ejecutar** → doble clic. La primera vez se agrega solo al menú de aplicaciones. |

Los archivos de Linux y Windows pesan más de 100 MB, por eso en GitHub están en la página de descargas y no en
esta carpeta. Al compilarlos en tu equipo (ver abajo) sí quedan aquí, en `AURORA-APP/`, junto al APK.

## Para quien desarrolla

Todos los instaladores quedan en esta carpeta, `AURORA-APP/`:

- Android: `./gradlew :androidApp:assembleRelease` y copiar
  `androidApp/build/outputs/apk/release/androidApp-release.apk` aquí como `Aurora-<versión>-android.apk`.
- Linux (.deb y AppImage, con Docker): `bash scripts/empaquetar-linux.sh`.
- Windows (.exe, en un PC con Windows y Java 21): doble clic en `build-windows.bat`.

Git solo guarda el APK y este archivo: el .deb, el AppImage y el .exe están en `.gitignore` por su tamaño
y se suben a la Release con `gh release upload v<versión> AURORA-APP/<archivo> --clobber`.
