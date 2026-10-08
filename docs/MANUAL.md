# Manual de usuario de Aurora

Versión 0.3.1 · Android, Windows y Linux

¿Tienes prisa? Lee primero la [guía rápida](GUIA-RAPIDA.md).

## Contenido

1. [Qué es Aurora](#1-qué-es-aurora)
2. [Instalar](#2-instalar)
3. [La configuración inicial](#3-la-configuración-inicial)
4. [Cómo moverse por la app](#4-cómo-moverse-por-la-app)
5. [Inicio y Tu mezcla de hoy](#5-inicio-y-tu-mezcla-de-hoy)
6. [Biblioteca](#6-biblioteca)
7. [Buscar](#7-buscar)
8. [El reproductor](#8-el-reproductor)
9. [Letras tipo karaoke](#9-letras-tipo-karaoke)
10. [Videos](#10-videos)
11. [Listas](#11-listas)
12. [El menú de cada canción](#12-el-menú-de-cada-canción)
13. [Corregir y editar los datos de una canción](#13-corregir-y-editar-los-datos-de-una-canción)
14. [Los 12 temas](#14-los-12-temas)
15. [Ajustes, uno por uno](#15-ajustes-uno-por-uno)
16. [Solo en Android: notificación, bloqueo y widgets](#16-solo-en-android-notificación-bloqueo-y-widgets)
17. [Solo en el PC: atajos de teclado y clic derecho](#17-solo-en-el-pc-atajos-de-teclado-y-clic-derecho)
18. [Privacidad: qué se envía a internet](#18-privacidad-qué-se-envía-a-internet)
19. [Problemas comunes](#19-problemas-comunes)

---

## 1. Qué es Aurora

Aurora reproduce **la música y los videos que ya tienes guardados** en tu celular o tu computador. No es un servicio
de streaming como Spotify o YouTube: no hay cuenta, ni suscripción, ni anuncios, y funciona sin internet.

Lo que la hace distinta:

- **Se ve diferente con cada canción.** En el tema Aurora, las canciones lentas tiñen la app de azul y las movidas de
  rojo, y el color de los botones sale de la portada.
- **Letras sincronizadas** que se descargan solas y avanzan con la música.
- **Tu mezcla de hoy**, una lista nueva cada día hecha con lo que más escuchas.
- **Música y video juntos**: puedes mezclar canciones y videos musicales en la misma lista.

Es gratis y de código abierto (licencia GPL-3.0).

## 2. Instalar

Cada instalador trae todo lo necesario. No hay que instalar Java ni VLC aparte.
Los archivos están en la [página de descargas](https://github.com/lial97/proyecto-aurora-app/releases/latest).

| Sistema | Archivo | Cómo se instala |
|---|---|---|
| **Android** | `Aurora-0.3.1-android.apk` | Ábrelo en el celular y toca **Instalar**. Si lo pide, permite "instalar apps de fuentes desconocidas". |
| **Windows** | `Aurora-0.3.1.exe` | Doble clic → **Siguiente** → **Siguiente** → **Finalizar**. Si aparece "Windows protegió su PC", toca **Más información** → **Ejecutar de todas formas**. |
| **Ubuntu, Linux Mint, Debian** | `aurora_0.3.1_amd64.deb` | Doble clic → **Instalar**. |
| **Arch y otros Linux** | `Aurora-0.3.1-x86_64.AppImage` | Clic derecho → **Propiedades** → **Permitir ejecutar** → doble clic. La primera vez se agrega solo al menú. |

**Actualizar:** instala la versión nueva encima de la anterior. No pierdes tus listas, tus Me gusta ni tus ajustes.

## 3. La configuración inicial

La primera vez que abres Aurora te hace 4 preguntas. Puedes saltar cualquiera con **Saltar este paso** o
**Lo haré después**, y todo se cambia luego en **Ajustes**.

1. **¿Cómo te llamamos?** Tu nombre o un apodo. Se usa para saludarte en Inicio ("Buenas noches, Ana").
2. **¿Dónde está tu música?** Toca **Elegir carpeta** y escoge la carpeta con tus canciones. Aurora también busca en
   todas sus subcarpetas. Puedes añadir varias. Aurora te sugiere carpetas donde ya encontró música.
3. **¿Y tus videos musicales?** Igual, pero para videos. **Solo aparecen los videos de las carpetas que elijas**: tus
   fotos y videos personales no se tocan.
4. **Elige tu estilo.** Toca un tema para probarlo en vivo. Aquí también puedes activar **Reducir movimiento**.

Al final, Aurora lee tus archivos (portadas, letras, artistas y álbumes). Puedes entrar en cuanto diga
**¡Todo listo!**: el *tempo* de cada canción (qué tan rápida es) se sigue calculando en segundo plano.

Para repetir estas preguntas: **Ajustes** → **Acerca de** → **Repetir configuración inicial**.

## 4. Cómo moverse por la app

Hay 5 secciones:

| Sección | Para qué sirve |
|---|---|
| **Inicio** | Lo último que escuchaste, tu mezcla del día y lo añadido hace poco. |
| **Biblioteca** | Toda tu música, ordenada por canciones, álbumes, artistas, géneros y listas. |
| **Videos** | Tus videos musicales. |
| **Buscar** | Encuentra canciones, artistas o videos escribiendo. |
| **Ajustes** | Todo lo configurable. |

- **En el celular** están en la barra de abajo. El **mini reproductor**, encima de esa barra, muestra lo que suena;
  tócalo para abrir el reproductor completo. El botón **Atrás** del teléfono funciona en toda la app y Aurora
  recuerda hasta dónde bajaste en cada lista.
- **En el PC** están en la barra lateral izquierda, junto con **Tus listas** y el botón **+** para crear una. Abajo
  está siempre la **barra de reproducción** (ver [sección 8](#8-el-reproductor)). Arriba hay flechas de **Atrás** y
  **Adelante** como en un navegador.

## 5. Inicio y Tu mezcla de hoy

En **Inicio** encuentras:

- **Un saludo** con tu nombre.
- **Sigue escuchando**: lo que sonó hace poco, para retomarlo.
- **Tu mezcla de hoy** (ver abajo).
- **Tus mezclas** (en el PC): listas automáticas que Aurora arma sola:
  - **Me gusta**: las canciones que marcaste con el corazón.
  - **Añadidas hace poco**: las 30 últimas que llegaron a tu biblioteca.
  - **Descubrimientos**: 25 canciones al azar de tu música.
  - **Lo mejor de…**: tus 3 artistas con más canciones.
- **Añadidas hace poco.**

Inicio solo muestra música. Los videos están en su propia sección.

### Tu mezcla de hoy

Cada día Aurora arma **25 canciones** pensadas para ti:

- Se fija en los **géneros y artistas que más escuchas** (lo reciente cuenta más que lo de hace meses) y en tus
  **Me gusta**.
- Incluye también canciones que **no escuchas hace tiempo**, para que no sea siempre lo mismo.
- Deja fuera lo que ya sonó hoy y pone **máximo 3 canciones por artista**.
- El mismo día siempre es la misma mezcla; al día siguiente llega otra.
- Si tu biblioteca es nueva y aún no hay historial, usa tus Me gusta, lo añadido hace poco y un poco de azar.

Toca **Reproducir** para escucharla. Abajo aparece **¿Te gustó?**:

- **Sí, guardarla** → se guarda como una lista más en tus listas ("Guardada en tus listas").
- Si no la guardas, no pasa nada: **mañana tendrás otra**.

## 6. Biblioteca

Arriba hay pestañas (chips): **Canciones**, **Álbumes**, **Artistas**, **Géneros**, **Listas** y **Videos**.

- **Buscar en la biblioteca:** el campo de arriba filtra lo que estás viendo.
- **Ordenar:** toca **Ordenar** y elige un criterio. Tocar de nuevo el mismo invierte el orden.

  | Criterio | Orden |
  |---|---|
  | Título | A–Z / Z–A |
  | Artista | A–Z / Z–A |
  | Álbum | A–Z / Z–A |
  | Año | Recientes / Antiguos |
  | Añadidas | Recientes / Antiguas |
  | Más escuchadas | Más / Menos |
  | Duración | Cortas / Largas |

- **Ver como lista** o **Ver como cuadrícula** (portadas grandes).
- En un álbum, artista o lista tienes los botones **Reproducir** y **Aleatorio**.

**Formatos que reconoce:**

- **Audio:** MP3, FLAC, M4A, AAC, OGG, OGA, OPUS, WAV, WMA, AIFF y ALAC.
- **Video:** MP4, M4V, MKV, WEBM, MOV, AVI, 3GP y TS.

En Android, Aurora ignora a propósito los audios de WhatsApp y Telegram, las notas de voz, las grabaciones, los
tonos y las alarmas, para que no se mezclen con tu música.

## 7. Buscar

Escribe y aparecen canciones, artistas y videos que coinciden. No importan las tildes ni las mayúsculas
("cancion" encuentra "Canción"). En el PC puedes abrir la búsqueda desde cualquier parte con **Ctrl + K**.

## 8. El reproductor

### Controles básicos

| Botón | Qué hace |
|---|---|
| ▶ / ⏸ | Reproducir o pausar. |
| ⏮ / ⏭ | Canción anterior o siguiente. |
| **Aleatorio** | Mezcla el orden de la cola. |
| **Repetir** | Se alterna entre: repetir toda la cola → repetir esta canción → desactivado. |
| ♥ **Me gusta** | Guarda la canción en tu mezcla **Me gusta**. |
| **Barra de progreso** | Arrástrala para saltar a otro momento. Mientras suena, se mueve como una onda (salvo en el tema Póster o con Reducir movimiento). |
| **Volumen** | Solo en el PC, en la barra de abajo. |

### En el celular

Toca el mini reproductor para abrir el reproductor completo: portada grande, controles y tres pestañas:

- **Letra**: la letra de la canción (ver [sección 9](#9-letras-tipo-karaoke)).
- **Cola**: lo que suena después.
- **Info**: los datos de la canción.

En **⋯ (Más opciones)** hay un **ecualizador rápido** y el temporizador **Apagar en**.

### En el PC: "Reproduciendo"

Toca la portada de la barra de abajo para abrir **Reproduciendo**. En pantallas grandes se ve en tres columnas:

1. **La ficha** de la canción: portada, artista y álbum (puedes tocarlos para ir a ellos), botones **Me gusta**,
   **Lista**, **Video** y **Compartir**, y el recuadro **Esta canción** con su ánimo, tempo (BPM), tonalidad,
   formato y calidad, cuántas veces la escuchaste y cuándo fue la última vez (una canción cuenta como escuchada a
   los 30 segundos).
2. **La letra.**
3. **El panel derecho**, con tres pestañas:
   - **Cola:** de qué lista viene, **Limpiar** para vaciarla, arrastra el asa de cada canción para reordenar y la
     **×** para quitarla.
   - **Info:** número de pista, formato, tamaño, ubicación del archivo y fecha en que se añadió, con los botones
     **Abrir carpeta** y **Editar datos**.
   - **Artista:** sus canciones y álbumes en tu biblioteca y **Mezcla de este artista**.

En ventanas más pequeñas, el panel derecho se esconde detrás de un botón (**Mostrar panel** / **Ocultar panel**).

### Apagar en (temporizador)

Para dormirte escuchando música. Elige **15 min**, **30 min**, **1 h** o **Al terminar** (la canción actual).
La música se detiene sola. Toca **Cancelar** para quitarlo.

### Ecualizador rápido

Desde el reproductor puedes activarlo y pasar de un ajuste a otro con ◀ ▶. El ecualizador completo está en
**Ajustes** → **Sonido** (ver [sección 15](#sonido)).

### Al abrir la app

Si **Recordar dónde quedé** está activado (Ajustes → Reproducción), Aurora retoma la última canción en el segundo
exacto donde la dejaste.

## 9. Letras tipo karaoke

Cuando una canción empieza, Aurora busca su letra **sola** en LRCLIB, un servicio gratuito. Si la encuentra
sincronizada, la línea que suena se resalta y la letra avanza con la música.

- **Toca una línea** para saltar a ese momento de la canción.
- **Si la letra va adelantada o atrasada**, usa los botones de sincronía: cada toque la mueve **medio segundo**.
  Aurora recuerda el ajuste para esa canción.
- **A− / A+**: letra más pequeña o más grande.
- **Enfoque** (PC): esconde todo lo demás y deja solo la letra, ideal para cantar.
- Si mueves la letra con la rueda del ratón o el dedo, deja de seguir la canción. Toca **Volver a la línea actual**.
- **Buscar de nuevo**: si la letra no es la correcta o no se encontró.
- **Guardar .lrc**: guarda la letra como archivo junto a la canción, para usarla en otros reproductores.

Cada letra descargada se guarda en Aurora, así que la próxima vez **funciona sin internet**.

**¿Y si no hay letra?** Aparece **Sin letra**. Puedes poner tú mismo un archivo `.lrc` con el mismo nombre que la
canción, en la misma carpeta (por ejemplo `Mi canción.mp3` y `Mi canción.lrc`). Si es instrumental, Aurora lo dice.

## 10. Videos

En la sección **Videos** están los videos de las carpetas que elegiste. Para reconocerlos sin abrirlos:

- **En el PC**, pasa el cursor por encima de un video y verás varios momentos del video.
- **En el celular**, cada video muestra una tira de 4 fotogramas.

Al abrir un video:

- **Pantalla completa**: botón o tecla **F** (sal con **Esc**).
- **Solo audio**: apaga la imagen y deja el sonido. Útil para ahorrar batería.
- **Más videos**: el panel con los demás videos para pasar al siguiente.
- **Me gusta**, **Añadir a una lista** y **Compartir**.

### Canción | Video

En el reproductor hay un selector **Canción | Video**:

- **Video** muestra la imagen.
- **Canción** muestra portada y letra **mientras el video sigue sonando**.

Si una lista pasa de una canción a un video, el reproductor cambia solo a la vista de video, y al revés.

### Mini video

Si sales del reproductor con un video sonando, aparece un **video pequeño flotante** en una esquina, con pausa,
**ampliar** y **cerrar**. Al cerrarlo, el sonido sigue.

## 11. Listas

### Crear una lista

- Desde cualquier canción: **⋯** → **Añadir a una lista…** → **Nueva lista…**
- En el PC: botón **+** en la barra lateral, junto a **Tus listas**.
- En **Biblioteca** → **Listas** → **Nueva lista**.

### Qué puedes hacer

- **Añadir canciones y videos** en la misma lista, en el orden que quieras.
- **Mover arriba** / **Mover abajo** una canción, o quitarla.
- **Renombrar lista** o **Borrar lista**. Borrar una lista **no borra las canciones** de tu equipo.
- **Guardar como lista propia**: convierte una mezcla automática (Me gusta, Descubrimientos, Lo mejor de…) en una
  lista tuya que ya no cambia sola.

Tus listas y tus Me gusta se guardan en el equipo y siguen ahí al cerrar la app o actualizarla.

## 12. El menú de cada canción

Toca **⋯** en una canción (o **clic derecho** en el PC) para ver:

| Opción | Qué hace |
|---|---|
| **Reproducir** | La pone a sonar ya. |
| **Reproducir a continuación** | La pone justo después de la que suena. |
| **Añadir a la cola** | La pone al final de la cola. |
| **Añadir a Me gusta** / **Quitar de Me gusta** | Marca o desmarca el corazón. |
| **Añadir a una lista…** | La guarda en una de tus listas o en una nueva. |
| **Mover arriba** / **Mover abajo** | Solo dentro de una lista: cambia su posición. |
| **Ver detalles** | Toda la información: duración, álbum, género, tempo, tonalidad, formato, calidad, tamaño, ubicación, fecha en que se añadió, compositores y producción. |
| **Corregir datos…** | Busca el nombre correcto en internet (ver abajo). |
| **Editar datos…** | Cambia título, artista, álbum, año o género a mano. |
| **Mostrar en la carpeta** | Abre la carpeta donde está el archivo (PC). |

## 13. Corregir y editar los datos de una canción

Muchas canciones descargadas tienen nombres como `Abrázame Muy Fuerte - Juan Gabriel (320).mp3` o salen como
"Artista desconocido". Aurora intenta adivinar el título y el artista a partir del nombre del archivo, pero también
puedes arreglarlo:

### Corregir datos (automático)

1. **⋯** → **Corregir datos…**
2. Escribe el **título** y, si sabes, el **artista** (mejora la búsqueda). Toca **Buscar**.
3. Aurora busca en **MusicBrainz**, una enciclopedia libre de música, y te muestra resultados. Toca el correcto.
4. Marca **Usar la portada del disco** si quieres la portada oficial. Se verá en la app, los widgets y la
   notificación.
5. Toca **Aplicar**. Se actualizan título, artista, álbum, año y género.

Para revisar todas de una vez: **Ajustes** → **Biblioteca** → **Datos de las canciones** → **Revisar**. Aparece la
lista de **Canciones con datos dudosos**; toca una para corregirla.

### Editar datos (a mano)

**⋯** → **Editar datos…** y cambia título, artista, álbum, año o género. Si marcas **Guardar también en el
archivo**, el cambio queda dentro del archivo y otros reproductores también lo verán. Después, Aurora vuelve a
buscar la letra con los datos nuevos.

## 14. Los 12 temas

Cada tema cambia colores, tipos de letra, la forma de las portadas y una decoración de fondo.
Cámbialo en **Ajustes** → **Apariencia** → **Tema**.

| Tema | Estilo |
|---|---|
| **Aurora** | Cambia con cada canción (el original). |
| **Póster** | Gráfico y directo. |
| **Pétalo** | Suave, rosas y lilas. |
| **Seda** | Ciruela y dorado. |
| **Carbono** | Técnico y preciso. |
| **Estadio** | Deportivo, letras grandes. |
| **Bruma** | Tranquilo y legible. |
| **Casete** | Retro, como una cinta (con carretes que giran). |
| **Acuarela** | Lila, durazno y menta (con una gota que se mueve). |
| **Bosque** | Verde profundo y cobre (con curvas de nivel). |
| **Grafito** | Oscuro neutro y nítido. |
| **Rockola** | Cereza, menta y vinilo (con un disco que gira). |

Las animaciones solo se mueven mientras suena música, y se apagan con **Reducir movimiento**.

## 15. Ajustes, uno por uno

Arriba hay un buscador (**Buscar un ajuste**) que te lleva directo a la opción. Muchas opciones tienen un **?** con
una explicación más larga.

### Biblioteca

- **Carpetas de música** y **Carpetas de videos**: **Añadir carpeta**, quitarlas o **Volver a buscar**. En el PC
  también puedes **Escribir ruta**.
- **Vigilar cambios**: detecta al instante los archivos nuevos que copies a tus carpetas (en el PC).
- **Incluir videos**: muestra u oculta la sección Videos.
- **Formatos**: los tipos de archivo que Aurora reconoce.
- **Datos de las canciones** → **Revisar**: canciones con nombres dudosos para corregir.

### Apariencia

- **Perfil**: cambia tu nombre.
- **Tema**: los [12 temas](#14-los-12-temas).
- **Color según la canción** (solo en el tema Aurora): las canciones lentas tiñen la app de azul y las movidas de
  rojo; el color de énfasis sale de la portada.
- **Luces de fondo**: la decoración animada del tema.
- **Densidad**: **Cómoda** (más espacio) o **Compacta** (cabe más en pantalla).
- **Estilo de los widgets** (Android): **Igual que la app** o **Colores del fondo de pantalla** (Material You,
  Android 12 o más nuevo).

### Sonido

- **Ecualizador** de 10 bandas (31 Hz a 16 kHz): arrastra los puntos de la curva o usa uno de los **18 presets**:
  Plano, Clásica, Club, Baile, Graves, Graves y agudos, Agudos, Auriculares, Sala grande, En vivo, Fiesta, Pop,
  Reggae, Rock, Ska, Suave, Rock suave y Techno. Si mueves algo, pasa a **Personalizado**. **Restablecer** vuelve
  a Plano.
- **Evitar saturación** (recomendado): algunos presets suben tanto el volumen que el sonido se distorsiona. Esta
  opción baja el volumen general lo justo para que no pase.
- **Normalizar volumen**: todas las canciones suenan con un volumen parecido. Empieza a funcionar desde la siguiente
  canción.

En el PC, con el teclado sobre la curva: flechas ±0,5 dB, RePág/AvPág ±3 dB, Inicio/Fin al máximo o mínimo y 0 para
dejar la banda en cero.

### Reproducción

- **Motor**: muestra si el audio funciona bien (VLC en el PC, Media3 en Android).
- **Mostrar controles en la pantalla de bloqueo** (Android): si lo apagas, la notificación no muestra la canción con
  el teléfono bloqueado.
- **Fundido**: baja el final de una canción mientras sube el inicio de la siguiente. Elige la **Duración del
  fundido** (hasta 12 segundos).
- **Sin pausas**: pasa a la siguiente sin el pequeño silencio entre archivos. Ideal para álbumes en vivo o mezclados.
  No se usa si el fundido está activado.
- **Recordar dónde quedé**: al abrir la app, retoma la canción y el segundo exacto.
- **Al terminar la cola**: **Detener** o **Repetir** desde el principio.

### Letras

- **Descargar letras automáticamente** desde LRCLIB al empezar cada canción.
- **Guardar como archivo .lrc**: guarda la letra junto a la canción, con el mismo nombre. En Android quizá tengas que
  tocar **Dar permiso** y volver a elegir tu carpeta de música.
- **Preferir archivo .lrc**: si ya tienes una letra junto a la canción o dentro del archivo, se usa esa en vez de
  descargarla.
- **Letras guardadas**: cuántas hay y cuánto ocupan; **Borrar** las elimina.

### Accesibilidad

- **Tamaño del texto**: de 90 % a 150 %, para toda la app. Ves una vista previa al moverlo.
- **Letra más grande**: solo en la vista Reproduciendo.
- **Alto contraste**: textos y bordes más marcados.
- **Reducir movimiento**: detiene las luces del fondo, la "respiración" de la portada y los desplazamientos suaves de
  la letra. Útil si te mareas o para ahorrar batería.
- **Foco reforzado**: un contorno grueso para ver dónde estás al usar el teclado.
- **Anunciar cambios de canción**: para lectores de pantalla.
- **Atajos de teclado**: la lista completa (ver [sección 17](#17-solo-en-el-pc-atajos-de-teclado-y-clic-derecho)).

### Acerca de

Versión, licencias y **Repetir configuración inicial**.

## 16. Solo en Android: notificación, bloqueo y widgets

### Notificación y pantalla de bloqueo

Mientras suena música, Aurora sigue funcionando con la pantalla apagada o usando otras apps. En la notificación y en
la pantalla de bloqueo tienes portada, anterior, reproducir/pausa, siguiente, **Me gusta** y **Aleatorio**. Lo que
cambies ahí se refleja en la app y en los widgets.

### Widgets

Mantén presionada la pantalla de inicio → **Widgets** → **Aurora**. Hay 3:

| Widget | Tamaño | Qué muestra |
|---|---|---|
| **Aurora · Reproductor** | 4×2 | Portada, título, artista, barra de progreso, Me gusta, anterior, reproducir y siguiente. Agrándalo a **4×3** y aparecen Aleatorio y **A continuación** con las 2 siguientes canciones (tócalas para ponerlas). |
| **Aurora · Barra** | 4×1 | Portada, título, artista, anterior, reproducir y siguiente. |
| **Aurora · Portada** | 2×2 | La portada grande con título, artista y botón de reproducir. |

- Los botones funcionan **sin abrir la app**. Tocar la portada o el título abre el reproductor.
- Siguen el tema de la app (colores, forma de la portada y tipo de letra), o los colores de tu fondo de pantalla si
  lo eliges en **Ajustes** → **Apariencia** → **Estilo de los widgets**.
- Sin nada sonando muestran la última canción con el botón **Continuar**. Si nunca sonó nada, dicen
  "Elige música en Aurora".

## 17. Solo en el PC: atajos de teclado y clic derecho

| Tecla | Acción |
|---|---|
| **Espacio** | Reproducir o pausar |
| **→ / ←** | Avanzar o retroceder 5 segundos |
| **Ctrl + → / Ctrl + ←** | Siguiente o anterior canción |
| **L** | Abrir la letra |
| **V** | Abrir el video |
| **F** | Pantalla completa (sal con **Esc**) |
| **Ctrl + ,** | Ajustes |
| **Ctrl + K** | Buscar |

Los atajos no se activan mientras escribes en un campo de texto.

**Clic derecho** sobre cualquier canción o video (en Inicio, Biblioteca, listas, búsqueda, la cola o la barra de
abajo) abre el [menú de la canción](#12-el-menú-de-cada-canción) junto al cursor. **Esc** lo cierra.

## 18. Privacidad: qué se envía a internet

Aurora no tiene cuenta, ni anuncios, ni recoge datos tuyos. Solo usa internet para dos cosas, y ambas se pueden
evitar:

| Para qué | A dónde | Qué se envía | Cómo apagarlo |
|---|---|---|---|
| Letras | LRCLIB | Título, artista, álbum y duración de la canción | Ajustes → Letras → **Descargar letras automáticamente** |
| Corregir datos y portadas | MusicBrainz y Cover Art Archive | Solo el título y el artista que escribes | Solo se usa cuando tú lo pides |

Tus archivos nunca se suben a ninguna parte.

## 19. Problemas comunes

**No aparece mi música.**
Ve a **Ajustes** → **Biblioteca** y revisa que tu carpeta esté en **Carpetas de música**. Si acabas de copiar
canciones, toca **Volver a buscar**. Revisa también que el formato esté en la lista de la
[sección 6](#6-biblioteca).

**Aparecen audios de WhatsApp o grabaciones.**
No deberían (Aurora los ignora en Android). Si pasa, quita esa carpeta de **Carpetas de música** y elige una más
específica.

**Mis videos personales aparecen en la app.**
Solo aparecen los videos de **Carpetas de videos**. Quita de ahí la carpeta de la cámara.

**La letra no coincide o va desfasada.**
Usa los botones de sincronía (medio segundo cada toque) o **Buscar de nuevo**. Si la canción tiene un nombre raro,
corrige primero sus datos (sección 13); la letra se busca con esos datos.

**El ecualizador suena distorsionado.**
Activa **Evitar saturación** en **Ajustes** → **Sonido**.

**Unas canciones suenan más fuerte que otras.**
Activa **Normalizar volumen** en **Ajustes** → **Sonido**.

**Windows dice "Windows protegió su PC" al instalar.**
Es normal en programas que no son de una gran empresa. Toca **Más información** → **Ejecutar de todas formas**.

**La app gasta mucha batería o se siente lenta.**
Activa **Reducir movimiento** en **Ajustes** → **Accesibilidad** y apaga **Luces de fondo** en **Apariencia**.

**Android, Moto G84: la app se traba con los widgets.**
Es un problema conocido en ese modelo. Quita los widgets de Aurora de la pantalla de inicio y avísanos.

**Encontré un error.**
Cuéntalo en la [página del proyecto](https://github.com/lial97/proyecto-aurora-app/issues), diciendo tu celular o
sistema y los pasos para que pase.
