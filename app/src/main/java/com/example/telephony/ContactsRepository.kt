package com.example.telephony

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentProviderOperation
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

    fun hasWritePermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CONTACTS
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

    // ------------------------------------------------------------------ writing

    /**
     * Creates a contact on the phone itself.
     *
     * The raw contact is written without an account, which is what the system
     * calls a device contact: it belongs to the phone, not to a synchronised
     * account the user may not have.
     */
    suspend fun create(context: Context, name: String, number: String): Boolean =
        withContext(Dispatchers.IO) {
            if (!hasWritePermission(context) || number.isBlank()) return@withContext false
            val operations = arrayListOf(
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                    .build(),
                nameInsert(name),
                numberInsert(number)
            )
            try {
                context.contentResolver
                    .applyBatch(ContactsContract.AUTHORITY, operations)
                    .isNotEmpty()
            } catch (e: Exception) {
                false
            }
        }

    /**
     * Renames a contact and rewrites the number the app knows it by.
     *
     * A contact created by another application may carry no name row at all, so
     * an update that changes nothing is followed by an insert.
     */
    suspend fun update(
        context: Context,
        contact: Contact,
        name: String,
        number: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (!hasWritePermission(context) || number.isBlank()) return@withContext false
        val id = contact.id.toString()
        val operations = arrayListOf(
            ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                .withSelection(
                    ContactsContract.Data.CONTACT_ID + " = ? AND " +
                        ContactsContract.Data.MIMETYPE + " = ?",
                    arrayOf(id, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                )
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                .build(),
            ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                .withSelection(
                    ContactsContract.Data.CONTACT_ID + " = ? AND " +
                        ContactsContract.Data.MIMETYPE + " = ? AND " +
                        ContactsContract.CommonDataKinds.Phone.NUMBER + " = ?",
                    arrayOf(
                        id,
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                        contact.number
                    )
                )
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, number)
                .build()
        )
        try {
            val results = context.contentResolver
                .applyBatch(ContactsContract.AUTHORITY, operations)
            val rawContactId = rawContactIdOf(context, contact.id) ?: return@withContext true
            val missing = arrayListOf<ContentProviderOperation>()
            if ((results.getOrNull(0)?.count ?: 0) == 0 && name.isNotBlank()) {
                missing += nameInsert(name, rawContactId)
            }
            if ((results.getOrNull(1)?.count ?: 0) == 0) {
                missing += numberInsert(number, rawContactId)
            }
            if (missing.isNotEmpty()) {
                context.contentResolver.applyBatch(ContactsContract.AUTHORITY, missing)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Removes the contact from the phone, with every number it carried. */
    suspend fun delete(context: Context, contact: Contact): Boolean =
        withContext(Dispatchers.IO) {
            if (!hasWritePermission(context)) return@withContext false
            try {
                context.contentResolver.delete(
                    ContactsContract.Contacts.CONTENT_URI,
                    ContactsContract.Contacts._ID + " = ?",
                    arrayOf(contact.id.toString())
                ) > 0
            } catch (e: Exception) {
                false
            }
        }

    /** The raw contact behind an aggregated one, needed to add a missing row. */
    @SuppressLint("MissingPermission")
    private fun rawContactIdOf(context: Context, contactId: Long): Long? = try {
        context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID),
            ContactsContract.RawContacts.CONTACT_ID + " = ?",
            arrayOf(contactId.toString()),
            null
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }
    } catch (e: Exception) {
        null
    }

    private fun nameInsert(name: String, rawContactId: Long? = null) =
        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .apply {
                if (rawContactId == null) {
                    withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                } else {
                    withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                }
            }
            .withValue(
                ContactsContract.Data.MIMETYPE,
                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
            )
            .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
            .build()

    private fun numberInsert(number: String, rawContactId: Long? = null) =
        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .apply {
                if (rawContactId == null) {
                    withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                } else {
                    withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                }
            }
            .withValue(
                ContactsContract.Data.MIMETYPE,
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
            )
            .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, number)
            .withValue(
                ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
            )
            .build()

    // ---------------------------------------------------------------- searching

    /**
     * Name and T9-like search: matches the name, the number, or the digits typed
     * on the keypad ("226" finds "Bao" through the letters of the keys).
     *
     * Prefer [ContactIndex] on a screen that searches while the user types: this
     * one lowercases and re-encodes every contact at each call.
     */
    fun search(contacts: List<Contact>, query: String): List<Contact> =
        ContactIndex(contacts).search(query)

    internal fun t9(name: String): String = buildString {
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

/**
 * A contact list prepared for search.
 *
 * Lowercasing the names and encoding them the way a keypad does costs the same
 * whether one letter or ten have been typed — so it is done once, when the list
 * arrives, instead of at every keystroke. On a phone holding a few thousand
 * contacts that is the difference between a smooth field and a stuttering one.
 */
class ContactIndex(val contacts: List<Contact>) {

    private class Entry(
        val contact: Contact,
        val name: String,
        val digits: String,
        val keypad: String
    )

    private val entries: List<Entry> = contacts.map { contact ->
        Entry(
            contact = contact,
            name = contact.name.lowercase(),
            digits = NumberUtils.sanitize(contact.number),
            keypad = ContactsRepository.t9(contact.name)
        )
    }

    fun search(query: String): List<Contact> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return contacts
        val lower = trimmed.lowercase()
        val digits = trimmed.filter { it.isDigit() }
        return entries
            .filter { entry ->
                entry.name.contains(lower) ||
                    (digits.isNotEmpty() && entry.digits.contains(digits)) ||
                    (digits.isNotEmpty() && entry.keypad.contains(digits))
            }
            .map { it.contact }
    }
}
