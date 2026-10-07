# Tema 8: Casete

El diseño "Casete" del principio, ajustado para que funcione en toda la app: mostaza, verde petróleo, café y crema, controles como teclas de grabadora y una cinta con carretes que giran mientras suena.

Toca reproducir, siguiente o la barra en cualquier pantalla: los carretes pasan la cinta de uno a otro según avanza la canción.

## Móvil

## Escritorio

## Qué se ajustó del diseño original

### Contraste

El verde petróleo original (#2E6E6A) no llegaba al contraste mínimo como texto sobre mostaza. Se oscureció a **#1D524F**, y el texto secundario pasó a café **#5A3D22**. Ambos cumplen 4,5:1.

### Portada siempre visible

En el original la portada no aparecía: era solo el dibujo del casete. Ahora la portada es la carátula, con su borde crema como si estuviera dentro del estuche, y la cinta con carretes va debajo.

### Teclas, no círculos

Los controles son teclas de grabadora: crema con borde café y sombra dura que se hunde 3 px al pulsar. El botón principal es la tecla verde, más ancha.

### Contador de cinta

El tiempo se muestra como el contador de una casetera (02:03) en una pastilla café, con barra gruesa de 8 px.

### Letra legible

Shrikhand es muy gruesa para leer muchas líneas. Los títulos usan Shrikhand; la letra usa DM Sans 800 sobre una hoja crema rayada, como la lista de canciones escrita a mano en una carátula, con la línea activa subrayada en mostaza.

### Menos cansado a la vista

Mostaza un poco más suave (#E9B949), grano de papel muy leve y una sola franja retro en una esquina, en lugar de llenar la pantalla de decoración.

## Tokens

### Colores

**Fondo** #E9B949Mostaza

**Lateral y barras** #E0AC3BMostaza más oscura

**Superficie / teclas** #F6E7C8Crema

**Texto, bordes y sombras** #3B2416Café

**Texto secundario** #5A3D22

**Énfasis / tecla principal** #1D524FVerde petróleo

**Decoración** #D9542BSolo franjas y detalles, nunca texto

### Tipografía y formas

**Títulos:** Shrikhand 400 (Google Fonts, OFL). h1 30 sp móvil / 44 escritorio; título del reproductor 26 / 36.

**Texto y letra:** DM Sans (ya está en la app); letra en 800, 21 sp móvil / 30 escritorio.

**Radios:** tarjetas 12, portadas 6–8, teclas 10, chips 8.

**Sombra dura:** 0 4 px 0 café en teclas; 3 px 3 px 0 café en portadas.

**Barra de progreso:** 8 px, relleno café.

### Animación

Carretes: una vuelta cada 3 s mientras suena; se detienen en pausa. La cinta del carrete izquierdo se achica y la del derecho crece con el progreso.

Teclas: bajan 3 px en 80 ms al pulsar.

Con "reducir movimiento", los carretes quedan quietos.

Canciones, letras y portadas de ejemplo.