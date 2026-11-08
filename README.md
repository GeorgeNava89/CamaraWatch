# Cámara Watch

App personal en español latino para controlar la cámara del **RedMagic 11 Pro** desde el **OnePlus Watch 3** (Wear OS, pantalla redonda).

- Teléfono: CameraX, foto y video con la cámara trasera o frontal, zoom, flash, linterna, exposición y temporizador. Servicio en primer plano y estados en español.
- Reloj: vista previa a pantalla completa y un disparador chico. Deslizar a la derecha pasa a video; en video, deslizar a la izquierda vuelve a foto. Deslizar hacia arriba abre flash, temporizador, zoom, exposición y cambio de cámara.
- Módulos: `:mobile`, `:wear`, `:shared`
- `applicationId`: `com.george.camarawatch`
- Vista previa: JPEG por TCP en Wi‑Fi (preferido) y respaldo por Wearable `ChannelClient`. Los comandos van por el Data Layer de Wear OS (Bluetooth), así que foto, video, zoom, flash y cambio de cámara no dependen del Wi‑Fi.
- Medios: `Pictures/CamaraWatch` y `Movies/CamaraWatch`

## Descargas

La versión **1.2.0** se genera al compilar (misma firma en teléfono y reloj):

- `mobile/build/outputs/apk/release/mobile-release.apk`
- `wear/build/outputs/apk/release/wear-release.apk`

La 1.0.0 anterior solo tenía Foto y Grabar en la cámara trasera:

