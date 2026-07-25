package com.example.viewmodel

import android.app.Application
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.telephony.Contact
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
        }
    }

    fun open(thread: SmsThread) {
        _openThread.value = thread
        loadMessages(thread)
        viewModelScope.launch {
            SmsRepository.markThreadRead(getApplication<Application>(), thread.threadId)
            refresh(contacts)
        }
    }

    /** Opens — or starts — the conversation with a number. */
    fun openWithAddress(address: String, name: String = "") {
        val existing = _threads.value.firstOrNull {
            com.example.telephony.NumberUtils.sameNumber(it.address, address)
        }
        if (existing != null) {
            open(existing)
            return
        }
        val draft = SmsThread(
            threadId = -1L,
            address = address,
            contactName = name,
            snippet = "",
            date = System.currentTimeMillis(),
            unreadCount = 0,
            messageCount = 0
        )
        _openThread.value = draft
        loadMessages(draft)
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

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().contentResolver.unregisterContentObserver(observer)
        } catch (e: Exception) {
            // Already unregistered.
        }
    }
}
