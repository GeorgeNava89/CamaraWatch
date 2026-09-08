package com.george.camarawatch.shared

object Protocol {
    const val APPLICATION_ID = "com.george.camarawatch"

    const val COMMAND_PATH = "/camara/command"
    const val STATUS_PATH = "/camara/status"
    const val PREVIEW_CHANNEL_PATH = "/camara/preview"
    const val PREVIEW_INFO_PATH = "/camara/preview_info"

    const val TAKE_PHOTO = "TAKE_PHOTO"
    const val START_RECORD = "START_RECORD"
    const val STOP_RECORD = "STOP_RECORD"

    const val PREVIEW_PORT = 18765
    const val JPEG_QUALITY = 55
    const val PREVIEW_MAX_EDGE = 360
    const val PREVIEW_MIN_INTERVAL_MS = 90L
    const val TCP_CONNECT_TIMEOUT_MS = 2_500
    const val TCP_SO_TIMEOUT_MS = 4_000

    const val CAPABILITY_PHONE = "camara_watch_phone"
    const val CAPABILITY_WEAR = "camara_watch_wear"

    const val PICTURES_FOLDER = "CamaraWatch"
    const val MOVIES_FOLDER = "CamaraWatch"

    const val KEY_IP = "ip"
    const val KEY_PORT = "port"
    const val KEY_STATUS = "status"
    const val KEY_RECORDING = "recording"
    const val KEY_TS = "ts"
}

enum class CameraCommand {
    TAKE_PHOTO,
    START_RECORD,
    STOP_RECORD;

    companion object {
        fun fromWire(value: String?): CameraCommand? = entries.firstOrNull { it.name == value }
    }
}

enum class PhoneStatus(val message: String) {
    STARTING("Iniciando cámara trasera…"),
    READY("Listo. Cámara trasera activa."),
    WAITING_WATCH("Cámara lista. Esperando el reloj…"),
    WATCH_CONNECTED("Reloj conectado."),
    PREVIEW_WIFI("Vista previa por Wi‑Fi."),
    PREVIEW_WEAR("Vista previa por Wear OS."),
    TAKING_PHOTO("Tomando foto…"),
    PHOTO_SAVED("Foto guardada en Pictures/CamaraWatch."),
    RECORDING("Grabando video…"),
    VIDEO_SAVED("Video guardado en Movies/CamaraWatch."),
    NO_CAMERA_PERMISSION("Falta permiso de cámara."),
    CAMERA_UNAVAILABLE("Cámara no disponible."),
    RECORD_ERROR("Error al grabar."),
    PHOTO_ERROR("Error al tomar la foto."),
    STOPPED("Cámara detenida."),
}
