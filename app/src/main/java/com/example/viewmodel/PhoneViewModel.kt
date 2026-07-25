package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.telephony.CallEntry
import com.example.telephony.CallLogRepository
import com.example.telephony.Contact
import com.example.telephony.ContactsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Contacts and call log of the device, reloaded on demand. */
class PhoneViewModel(application: Application) : AndroidViewModel(application) {

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    private val _callLog = MutableStateFlow<List<CallEntry>>(emptyList())
    val callLog: StateFlow<List<CallEntry>> = _callLog.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _contacts.value = ContactsRepository.load(getApplication<Application>())
            _callLog.value = CallLogRepository.load(getApplication<Application>())
            _loading.value = false
        }
    }

    fun refreshCallLog() {
        viewModelScope.launch {
            _callLog.value = CallLogRepository.load(getApplication<Application>())
        }
    }

    fun favorites(): List<Contact> = _contacts.value.filter { it.isFavorite }
}
