package com.george.camarawatch.shared

import java.util.Locale

enum class CameraFacing {
    BACK,
    FRONT;

    fun shortName(): String = if (this == BACK) "Trasera" else "Frontal"

    fun longName(): String = if (this == BACK) "Cámara trasera" else "Cámara frontal"

    companion object {
        fun fromWire(value: String?): CameraFacing = entries.firstOrNull { it.name == value } ?: BACK
    }
}

enum class FlashMode {
    OFF,
    ON,
    AUTO,
    TORCH;

    fun chipLabel(): String = when (this) {
        OFF -> "Apagado"
        ON -> "Encendido"
        AUTO -> "Automático"
        TORCH -> "Linterna"
    }

    fun shortLabel(): String = when (this) {
        OFF -> "flash apagado"
        ON -> "flash encendido"
        AUTO -> "flash automático"
        TORCH -> "linterna"
    }

    fun next(flashSupported: Boolean, torchSupported: Boolean): FlashMode? {
        val modes = buildList {
            add(OFF)
            if (flashSupported) {
                add(ON)
                add(AUTO)
            }
            if (torchSupported) add(TORCH)
        }
        if (modes.size < 2) return null
        val index = modes.indexOf(this).let { if (it < 0) 0 else it }
        return modes[(index + 1) % modes.size]
    }

    companion object {
        fun fromWire(value: String?): FlashMode = entries.firstOrNull { it.name == value } ?: OFF
    }
}

data class CameraControls(
    val facing: CameraFacing = CameraFacing.BACK,
    val zoomRatio: Float = 1f,
    val minZoom: Float = 1f,
    val maxZoom: Float = 1f,
    val flashMode: FlashMode = FlashMode.OFF,
    val flashSupported: Boolean = false,
    val torchSupported: Boolean = false,
    val exposureIndex: Int = 0,
    val exposureMin: Int = 0,
    val exposureMax: Int = 0,
    val exposureStep: Float = 0f,
    val canSwitchCamera: Boolean = false,
) {
    val zoomSupported: Boolean get() = maxZoom > minZoom + 0.05f

    val exposureSupported: Boolean get() = exposureMax > exposureMin && exposureStep > 0f

    val flashControlVisible: Boolean get() = flashSupported || torchSupported

    fun zoomLabel(): String = "${formatZoom(zoomRatio)}x"

    fun headline(): String = "${facing.shortName()} · ${zoomLabel()}"

    fun exposureText(): String? {
        if (!exposureSupported || exposureIndex == 0) return null
        return "exposición ${formatEv(exposureIndex * exposureStep)}"
    }

    fun exposureValue(): String = formatEv(exposureIndex * exposureStep)

    fun flashText(): String? = when {
        flashMode == FlashMode.TORCH -> "linterna"
        !flashSupported && !torchSupported -> null
        else -> flashMode.shortLabel()
    }

    fun summary(): String = listOfNotNull(
        facing.longName(),
        "Zoom ${zoomLabel()}",
        flashText(),
        exposureText(),
    ).joinToString(" · ")

    fun shortLabel(): String = listOfNotNull(headline(), flashText(), exposureText()).joinToString(" · ")

    fun toWire(): String = listOf(
        facing.name,
        fmt(zoomRatio),
        fmt(minZoom),
        fmt(maxZoom),
        flashMode.name,
        bit(flashSupported),
        bit(torchSupported),
        exposureIndex.toString(),
        exposureMin.toString(),
        exposureMax.toString(),
        fmt(exposureStep),
        bit(canSwitchCamera),
    ).joinToString("|")

    companion object {
        fun fromParts(parts: List<String>, offset: Int): CameraControls {
            fun raw(index: Int) = parts.getOrNull(offset + index)
            fun decimal(index: Int, default: Float) = raw(index)?.toFloatOrNull() ?: default
            fun number(index: Int, default: Int) = raw(index)?.toIntOrNull() ?: default
            fun flag(index: Int) = raw(index) == "1"
            return CameraControls(
                facing = CameraFacing.fromWire(raw(0)),
                zoomRatio = decimal(1, 1f),
                minZoom = decimal(2, 1f),
                maxZoom = decimal(3, 1f),
                flashMode = FlashMode.fromWire(raw(4)),
                flashSupported = flag(5),
                torchSupported = flag(6),
                exposureIndex = number(7, 0),
                exposureMin = number(8, 0),
                exposureMax = number(9, 0),
                exposureStep = decimal(10, 0f),
                canSwitchCamera = flag(11),
            )
        }
    }
}

fun formatZoom(ratio: Float): String = if (ratio >= 10f) {
    String.format(Locale.US, "%.0f", ratio)
} else {
    String.format(Locale.US, "%.1f", ratio)
}

fun formatEv(ev: Float): String = String.format(Locale.US, "%+.1f", ev)

private fun fmt(value: Float): String = String.format(Locale.US, "%.4f", value)

private fun bit(value: Boolean): String = if (value) "1" else "0"

fun isTimerChoice(seconds: Int): Boolean = seconds == 0 || seconds == 3 || seconds == 10

data class ShutterTimer(
    val seconds: Int = 0,
    val remaining: Int = -1,
    val forVideo: Boolean = false,
) {
    val counting: Boolean get() = remaining > 0
}

