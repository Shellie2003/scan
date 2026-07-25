package com.example.telephony

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Contact(
    val id: Long,
    val name: String,
    val number: String,
    val photoUri: String? = null,
    val isFavorite: Boolean = false
) {
    val initials: String
        get() = name.split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .map { it.first().uppercaseChar() }
            .joinToString("")
            .ifEmpty { "#" }
}

object ContactsRepository {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

    /** All contacts holding at least one phone number, one entry per number. */
    @SuppressLint("MissingPermission")
    suspend fun load(context: Context): List<Contact> = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) return@withContext emptyList()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
            ContactsContract.CommonDataKinds.Phone.STARRED
        )
        val result = mutableListOf<Contact>()
        val seen = mutableSetOf<String>()
        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE ASC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)
                val starredIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.STARRED)
                while (cursor.moveToNext()) {
                    val number = if (numberIndex >= 0) cursor.getString(numberIndex).orEmpty() else ""
                    if (number.isBlank()) continue
                    val id = if (idIndex >= 0) cursor.getLong(idIndex) else 0L
                    val key = id.toString() + "|" + NumberUtils.toNational(number)
                    if (!seen.add(key)) continue
                    result += Contact(
                        id = id,
                        name = if (nameIndex >= 0) cursor.getString(nameIndex).orEmpty() else "",
                        number = number,
                        photoUri = if (photoIndex >= 0) cursor.getString(photoIndex) else null,
                        isFavorite = starredIndex >= 0 && cursor.getInt(starredIndex) == 1
                    )
                }
            }
        } catch (e: SecurityException) {
            return@withContext emptyList()
        } catch (e: Exception) {
            return@withContext emptyList()
        }
        result
    }

    /** Display name of a number, or `null` when it is unknown. */
    @SuppressLint("MissingPermission")
    suspend fun nameFor(context: Context, number: String): String? = withContext(Dispatchers.IO) {
        if (!hasPermission(context) || number.isBlank()) return@withContext null
        val uri = android.net.Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            android.net.Uri.encode(number)
        )
        try {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) return@withContext cursor.getString(0)
            }
        } catch (e: Exception) {
            return@withContext null
        }
        null
    }

    /**
     * Name and T9-like search: matches the name, the number, or the digits typed
     * on the keypad ("226" finds "Bao" through the letters of the keys).
     */
    fun search(contacts: List<Contact>, query: String): List<Contact> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return contacts
        val lower = trimmed.lowercase()
        val digits = trimmed.filter { it.isDigit() }
        return contacts.filter { contact ->
            contact.name.lowercase().contains(lower) ||
                (digits.isNotEmpty() && NumberUtils.sanitize(contact.number).contains(digits)) ||
                (digits.isNotEmpty() && t9(contact.name).contains(digits))
        }
    }

    private fun t9(name: String): String = buildString {
        name.lowercase().forEach { c ->
            append(
                when (c) {
                    in 'a'..'c' -> '2'
                    in 'd'..'f' -> '3'
                    in 'g'..'i' -> '4'
                    in 'j'..'l' -> '5'
                    in 'm'..'o' -> '6'
                    in 'p'..'s' -> '7'
                    in 't'..'v' -> '8'
                    in 'w'..'z' -> '9'
                    else -> ' '
                }
            )
        }
    }
}
