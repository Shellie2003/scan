package com.example.viewmodel

import android.app.Application
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.telephony.Contact
import com.example.telephony.ContactsRepository
import com.example.telephony.MessageNotifier
import com.example.telephony.NumberUtils
import com.example.telephony.SmsMessageItem
import com.example.telephony.SmsRepository
import com.example.telephony.SmsSender
import com.example.telephony.SmsThread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            refresh(contacts)
            _openThread.value?.let { loadMessages(it) }
        }
    }

    init {
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
            val sent = SmsSender.sendSingle(getApplication<Application>(), address, body, subscriptionId)
            _openThread.value?.let { loadMessages(it) }
            refresh(contacts)
            onDone(sent)
        }
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

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().contentResolver.unregisterContentObserver(observer)
        } catch (e: Exception) {
            // Already unregistered.
        }
    }
}