sealed class CameraCommand {
    data object TakePhoto : CameraCommand()
    data object StartRecord : CameraCommand()
    data object StopRecord : CameraCommand()
    data object SwitchCamera : CameraCommand()
    data object ZoomIn : CameraCommand()
    data object ZoomOut : CameraCommand()
    data class SetZoom(val ratio: Float) : CameraCommand()
    data class SetFlash(val mode: FlashMode) : CameraCommand()
    data object ExposureUp : CameraCommand()
    data object ExposureDown : CameraCommand()
    data class SetTimer(val seconds: Int) : CameraCommand()
    data class ArmCountdown(val video: Boolean, val seconds: Int) : CameraCommand()
    data object CancelCountdown : CameraCommand()

    fun toWire(): String = when (this) {
        TakePhoto -> Protocol.TAKE_PHOTO
        StartRecord -> Protocol.START_RECORD
        StopRecord -> Protocol.STOP_RECORD
        SwitchCamera -> Protocol.SWITCH_CAMERA
        ZoomIn -> Protocol.ZOOM_IN
        ZoomOut -> Protocol.ZOOM_OUT
        is SetZoom -> "${Protocol.SET_ZOOM}|${fmt(ratio)}"
        is SetFlash -> "${Protocol.SET_FLASH}|${mode.name}"
        ExposureUp -> Protocol.EXPOSURE_UP
        ExposureDown -> Protocol.EXPOSURE_DOWN
        is SetTimer -> "${Protocol.SET_TIMER}|$seconds"
        is ArmCountdown -> "${Protocol.ARM}|${if (video) "VIDEO" else "PHOTO"}|$seconds"
        CancelCountdown -> Protocol.CANCEL_TIMER
    }

    companion object {
        fun fromWire(value: String?): CameraCommand? {
            if (value.isNullOrBlank()) return null
            val parts = value.split('|')
            return when (parts[0]) {
                Protocol.TAKE_PHOTO -> TakePhoto
                Protocol.START_RECORD -> StartRecord
                Protocol.STOP_RECORD -> StopRecord
                Protocol.SWITCH_CAMERA -> SwitchCamera
                Protocol.ZOOM_IN -> ZoomIn
                Protocol.ZOOM_OUT -> ZoomOut
                Protocol.EXPOSURE_UP -> ExposureUp
                Protocol.EXPOSURE_DOWN -> ExposureDown
                Protocol.SET_ZOOM -> {
                    val ratio = parts.getOrNull(1)?.toFloatOrNull() ?: return null
                    if (!ratio.isFinite() || ratio <= 0f || ratio > 100f) null else SetZoom(ratio)
                }
                Protocol.SET_FLASH -> {
                    val mode = FlashMode.entries.firstOrNull { it.name == parts.getOrNull(1) } ?: return null
                    SetFlash(mode)
                }
                Protocol.SET_TIMER -> {
                    val seconds = parts.getOrNull(1)?.toIntOrNull() ?: return null
                    if (!isTimerChoice(seconds)) null else SetTimer(seconds)
                }
                Protocol.ARM -> {
                    val video = when (parts.getOrNull(1)) {
                        "VIDEO" -> true
                        "PHOTO" -> false
                        else -> return null
                    }
                    val seconds = parts.getOrNull(2)?.toIntOrNull() ?: return null
                    if (!isTimerChoice(seconds)) null else ArmCountdown(video, seconds)
                }
                Protocol.CANCEL_TIMER -> CancelCountdown
                else -> null
            }
        }
    }
}

data class PhoneSnapshot(
    val status: PhoneStatus?,
    val message: String,
    val recording: Boolean,
    val controls: CameraControls?,
    val timer: ShutterTimer = ShutterTimer(),
)

fun encodeSnapshot(
    status: PhoneStatus,
    message: String,
    recording: Boolean,
    controls: CameraControls,
    timer: ShutterTimer = ShutterTimer(),
): String {
    val safe = message.replace("|", "·").replace("\n", " ")
    return buildString {
        append("v2|")
        append(status.name)
        append('|')
        append(safe)
        append('|')
        append(if (recording) "1" else "0")
        append('|')
        append(controls.toWire())
        append('|')
        append(timer.seconds)
        append('|')
        append(timer.remaining)
        append('|')
        append(if (timer.forVideo) "1" else "0")
    }
}

fun decodeSnapshot(text: String): PhoneSnapshot {
    val parts = text.split('|')
    if (parts.size >= 16 && parts[0] == "v2") {
        return PhoneSnapshot(
            status = PhoneStatus.entries.firstOrNull { it.name == parts[1] },
            message = parts[2].ifBlank { "Cámara lista." },
            recording = parts[3] == "1" || parts[3].equals("true", ignoreCase = true),
            controls = CameraControls.fromParts(parts, offset = 4),
            timer = ShutterTimer(
                seconds = parts.getOrNull(16)?.toIntOrNull()?.takeIf(::isTimerChoice) ?: 0,
                remaining = parts.getOrNull(17)?.toIntOrNull() ?: -1,
                forVideo = parts.getOrNull(18) == "1",
            ),
        )
    }
    return PhoneSnapshot(
        status = PhoneStatus.entries.firstOrNull { it.name == parts.getOrNull(0) },
        message = parts.getOrNull(1)?.ifBlank { null } ?: text,
        recording = parts.getOrNull(2)?.toBooleanStrictOrNull() ?: false,
        controls = null,
    )
}
