package com.example.viewmodel

import android.app.Application
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.telephony.BulkSmsQueue
import com.example.telephony.BulkSmsService
import com.example.telephony.Contact
import com.example.telephony.ContactsRepository
import com.example.telephony.MessageNotifier
import com.example.telephony.NumberUtils
import com.example.telephony.SmsMessageItem
import com.example.telephony.SmsRecipient
import com.example.telephony.SmsRepository
import com.example.telephony.SmsSendReport
import com.example.telephony.SmsSender
import com.example.telephony.SmsThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The inbox: conversations and the messages of the open one.
 *
 * A content observer on the SMS database keeps both lists live, so a message
 * that arrives while the screen is open shows up without any manual refresh.
 */
class MessagesViewModel(application: Application) : AndroidViewModel(application) {

    private val _threads = MutableStateFlow<List<SmsThread>>(emptyList())
    val threads: StateFlow<List<SmsThread>> = _threads.asStateFlow()

    private val _messages = MutableStateFlow<List<SmsMessageItem>>(emptyList())
    val messages: StateFlow<List<SmsMessageItem>> = _messages.asStateFlow()

    private val _openThread = MutableStateFlow<SmsThread?>(null)
    val openThread: StateFlow<SmsThread?> = _openThread.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private var contacts: List<Contact> = emptyList()

