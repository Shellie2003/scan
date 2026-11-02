package com.example.telephony

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A live call, in the shape the UI needs it. */
data class CallSnapshot(
    val id: String,
    val number: String,
    val displayName: String,
    val state: Int,
    val connectTimeMillis: Long,
    val creationTimeMillis: Long,
    val isConference: Boolean,
    /** Which card the phone is ringing on, when it is known. */
    val simLabel: String = "",
    /** Picture of the contact calling, when the phone knows their face. */
    val photoUri: String = ""
) {
    val isRinging: Boolean get() = state == Call.STATE_RINGING
    val isActive: Boolean get() = state == Call.STATE_ACTIVE
    val isHeld: Boolean get() = state == Call.STATE_HOLDING
    val isDialing: Boolean get() = state == Call.STATE_DIALING || state == Call.STATE_CONNECTING
    val isDisconnected: Boolean
        get() = state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING

    val label: String get() = displayName.ifBlank { NumberUtils.formatForDisplay(number) }
}

/**
 * Bridge between the telecom framework and the call screen.
 *
 * The service pushes the calls it is given here; the UI observes [calls] and
 * sends actions back. Keeping the state in one object is what allows the call
 * screen to be a plain composable.
 */
object OngoingCallStore {

    private val tracked = LinkedHashMap<String, Call>()
    private val callbacks = HashMap<String, Call.Callback>()

    private val _calls = MutableStateFlow<List<CallSnapshot>>(emptyList())
    val calls: StateFlow<List<CallSnapshot>> = _calls.asStateFlow()

    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    private val _speakerOn = MutableStateFlow(false)
    val speakerOn: StateFlow<Boolean> = _speakerOn.asStateFlow()

    /** Contact names resolved asynchronously, keyed by national number. */
    private val nameCache = HashMap<String, String>()

    /** SIM card of a call, resolved asynchronously, keyed by call id. */
    private val simLabels = HashMap<String, String>()

    /** Contact pictures, keyed by national number like the names. */
    private val photoCache = HashMap<String, String>()

    @Volatile
    private var service: InCallService? = null

    fun attach(inCallService: InCallService) {
        service = inCallService
    }

    fun detach() {
        service = null
    }

    fun add(call: Call) {
        val id = idOf(call)
        if (tracked.containsKey(id)) return
        tracked[id] = call
        val callback = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) = publish()
            override fun onDetailsChanged(call: Call, details: Call.Details) = publish()
        }
        callbacks[id] = callback
        call.registerCallback(callback)
        publish()
    }

    fun remove(call: Call) {
        val id = idOf(call)
        callbacks.remove(id)?.let { call.unregisterCallback(it) }
        tracked.remove(id)
        simLabels.remove(id)
        publish()
    }

    /** The name and the face of a number, as the contacts gave them. */
    fun cacheContact(number: String, name: String?, photoUri: String?) {
        if (number.isBlank()) return
        val key = NumberUtils.toNational(number)
        if (!name.isNullOrBlank()) nameCache[key] = name
        if (!photoUri.isNullOrBlank()) photoCache[key] = photoUri
        publish()
    }

    fun cacheName(number: String, name: String) {
        if (number.isBlank() || name.isBlank()) return
        nameCache[NumberUtils.toNational(number)] = name
        publish()
    }

    /**
     * Which card a call came in on.
     *
     * On a phone holding three cards, this is who is being called: the line of
     * the shop, or the one the family knows.
     */
    fun cacheSimLabel(call: Call, label: String) {
        if (label.isBlank()) return
        simLabels[idOf(call)] = label
        publish()
    }

    fun onAudioStateChanged(audioState: CallAudioState) {
        _muted.value = audioState.isMuted
        _speakerOn.value = audioState.route == CallAudioState.ROUTE_SPEAKER
    }

    fun hasCalls(): Boolean = tracked.isNotEmpty()

    // ------------------------------------------------------------------ actions

    /**
     * Answers, refuses, hangs up — and says whether it went through.
     *
     * Each of these three reaches the telecom framework, which can refuse: the
     * call has just ended, the permission to answer was never granted, the
     * framework has already let go of it. Thrown from a notification button,
     * that exception used to take the whole application down while the phone
     * was ringing. It is caught here, and what the caller gets back is enough
     * to tell the user that nothing happened.
     */
    fun answer(id: String): Boolean = act(id) { it.answer(VideoProfile.STATE_AUDIO_ONLY) }

    fun reject(id: String): Boolean = act(id) { it.reject(false, null) }

    fun hangUp(id: String): Boolean = act(id) { it.disconnect() }

    private inline fun act(id: String, action: (Call) -> Unit): Boolean {
        val call = resolve(id) ?: return false
        return try {
            action(call)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** The number of a call, to answer it by message instead of by voice. */
    fun numberOf(id: String): String =
        tracked[id]?.details?.handle?.schemeSpecificPart.orEmpty()

    /**
     * The call an action is about.
     *
     * A notification button can be pressed after the call it was posted for has
     * been replaced — the identifier it carries is then unknown, and doing
     * nothing would leave the phone ringing in the hand of the user. The call
     * that is ringing is what they meant.
     */
    @Suppress("DEPRECATION")
    private fun resolve(id: String): Call? = tracked[id]
        ?: tracked.values.firstOrNull { it.state == Call.STATE_RINGING }
        ?: tracked.values.lastOrNull()

    fun hangUpAll() {
        tracked.values.toList().forEach { call ->
            try {
                call.disconnect()
            } catch (e: Exception) {
                // One call that will not let go must not keep the others up.
            }
        }
    }

    fun toggleHold(id: String) {
        val call = tracked[id] ?: return
        try {
            @Suppress("DEPRECATION")
            if (call.state == Call.STATE_HOLDING) call.unhold() else call.hold()
        } catch (e: Exception) {
            // A line that refuses to be held stays as it is.
        }
    }

    fun setMuted(muted: Boolean) {
        try {
            service?.setMuted(muted)
        } catch (e: Exception) {
            // The audio route is the framework's to give.
        }
        _muted.value = muted
    }

    fun setSpeaker(on: Boolean) {
        try {
            service?.setAudioRoute(
                if (on) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE
            )
        } catch (e: Exception) {
            // Same: the button goes back to what the framework reports.
        }
        _speakerOn.value = on
    }

    fun playDtmf(id: String, digit: Char) {
        val call = tracked[id] ?: return
        try {
            call.playDtmfTone(digit)
            call.stopDtmfTone()
        } catch (e: Exception) {
            // A tone the line will not play is not worth a crash.
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun idOf(call: Call): String = System.identityHashCode(call).toString()

    @Suppress("DEPRECATION")
    private fun publish() {
        _calls.value = tracked.map { (id, call) ->
            val details = call.details
            val number = details?.handle?.schemeSpecificPart.orEmpty()
            val cached = nameCache[NumberUtils.toNational(number)].orEmpty()
            CallSnapshot(
                id = id,
                number = number,
                displayName = cached.ifBlank { details?.callerDisplayName.orEmpty() },
                state = call.state,
                connectTimeMillis = details?.connectTimeMillis ?: 0L,
                creationTimeMillis = if (details != null &&
                    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
                ) details.creationTimeMillis else 0L,
                isConference = call.children.isNotEmpty(),
                simLabel = simLabels[id].orEmpty(),
                photoUri = photoCache[NumberUtils.toNational(number)].orEmpty()
            )
        }
    }
}
