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
    const val SWITCH_CAMERA = "SWITCH_CAMERA"
    const val ZOOM_IN = "ZOOM_IN"
    const val ZOOM_OUT = "ZOOM_OUT"
    const val SET_ZOOM = "SET_ZOOM"
    const val SET_FLASH = "SET_FLASH"
    const val EXPOSURE_UP = "EXPOSURE_UP"
    const val EXPOSURE_DOWN = "EXPOSURE_DOWN"
    const val SET_TIMER = "SET_TIMER"
    const val ARM = "ARM"
    const val CANCEL_TIMER = "CANCEL_TIMER"

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
    const val KEY_FACING = "facing"
    const val KEY_ZOOM = "zoom"
    const val KEY_ZOOM_MIN = "zoom_min"
    const val KEY_ZOOM_MAX = "zoom_max"
    const val KEY_FLASH = "flash"
    const val KEY_FLASH_SUPPORTED = "flash_ok"
    const val KEY_TORCH_SUPPORTED = "torch_ok"
    const val KEY_EXPOSURE = "exposure"
    const val KEY_EXPOSURE_MIN = "exp_min"
    const val KEY_EXPOSURE_MAX = "exp_max"
    const val KEY_EXPOSURE_STEP = "exp_step"
    const val KEY_CAN_SWITCH = "can_switch"
    const val KEY_TIMER = "timer"
    const val KEY_COUNTDOWN = "countdown"
    const val KEY_COUNTDOWN_VIDEO = "countdown_video"
}

enum class PhoneStatus(val message: String) {
    STARTING("Iniciando cámara…"),
    READY("Cámara lista."),
    WAITING_WATCH("Cámara lista. Esperando el reloj…"),
    WATCH_CONNECTED("Reloj conectado."),
    PREVIEW_WIFI("Vista previa por Wi‑Fi."),
    PREVIEW_WEAR("Vista previa por Wear OS."),
    PREVIEW_PAUSED("Vista previa pausada. La cámara sigue lista."),
    TAKING_PHOTO("Tomando foto…"),
    PHOTO_SAVED("Foto guardada en Pictures/CamaraWatch."),
    RECORDING("Grabando video…"),
    VIDEO_SAVED("Video guardado en Movies/CamaraWatch."),
    NO_CAMERA_PERMISSION("Falta permiso de cámara."),
    CAMERA_UNAVAILABLE("Cámara no disponible."),
    SWITCH_WHILE_RECORDING("No se puede cambiar de cámara mientras graba."),
    COUNTDOWN("Temporizador…"),
    TIMER_CANCELLED("Temporizador cancelado."),
    RECORD_ERROR("Error al grabar."),
    PHOTO_ERROR("Error al tomar la foto."),
    STOPPED("Cámara detenida."),
}
