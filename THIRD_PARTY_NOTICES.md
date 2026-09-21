# Dependencias y servicios

Código original de DuoX TV: DanikDeJesus. Las marcas Twitch, Kick y Amazon pertenecen a sus respectivos titulares.

Dependencias:

- AndroidX Media3 1.5.1 — Android Open Source Project, Apache-2.0. https://github.com/androidx/media/blob/release/LICENSE
- OkHttp 4.12.0 — Square y colaboradores, Apache-2.0. https://github.com/square/okhttp/blob/parent-4.12.0/LICENSE.txt
- Okio y Kotlin — dependencias transitivas de OkHttp, Apache-2.0. https://github.com/square/okio y https://github.com/JetBrains/kotlin
- ZXing Core 3.5.3 — ZXing authors, Apache-2.0. https://github.com/zxing/zxing/blob/zxing-3.5.3/LICENSE
- JUnit 4.13.2 — solo pruebas, EPL-1.0. https://github.com/junit-team/junit4/blob/r4.13.2/LICENSE-junit.txt

Los emotes y avatares se descargan de los servicios correspondientes durante el uso; no se distribuye un catálogo de contenido de terceros. El chat Kick utiliza https://kick.com/popout/USUARIO/chat .

La implementación Java de reproducción de Twitch es propia; se consultaron los parámetros públicos empleados por Streamlink (BSD-2-Clause). No se incluye código de S0undTV ni se presenta DuoX TV como una versión oficial de ese proyecto.

## Intro sound

The opening sound of the released APK is licensed to the author under an Envato Elements subscription and is embedded in the app only. It is **not** included in this source repository: `app/src/main/res/raw/duox_signature.m4a` here is a silent placeholder, so the project builds but the intro is silent unless you supply your own sound with that file name.
