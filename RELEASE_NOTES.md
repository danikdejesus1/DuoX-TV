# DuoX TV 3.0.1

- **Multivista más estable en directos largos:** cada pantalla usa solo la calidad que necesita (720p con 2 canales, 480p con 3-4) y solo la pantalla con sonido decodifica audio; el reintento de conexión ya no se rinde tras 20 intentos.
- **Panel de directos:** la burbuja junto a cada canal ahora es «+» para añadirlo a la multivista o «−» (roja) para quitarlo si ya está dentro, sin cerrar el panel.
- **Audio en la multivista:** el aviso de que arriba/abajo cambia el canal con sonido es ahora una burbuja pequeña sobre la barra, en vez de flechas de texto.
- **Actualizaciones:** el icono ↻ se enciende en verde si hay una versión nueva; la comprobación se hace una sola vez al abrir la app, no todo el rato.
- **Idioma:** el botón ahora es un icono, con un menú pequeño debajo en vez de la ventana grande.
- **Versión visible:** número pequeño en la esquina del Home.
- **Detalle de temporada:** un aviso breve y discreto abajo del Home según la época del año (el primero, para Halloween).

# DuoX TV 3.0.0

- **Nueva intro:** animación minimalista («Du», «o» y la X que estalla) con sonido propio, que corre en su propio hilo para no congelarse mientras el Home se carga detrás. Dura unos 3 s y se repite en cada arranque.
- **Panel de directos mientras ves un stream:** al entrar aparecen 5 s el panel y la barra; el panel se actualiza en segundo plano (quién está en directo y cuántos espectadores) y el canal seleccionado hace más zoom. Burbuja «+» y círculo del perfil más pequeños; el círculo lleva un aro completo del color de la plataforma.
- **Multivista:** los nombres de los canales solo se ven mientras usas la barra o el panel.
- **Búsqueda:** pantalla más tranquila, con «Ir», «Voz» y «VOD sync» juntos bajo el campo; el buscador de canales de VOD sync usa la misma pantalla completa.
- **Orden de la búsqueda:** primero los canales en directo (más espectadores primero) y después los que están offline por número de seguidores, así basta escribir el principio del nombre para encontrar al canal grande.
- **Continuar viendo en el perfil:** ahora también guarda las sesiones de VOD sync (cuáles VOD, dónde ibas y el desfase entre ellos) y las marca con un icono pequeño de sync en la miniatura; sale en el perfil de cada canal que participa. Mantén pulsado en cualquier tarjeta para «Olvidar este VOD».
- **VOD sync:** al bajar con el control, el foco llega a «Empezar» y no se salta a «Cancelar».
- **Calidad:** menú más pequeño y sin entradas repetidas; iconos de la barra más juntos.
- **Chat de Kick más limpio:** sin cabecera, caja de escribir, mensaje fijado ni insignias, y con letra más pequeña.
- **APK de 1,5 MB.**

# DuoX TV 2.2.6

- **Panel LIVE flotante rediseñado:** solo los canales en directo, sin cabecera; la burbuja «+» va fuera del panel con el mismo diseño de cristal y siempre junto al canal enfocado; el panel avanza por filas completas, así que ningún avatar se corta por arriba o por abajo.
- **Orden por espectadores:** el panel flotante y el panel LIVE del Home ordenan los canales de mayor a menor número de espectadores (Twitch y Kick juntos).
- **Círculo del perfil en la barra del reproductor:** la imagen rellena todo el círculo y lleva un aro fino del color de la plataforma (violeta Twitch, verde Kick).

# DuoX TV 2.2.5

- **Panel LIVE flotante mientras ves un directo:** desde la barra de controles, pulsa izquierda hasta el final y aparece el panel de canales en directo del Home, más transparente. Pulsa un canal para verlo, o ve a la derecha y pulsa la burbuja «+» para añadirlo a la multivista. Se oculta solo a los 5 s sin usarlo. Por eso la multivista ya no está en la barra de abajo.
- **Directos que se congelaban o dejaban la pantalla en negro (Fire TV):** se quitó el ajuste de velocidad del directo, que hacía que el audio se parara; ahora la app detecta el atasco y vuelve al directo con una dirección nueva (las de Kick caducan).
- **Retraso acumulado:** si el directo se queda más de 20 s por detrás, la app salta sola al directo.

# DuoX TV 2.2.2

- **Directos que se congelaban:** en el Fire TV el audio podía quedarse sin datos y la imagen se paraba aunque el reproductor dijera «reproduciendo». Ahora la app lo detecta (el retraso con el directo crece como el reloj) y reinicia sola la fuente en unos 10 s.
- **Barra del reproductor** algo más opaca para que los iconos se vean sobre cualquier fondo.
- Web y documentación actualizadas.

# DuoX TV 2.2.0

Twitch y Kick en Fire TV y Android TV, con el mando. Primera versión publicada en este repositorio.

## Novedades
- **Multivista** de hasta cuatro directos (Twitch y Kick mezclados), con audio elegible y calidad por pantalla.
- **VOD sincronizada:** hasta cuatro VOD alineados por el audio donde los streamers hablan juntos, con ajuste manual.
- **Buscador** con teclado propio y voz, resultados reales de Twitch y Kick con los directos primero.
- **Perfiles** con preview en vivo, VOD con fecha y «Continuar viendo».
- **Reproductor** con barra fina de iconos, favoritos y chat con QR para leerlo en el móvil.
- **APK de 1,2 MB:** se recorta el código y los recursos que no se usan y solo se incluyen español e inglés.
- Inicio de sesión de Kick compacto y «Conecta Twitch» con el estilo de la app.
- El botón ↻ de la app comprueba y descarga las nuevas versiones desde este repositorio.

## Instalar
Con Downloader, introduce: `https://github.com/danikdejesus1/DuoX-TV/releases/latest/download/DuoX.apk`

Si vienes de una versión anterior con otro nombre, instala esta una vez a mano: es una app distinta y tendrás que volver a conectar tus cuentas.

## Límites conocidos
- La alineación por voz necesita voces o sonidos compartidos entre los VOD; si no encuentra coincidencia clara, se ajusta a mano.
- Varias VOD a la vez limitan la calidad a 480p (2 pantallas) o 360p (3–4).
- Cliente independiente y experimental, no afiliado a Twitch, Kick ni Amazon. Probado en Fire OS 7.
