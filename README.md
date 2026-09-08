# Cámara Watch

App personal en español latino para controlar la **cámara trasera** del **RedMagic 11 Pro** desde el **OnePlus Watch 3** (Wear OS, pantalla redonda).

- Teléfono: CameraX (solo cámara trasera), foto + video, servicio en primer plano y estados en español.
- Reloj: vista previa en vivo, **Foto**, **Grabar** / **Parar**.
- Módulos: `:mobile`, `:wear`, `:shared`
- `applicationId`: `com.george.camarawatch`
- Vista previa: JPEG por TCP en Wi‑Fi (preferido) y respaldo por Wearable `ChannelClient`.
- Comandos Data Layer: `TAKE_PHOTO`, `START_RECORD`, `STOP_RECORD`
- Medios: `Pictures/CamaraWatch` y `Movies/CamaraWatch`

## Descargas (v1.0.0)

- [APK del teléfono](https://github.com/GeorgeNava89/CamaraWatch/releases/download/v1.0.0/CamaraWatch-phone-release.apk)
- [APK del reloj](https://github.com/GeorgeNava89/CamaraWatch/releases/download/v1.0.0/CamaraWatch-watch-release.apk)

## Instalación

### 1. Teléfono (RedMagic 11 Pro)

1. En el teléfono, activen **orígenes desconocidos** / instalar apps desconocidas para el navegador o el administrador de archivos.
2. Descarguen `CamaraWatch-phone-release.apk` e instálenlo.
3. Abran **Cámara Watch** y acepten cámara, micrófono y notificaciones.
4. Dejen la app abierta (el servicio en primer plano mantiene la cámara trasera lista). El estado aparece en español en pantalla y en la notificación.

### 2. Emparejar el OnePlus Watch 3

1. Instalen **Wear OS** / la app de OnePlus en el teléfono y emparejen el Watch 3 por Bluetooth.
2. Confirmen que el reloj aparece como conectado.
3. Para la vista previa más fluida, teléfono y reloj deben estar en la **misma red Wi‑Fi**. Si no hay Wi‑Fi, la app usa el canal Wear OS (más lento).

### 3. Reloj (sideload)

El APK del reloj no se instala solo desde Play. Háganlo por depuración inalámbrica:

1. En el Watch 3: **Ajustes → Acerca del reloj** y toquen el número de compilación hasta activar opciones de desarrollador.
2. Activen **Depuración ADB** y **Depuración inalámbrica**.
3. Anoten IP y puerto (o el código de emparejamiento).
4. Desde una computadora o desde el teléfono con **Bugjaeger** / **Remote ADB**:
   - `adb pair <IP>:<puerto> <código>` (si pide emparejamiento)
   - `adb connect <IP>:<puerto>`
   - `adb install -r CamaraWatch-watch-release.apk`
5. En el reloj abran **Cámara Watch**. Deberían ver la vista previa, **Foto** y **Grabar**.

## Uso

1. Teléfono: **Cámara Watch** en primer plano o con la notificación activa.
2. Reloj: **Foto** toma una imagen (`Pictures/CamaraWatch`).
3. Reloj: **Grabar** inicia video; **Parar** lo detiene (`Movies/CamaraWatch`).
4. El teléfono muestra el estado: listo, reloj conectado, Wi‑Fi, grabando, foto/video guardado.

## Compilar

Requisitos: JDK 17+, Android SDK 35.

```bash
export ANDROID_HOME=/ruta/al/sdk
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :mobile:assembleRelease :wear:assembleRelease
```

APKs firmados:

- `mobile/build/outputs/apk/release/mobile-release.apk`
- `wear/build/outputs/apk/release/wear-release.apk`

La firma de release está en `keystore/camarawatch.jks` (app personal, sideload). Teléfono y reloj usan el **mismo certificado**, necesario para el Data Layer de Wear OS.

## Arquitectura

| Pieza | Rol |
|---|---|
| `:shared` | Protocolo, comandos, codec JPEG (largo + bytes) |
| `:mobile` | CameraX trasera, `CameraForegroundService`, servidor TCP `:18765`, listener Wear |
| `:wear` | UI redonda, cliente TCP, fallback `ChannelClient`, envío de comandos |

Rutas Wear:

- `/camara/command` — `TAKE_PHOTO` / `START_RECORD` / `STOP_RECORD`
- `/camara/status` — estado en español
- `/camara/preview_info` — IP, puerto y estado
- `/camara/preview` — canal de frames JPEG