- [APK del teléfono (v1.0.0)](https://github.com/GeorgeNava89/CamaraWatch/releases/download/v1.0.0/CamaraWatch-phone-release.apk)
- [APK del reloj (v1.0.0)](https://github.com/GeorgeNava89/CamaraWatch/releases/download/v1.0.0/CamaraWatch-watch-release.apk)

## Instalación

### 1. Teléfono (RedMagic 11 Pro)

1. En el teléfono, activen **orígenes desconocidos** / instalar apps desconocidas para el navegador o el administrador de archivos.
2. Descarguen el APK del teléfono e instálenlo.
3. Abran **Cámara Watch** y acepten cámara, micrófono y notificaciones.
4. Dejen la app abierta (el servicio en primer plano mantiene la cámara lista). El estado aparece en español en pantalla y en la notificación: cámara frontal o trasera, zoom y flash o linterna.

### 2. Emparejar el OnePlus Watch 3

1. Instalen **Wear OS** / la app de OnePlus en el teléfono y emparejen el Watch 3 por Bluetooth.
2. Confirmen que el reloj aparece como conectado.
3. Para la vista previa más fluida, teléfono y reloj deben estar en la **misma red Wi‑Fi**. Si no hay Wi‑Fi, la app usa el canal Wear OS (más lento). Los botones funcionan igual por Bluetooth.

### 3. Reloj (sideload)

El APK del reloj no se instala solo desde Play. Háganlo por depuración inalámbrica, con el reloj en **Modo inteligente**:

1. En el Watch 3: **Ajustes → Sistema → Modo de batería** (o el ajuste equivalente) y dejen **Modo inteligente**, no Modo ahorro de energía.
2. **Ajustes → Acerca del reloj** y toquen el número de compilación hasta activar opciones de desarrollador.
3. Activen **Depuración ADB** y **Depuración inalámbrica**.
4. Anoten IP y puerto (o el código de emparejamiento).
5. Desde una computadora o desde el teléfono con **Bugjaeger** / **Remote ADB**:
   - `adb pair <IP>:<puerto> <código>` (si pide emparejamiento)
   - `adb connect <IP>:<puerto>`
   - `adb install -r CamaraWatch-watch-release.apk`
6. En el reloj abran **Cámara Watch**. Deberían ver la vista previa y un disparador abajo.

## Modo del reloj

Cámara Watch **solo corre en Modo inteligente** (Wear OS, chip Snapdragon W5). Ahí se instalan apps de terceros y funciona el Data Layer.

### Modo ahorro de energía del OnePlus Watch 3 (Power Saver)

Ese modo **no es compatible** y esta app no lo va a habilitar. El Watch 3 tiene dos sistemas:

- **Modo inteligente**: Wear OS. Acepta apps, esferas y complicaciones de terceros. Cámara Watch corre aquí. La batería de ese modo suele durar unos pocos días, no las semanas del otro sistema.
- **Modo ahorro de energía / Power Saver**: el reloj pasa al chip BES2800 (RTOS). Se apagan las apps de terceros, las esferas de terceros y las complicaciones de terceros. Tampoco hay Wi‑Fi. Cámara Watch no abre ni se puede usar ahí. No es un ajuste de esta app: Wear OS deja de estar activo.

Para usarla, salgan de Modo ahorro de energía y vuelvan a **Modo inteligente**.

### Ahorro de batería de Wear OS

El **ahorro de batería** del sistema Wear OS (el ajuste de Google, distinto al Power Saver de OnePlus) suele dejar el reloj en la hora y apaga Wi‑Fi y Bluetooth. Sin Bluetooth no llegan los comandos. Desactívenlo para usar el reloj como control.

Con la pantalla apagada, en ambiente o con la app en segundo plano, Cámara Watch deja de pedir la vista previa y el teléfono deja de armar JPEG si nadie está conectado. Eso evita transmitir frames al vacío. No cambia la autonomía del Modo inteligente ni la acerca a la del modo RTOS.

## Uso

1. Teléfono: **Cámara Watch** en primer plano o con la notificación activa. El reloj, en Modo inteligente.
2. En modo foto, el disparador toma la imagen (`Pictures/CamaraWatch`).
3. Deslicen a la derecha para video. El mismo disparador graba; tóquenlo otra vez para parar (`Movies/CamaraWatch`). Deslicen a la izquierda para volver a foto. Mientras graba, ese desliz no cambia de modo.
4. Deslicen hacia arriba para las opciones: flash (apagado, encendido, automático, linterna), temporizador (apagado, 3 s, 10 s), zoom, exposición y cambio de cámara. Deslicen hacia abajo o toquen fuera para cerrar. Con el panel abierto no cambia el modo foto/video.
5. El temporizador cuenta en el teléfono y se ve en el reloj. Se cancela con otro toque al disparador. Si la cámara no tiene flash, ese control no aparece. No se puede cambiar de cámara mientras graba.
6. El teléfono muestra el estado en español: cámara frontal o trasera, zoom, flash o linterna, temporizador, y si la vista previa va por Wi‑Fi, por Wear OS o está pausada.

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
| `:shared` | Protocolo, comandos, estado de cámara, codec JPEG (largo + bytes) |
| `:mobile` | CameraX frontal y trasera, `CameraForegroundService`, servidor TCP `:18765`, listener Wear |
| `:wear` | UI redonda con gestos, cliente TCP, fallback `ChannelClient`, envío de comandos |

Rutas Wear:

- `/camara/command` — `TAKE_PHOTO`, `START_RECORD`, `STOP_RECORD`, `SWITCH_CAMERA`, `ZOOM_IN`, `ZOOM_OUT`, `SET_ZOOM|<ratio>`, `SET_FLASH|OFF|ON|AUTO|TORCH`, `EXPOSURE_UP`, `EXPOSURE_DOWN`, `SET_TIMER|0|3|10`, `ARM|PHOTO|segundos`, `ARM|VIDEO|segundos`, `CANCEL_TIMER`
- `/camara/status` — estado en español y controles (cámara, zoom, flash, exposición)
- `/camara/preview_info` — IP, puerto, estado y los mismos controles
- `/camara/preview` — canal de frames JPEG

Si el reloj no está mirando la vista previa, el teléfono no codifica frames. Foto, video y el resto de controles siguen por Bluetooth.
