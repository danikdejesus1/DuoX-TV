# Privacidad

DuoX TV conecta el dispositivo directamente con Twitch, Kick y proveedores de imágenes y emotes (7TV, BetterTTV y FrankerFaceZ). No utiliza un servidor propio del autor ni incluye analítica propia.

Los tokens de Twitch se guardan mediante el almacén cifrado de la aplicación. Kick conecta mediante QR y conserva localmente el token de sesión recibido al autorizar desde el móvil. Los seguidos, favoritos, metadatos de canales y últimos canales vistos se guardan en el dispositivo. Continuar viendo conserva localmente hasta 20 VOD con identificador, título, miniatura, canal, fecha, duración y punto de reproducción. No guarda enlaces de reproducción ni envía el historial al autor. Puedes quitar cada VOD desde esa fila; el progreso no se sincroniza entre dispositivos. Las copias de seguridad automáticas del APK están desactivadas.

Las plataformas reciben las solicitudes necesarias para reproducir contenido y mostrar chats. El chat de Kick obtiene mensajes y emotes directamente de sus servicios; se aplican las políticas de la plataforma. Las credenciales, sesiones, almacenes de firma y capturas de pruebas del autor no forman parte del proyecto público.

Puedes desconectar cada plataforma desde Cuentas. Borrar los datos de DuoX TV elimina su almacenamiento local. No compartas contraseñas o tokens en incidencias de GitHub.

Al abrir Actualización, DuoX TV consulta la API pública de GitHub. Si decides descargar una versión, obtiene el APK de GitHub. No envía credenciales de Twitch/Kick ni historial de visualización en estas solicitudes; GitHub recibe los datos habituales de conexión, incluida la dirección IP.

Al buscar canales, DuoX TV envía el texto escrito a la API de Twitch (con la cuenta conectada) y al buscador público de Kick. El QR del chat solo codifica la dirección pública del chat del canal.
