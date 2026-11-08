package com.george.camarawatch.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CameraControlTest {
    @Test
    fun photoCommandsRoundTrip() {
        listOf(
            CameraCommand.TakePhoto,
            CameraCommand.StartRecord,
            CameraCommand.StopRecord,
            CameraCommand.SwitchCamera,
            CameraCommand.ZoomIn,
            CameraCommand.ZoomOut,
            CameraCommand.ExposureUp,
            CameraCommand.ExposureDown,
            CameraCommand.CancelCountdown,
        ).forEach { command ->
            assertEquals(command, CameraCommand.fromWire(command.toWire()))
        }
    }

    @Test
    fun zoomAndFlashCarryPayload() {
        val zoom = CameraCommand.SetZoom(2.5f)
        assertEquals("SET_ZOOM|2.5000", zoom.toWire())
        assertEquals(zoom, CameraCommand.fromWire(zoom.toWire()))
        assertNull(CameraCommand.fromWire("SET_ZOOM|no"))
        assertNull(CameraCommand.fromWire("SET_FLASH|NOPE"))
        assertEquals(
            CameraCommand.SetFlash(FlashMode.TORCH),
            CameraCommand.fromWire("SET_FLASH|TORCH"),
        )
    }

    @Test
    fun timerCommandsRoundTrip() {
        assertEquals(CameraCommand.SetTimer(10), CameraCommand.fromWire("SET_TIMER|10"))
        assertEquals(CameraCommand.ArmCountdown(video = false, seconds = 3), CameraCommand.fromWire("ARM|PHOTO|3"))
        assertEquals(CameraCommand.ArmCountdown(video = true, seconds = 10), CameraCommand.fromWire("ARM|VIDEO|10"))
        assertEquals(CameraCommand.CancelCountdown, CameraCommand.fromWire("CANCEL_TIMER"))
        assertNull(CameraCommand.fromWire("SET_TIMER|5"))
        assertNull(CameraCommand.fromWire("ARM|PHOTO|2"))
        assertNull(CameraCommand.fromWire("ARM|BOOM|3"))
    }

    @Test
    fun flashCycleSkipsUnsupportedModes() {
        assertEquals(FlashMode.ON, FlashMode.OFF.next(flashSupported = true, torchSupported = true))
        assertEquals(FlashMode.AUTO, FlashMode.ON.next(flashSupported = true, torchSupported = true))
        assertEquals(FlashMode.TORCH, FlashMode.AUTO.next(flashSupported = true, torchSupported = true))
        assertEquals(FlashMode.OFF, FlashMode.TORCH.next(flashSupported = true, torchSupported = true))
        assertEquals(FlashMode.TORCH, FlashMode.OFF.next(flashSupported = false, torchSupported = true))
        assertNull(FlashMode.OFF.next(flashSupported = false, torchSupported = false))
        assertEquals(FlashMode.OFF, FlashMode.AUTO.next(flashSupported = true, torchSupported = false))
    }

    @Test
    fun snapshotRoundTripKeepsSpanishMessageAndControls() {
        val controls = CameraControls(
            facing = CameraFacing.FRONT,
            zoomRatio = 1.5f,
            minZoom = 1f,
            maxZoom = 8f,
            flashMode = FlashMode.OFF,
            flashSupported = false,
            torchSupported = false,
            exposureIndex = 2,
            exposureMin = -12,
            exposureMax = 12,
            exposureStep = 0.5f,
            canSwitchCamera = true,
        )
        val wire = encodeSnapshot(
            status = PhoneStatus.READY,
            message = "Cámara frontal lista.",
            recording = false,
            controls = controls,
        )
        val decoded = decodeSnapshot(wire)
        assertEquals(PhoneStatus.READY, decoded.status)
        assertEquals("Cámara frontal lista.", decoded.message)
        assertEquals(false, decoded.recording)
        assertEquals(CameraFacing.FRONT, decoded.controls?.facing)
        assertEquals(1.5f, decoded.controls?.zoomRatio)
        assertEquals(false, decoded.controls?.flashSupported)
        assertEquals(true, decoded.controls?.canSwitchCamera)
        assertEquals("Cámara frontal · Zoom 1.5x · exposición +1.0", decoded.controls?.summary())
        val timed = decodeSnapshot(
            encodeSnapshot(
                status = PhoneStatus.COUNTDOWN,
                message = "Foto en 3…",
                recording = false,
                controls = controls,
                timer = ShutterTimer(seconds = 3, remaining = 3, forVideo = false),
            ),
        )
        assertEquals(3, timed.timer.remaining)
        assertEquals(3, timed.timer.seconds)
        assertEquals(false, timed.timer.forVideo)
        assertEquals("Foto en 3…", timed.message)
    }

    @Test
    fun legacyStatusStillParses() {
        val decoded = decodeSnapshot("PREVIEW_WIFI|Vista previa por Wi‑Fi.|true")
        assertEquals("Vista previa por Wi‑Fi.", decoded.message)
        assertEquals(true, decoded.recording)
        assertNull(decoded.controls)
    }
}