    /**
     * Requests to reload, coalesced.
     *
     * Every write to the SMS provider notifies the observer — including each of
     * the two hundred copies a bulk send writes. Reloading the whole inbox that
     * many times froze the phone; the requests are now folded into one.
     */
    private val reloadRequests = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            reloadRequests.tryEmit(Unit)
        }
    }

    init {
        viewModelScope.launch {
            reloadRequests.debounce(RELOAD_DEBOUNCE_MS).collect {
                refresh(contacts)
                _openThread.value?.let { loadMessages(it) }
            }
        }
        try {
            getApplication<Application>().contentResolver.registerContentObserver(
                SmsRepository.contentUri,
                true,
                observer
            )
        } catch (e: Exception) {
            // The provider is unavailable on this device.
        }
    }

    fun refresh(contacts: List<Contact> = this.contacts) {
        this.contacts = contacts
        viewModelScope.launch {
            _loading.value = true
            _threads.value = SmsRepository.loadThreads(getApplication<Application>(), contacts)
            _loading.value = false
            attachOpenThread()
        }
    }

    /**
     * A conversation opened from a notification starts as a draft, because the
     * inbox is not loaded yet. As soon as it is, the real conversation — with its
     * id, and therefore its history and its deletion — takes its place.
     */
    private fun attachOpenThread() {
        val open = _openThread.value ?: return
        if (open.threadId >= 0) return
        val match = _threads.value.firstOrNull { NumberUtils.sameNumber(it.address, open.address) }
            ?: return
        _openThread.value = if (open.contactName.isNotBlank() && match.contactName.isBlank()) {
            match.copy(contactName = open.contactName)
        } else {
            match
        }
        loadMessages(_openThread.value ?: return)
    }

    fun open(thread: SmsThread) {
        _openThread.value = thread
        loadMessages(thread)
        MessageNotifier.cancel(getApplication<Application>(), thread.address)
        viewModelScope.launch {
            markRead(thread)
            refresh(contacts)
        }
    }

    /** Marks a conversation as read, whether it is known by id or by number only. */
    private suspend fun markRead(thread: SmsThread) {
        if (thread.threadId >= 0) {
            SmsRepository.markThreadRead(getApplication<Application>(), thread.threadId)
        } else {
            SmsRepository.markAddressRead(getApplication<Application>(), thread.address)
        }
    }

    /**
     * Opens — or starts — the conversation with a number.
     *
     * The name is looked up when the caller does not know it, which is the case
     * when the conversation is opened from a notification.
     */
    fun openWithAddress(address: String, name: String = "") {
        if (address.isBlank()) return
        val known = name.ifBlank {
            contacts.firstOrNull { NumberUtils.sameNumber(it.number, address) }?.name.orEmpty()
        }
        val existing = _threads.value.firstOrNull { NumberUtils.sameNumber(it.address, address) }
        if (existing != null) {
            open(if (known.isNotBlank()) existing.copy(contactName = known) else existing)
            if (known.isBlank()) resolveName(address)
            return
        }
        val draft = SmsThread(
            threadId = -1L,
            address = address,
            contactName = known,
            snippet = "",
            date = System.currentTimeMillis(),
            unreadCount = 0,
            messageCount = 0
        )
        _openThread.value = draft
        loadMessages(draft)
        MessageNotifier.cancel(getApplication<Application>(), address)
        viewModelScope.launch {
            markRead(draft)
            refresh(contacts)
        }
        if (known.isBlank()) resolveName(address)
    }

    /** Fills the title of an open conversation with the contact name, when there is one. */
    private fun resolveName(address: String) {
        viewModelScope.launch {
            val resolved = ContactsRepository.nameFor(getApplication<Application>(), address).orEmpty()
            if (resolved.isBlank()) return@launch
            val current = _openThread.value ?: return@launch
            if (NumberUtils.sameNumber(current.address, address) && current.contactName.isBlank()) {
                _openThread.value = current.copy(contactName = resolved)
            }
        }
    }

    fun close() {
        _openThread.value = null
        _messages.value = emptyList()
    }

    private fun loadMessages(thread: SmsThread) {
        viewModelScope.launch {
            _messages.value = if (thread.threadId >= 0) {
                SmsRepository.loadMessages(getApplication<Application>(), thread.threadId)
            } else {
                SmsRepository.loadMessagesForAddress(getApplication<Application>(), thread.address)
            }
        }
    }

    /** Sends a reply and refreshes the conversation. */
    fun send(address: String, body: String, subscriptionId: Int, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            // Sending goes through the telephony binder and writes the copy to the
            // SMS provider: never on the thread that draws the conversation.
            val sent = withContext(Dispatchers.IO) {
                SmsSender.sendSingle(getApplication<Application>(), address, body, subscriptionId)
            }
            _openThread.value?.let { loadMessages(it) }
            refresh(contacts)
            onDone(sent)
        }
    }

    // --------------------------------------------------------------- bulk send

    /**
     * Progress and result of the campaign, held by [BulkSmsQueue] so they
     * survive this view model — and the screen that started them.
     */
    val bulkProgress: StateFlow<BulkSmsQueue.Progress?> = BulkSmsQueue.progress
    val bulkReport: StateFlow<SmsSendReport?> = BulkSmsQueue.report

    /**
     * Sends one message to many recipients, each group going through its own SIM.
     *
     * The work is handed to [BulkSmsService]: two hundred messages spaced out by
     * the pause operators need take more than a minute, and the user must be
     * free to leave the tab — or the application — while it finishes.
     */
    fun sendBulk(
        groups: Map<Int, List<SmsRecipient>>,
        body: String,
        subscriptionId: Int,
        simLabel: String
    ) {
        BulkSmsService.start(
            context = getApplication<Application>(),
            groups = groups,
            body = body,
            subscriptionId = subscriptionId,
            simLabel = simLabel
        )
    }

    fun clearBulkReport() {
        BulkSmsQueue.clearReport()
    }

    fun deleteThread(thread: SmsThread) {
        viewModelScope.launch {
            SmsRepository.deleteThread(getApplication<Application>(), thread.threadId)
            close()
            refresh(contacts)
        }
    }

    /** Deletes the conversations selected in the inbox. */
    fun deleteThreads(threads: Collection<SmsThread>) {
        if (threads.isEmpty()) return
        viewModelScope.launch {
            threads.forEach { thread ->
                if (thread.threadId >= 0) {
                    SmsRepository.deleteThread(getApplication<Application>(), thread.threadId)
                }
                MessageNotifier.cancel(getApplication<Application>(), thread.address)
            }
            refresh(contacts)
        }
    }

    /** Deletes the messages selected inside the open conversation. */
    fun deleteMessages(ids: Collection<Long>, onDone: () -> Unit = {}) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            SmsRepository.deleteMessages(getApplication<Application>(), ids)
            _openThread.value?.let { loadMessages(it) }
            refresh(contacts)
            onDone()
        }
    }

    private companion object {
        /** Long enough to swallow a burst of writes, short enough to feel live. */
        const val RELOAD_DEBOUNCE_MS = 350L
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().contentResolver.unregisterContentObserver(observer)
        } catch (e: Exception) {
            // Already unregistered.
        }
    }
}
