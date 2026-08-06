# Radio Studio

Estudio profesional de transmisión de radio en vivo para Android, con motor de audio nativo en C++ (Oboe/AAudio), mezclador multicanal (micrófono + música), ecualizador, soundboard de efectos y streaming en vivo a servidores Icecast.

## Requisitos

- **Android Studio** (última versión estable, ej. "Otter" o posterior) — recomendado para la mayoría de usuarios.
- **JDK 17** (Android Studio lo incluye).
- Para compilar por línea de comandos: **Gradle 9.1.0 o superior** instalado en tu sistema (solo hace falta una vez, ver más abajo).
- Conexión a internet la primera vez que abras el proyecto (para descargar dependencias).

No necesitas instalar el NDK manualmente: el proyecto fija la versión (`27.0.12077973`) en `app/build.gradle.kts` y Android Studio / Gradle la descargan automáticamente.

## Primer uso: generar el wrapper de Gradle

Este repositorio **no incluye el binario `gradle-wrapper.jar`** (es un archivo binario que no se puede versionar de forma fiable a mano). Es un paso de un solo minuto:

### Opción A — Desde Android Studio (recomendado)
1. Abre el proyecto en Android Studio (`File > Open` → selecciona la carpeta del repo).
2. Android Studio detecta que falta el wrapper y te ofrece generarlo automáticamente. Acepta, o si no aparece el aviso, abre la terminal integrada (`View > Tool Windows > Terminal`) y ejecuta:
   ```
   gradle wrapper --gradle-version 9.1.0
   ```
   (Android Studio trae Gradle embebido, así que el comando `gradle` funciona ahí aunque no lo tengas instalado en tu sistema).

### Opción B — Línea de comandos
Si tienes Gradle instalado (por ejemplo vía [SDKMAN!](https://sdkman.io/): `sdk install gradle 9.1.0`):
```bash
gradle wrapper --gradle-version 9.1.0
```
Esto genera `gradlew`, `gradlew.bat` y `gradle/wrapper/gradle-wrapper.properties` + `.jar`. A partir de ahí, cualquiera puede clonar el repo y compilar con `./gradlew ...` sin instalar Gradle.

**Después de generarlo una vez, haz commit de esos archivos** (`gradlew`, `gradlew.bat`, `gradle/wrapper/`) para que el resto de usuarios ya no necesiten este paso.

## Compilar y ejecutar

```bash
# Compilar el APK de debug (se firma automáticamente con un keystore de debug
# que el propio build genera si no existe — no requiere configuración manual)
./gradlew assembleDebug

# Instalar en un dispositivo/emulador conectado
./gradlew installDebug

# Ejecutar las pruebas unitarias
./gradlew test

# Ejecutar las pruebas instrumentadas (requiere dispositivo/emulador)
./gradlew connectedAndroidTest
```

O simplemente pulsa "Run" (▶) en Android Studio con un dispositivo/emulador conectado.

## Publicar en modo release (firma real)

El build `release` cae automáticamente al keystore de debug si no configuras variables de entorno (para que `./gradlew assembleRelease` funcione sin fricción en pruebas locales), pero **eso no sirve para publicar en Google Play**. Para una firma real:

```bash
export KEYSTORE_PATH=/ruta/a/tu/upload-keystore.jks
export STORE_PASSWORD=tu_store_password
export KEY_PASSWORD=tu_key_password
./gradlew bundleRelease   # genera el .aab para subir a Play Console
```

El alias de la clave debe llamarse `upload` (o edita `signingConfigs.release` en `app/build.gradle.kts` si usas otro).

## Configurar el streaming a Icecast

Dentro de la app, en la pestaña de configuración del stream, indica:
- **Servidor** y **puerto** de tu Icecast (ej. `icecast.tuempresa.com`, `8000`)
- **Punto de montaje** (ej. `/stream.wav`)
- **Contraseña de origen** (source password) configurada en tu `icecast.xml`

### Nota importante sobre el códec de audio
El motor nativo envía el audio como **PCM sin comprimir dentro de un contenedor WAV en streaming** (válido y reproducible de inmediato en cualquier reproductor: VLC, ffmpeg, mpv, navegadores), no como MP3/AAC comprimido. Esto es intencional y honesto: es la forma más simple de garantizar un stream **correcto y reproducible de verdad**, a costa de mayor consumo de datos (~1.5 Mbps en estéreo 48kHz/16-bit) frente a un MP3 a 128 kbps.

Si necesitas menor consumo de ancho de banda, la mejora recomendada es sustituir `IcecastStreamer::workerLoop()` (en `app/src/main/cpp/IcecastStreamer.cpp`) por una codificación real, por ejemplo:
- **AAC** usando `android.media.MediaCodec` (API de Android, sin dependencias externas), o
- **MP3/Opus** integrando una librería de codificación nativa (ej. `libopus` vía Maven, o vendorizando `shine`/`LAME`).

## Estructura del proyecto

```
app/src/main/java/com/example/          Código Kotlin (UI Compose + ViewModel)
app/src/main/cpp/                       Motor de audio nativo en C++ (Oboe + streaming Icecast)
app/src/main/res/                       Recursos Android (iconos, strings, temas)
```

## Solución de problemas

- **"gradle-wrapper.jar not found" / "gradlew: command not found"**: no completaste el paso "Primer uso" de arriba.
- **Falla la firma del build debug**: borra `debug.keystore` en la raíz del proyecto y vuelve a compilar; se regenera solo.
- **Falla la build nativa (CMake/NDK)**: confirma que tienes conexión a internet la primera vez (Gradle descarga el NDK `27.0.12077973` automáticamente) y que no hay un firewall bloqueando `sdkmanager`.
- **Firebase**: el proyecto funciona sin `google-services.json` (el plugin está configurado en modo `WARN`, no falla el build). Si quieres usar Firebase de verdad, añade tu propio `google-services.json` en `app/`.

## Licencia

Ver [LICENSE](LICENSE).
