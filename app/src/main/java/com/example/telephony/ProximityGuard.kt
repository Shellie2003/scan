package com.example.telephony

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager

/**
 * Turns the screen off while the telephone is held against the ear.
 *
 * Without this, a call looks like any other screen: lit, and listening for
 * touches. The call screen keeps the display on for the whole conversation —
 * it has to, so that hanging up does not mean waking the phone up first — and
 * the ear then presses whatever is under it. The loudspeaker button is the
 * widest of them, which is why a call turns itself into a hands-free call in
 * the middle of a sentence. It is not the audio code: it is a cheek.
 *
 * Every telephone solves this with the proximity sensor, and so does this:
 *
 *  - the proper way is the proximity wake lock, which turns the display *and
 *    the touchscreen* off in the hands of the system. Nothing reaches the
 *    application at all, which is exactly what is wanted;
 *  - a phone whose system refuses that lock is read from the sensor directly,
 *    and [onNear] lets the call screen cover itself with a shield that swallows
 *    the touches. The screen stays lit, which is a waste of battery and the
 *    best that can be done there.
 *
 * It is owned by the service rather than by the screen on purpose: the lock
 * turning the display off can stop the activity, which would release the lock
 * and light the screen back up, over and over.
 */
class ProximityGuard(
    private val context: Context,
    private val onNear: (Boolean) -> Unit
) {

    private val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val sensors = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private var lock: PowerManager.WakeLock? = null
    private var listener: SensorEventListener? = null
    private var running = false

    /** `true` once the guard is watching, whichever of the two ways it found. */
    fun start() {
        if (running) return
        running = true
        if (acquireLock()) return
        listenToSensor()
    }

    fun stop() {
        if (!running) return
        running = false
        try {
            if (lock?.isHeld == true) lock?.release()
        } catch (e: Exception) {
            // A lock the system has already taken back is no longer ours.
        }
        lock = null
        listener?.let { registered ->
            try {
                sensors?.unregisterListener(registered)
            } catch (e: Exception) {
                // Nothing to do: the sensor is the system's.
            }
        }
        listener = null
        // Whatever happens, the shield comes down: a screen left covered after
        // the call is a telephone that looks broken.
        onNear(false)
    }

    private fun acquireLock(): Boolean {
        val manager = power ?: return false
        val supported = try {
            manager.isWakeLockLevelSupported(PROXIMITY_SCREEN_OFF_WAKE_LOCK)
        } catch (e: Exception) {
            false
        }
        if (!supported) return false
        return try {
            @Suppress("WakelockTimeout")
            val acquired = manager.newWakeLock(PROXIMITY_SCREEN_OFF_WAKE_LOCK, TAG)
            acquired.setReferenceCounted(false)
            // No timeout: the lock lives exactly as long as the call, and
            // [stop] is called whatever way the call ends.
            acquired.acquire()
            lock = acquired
            true
        } catch (e: Exception) {
            // A system that declares the level and then refuses it: the sensor
            // is read by hand instead.
            lock = null
            false
        }
    }

    private fun listenToSensor() {
        val manager = sensors ?: return
        val sensor = try {
            manager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        } catch (e: Exception) {
            null
        } ?: return
        // A sensor reports centimetres on some phones and 0 or 5 on most.
        // "Near" is anything closer than its own range, which is the only
        // reading that means the same thing everywhere.
        val near = if (sensor.maximumRange > 0f) sensor.maximumRange else NEAR_CM
        val registered = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val distance = event.values.firstOrNull() ?: return
                onNear(distance < near)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        val ok = try {
            manager.registerListener(registered, sensor, SensorManager.SENSOR_DELAY_UI)
        } catch (e: Exception) {
            false
        }
        listener = if (ok) registered else null
    }

    private companion object {
        /**
         * `PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK`.
         *
         * The constant is not published, the method that takes it is. Every
         * telephone application uses it this way, and a system that does not
         * know the level says so through `isWakeLockLevelSupported`.
         */
        const val PROXIMITY_SCREEN_OFF_WAKE_LOCK = 32

        /** Fallback threshold for a sensor that does not state its range. */
        const val NEAR_CM = 5f

        const val TAG = "scan:call-proximity"
    }
}
