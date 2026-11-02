package com.example.telephony

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentProviderOperation
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
        mergeDuplicates(result)
    }

    /**
     * One row per person, when the address book holds several for them.
     *
     * The key above — the identifier of the contact and the number — only
     * catches the same number repeated inside one contact. It cannot catch the
     * same person held twice: a phone here carries the numbers of the SIM card
     * and those of a Google account side by side, and Android links the two
     * only when it is sure. Unlinked, they are two contacts with two
     * identifiers, the same name and the same number — which is what showed up
     * twice in the search.
     *
     * Two rows are the same person when the number is the same and the names
     * do not contradict each other. A name absent on one side, the same name,
     * or one that only adds words to the other — "Rakoto" against "Rakoto
     * Jean" — is one person. Two names that differ are two people, and a number
     * shared by a household must not make one of them disappear.
     */
    internal fun mergeDuplicates(contacts: List<Contact>): List<Contact> {
        val kept = mutableListOf<Contact>()
        val slotsByNumber = HashMap<String, MutableList<Int>>()
        contacts.forEach { contact ->
            val slots = slotsByNumber.getOrPut(numberKey(contact.number)) { mutableListOf() }
            val at = slots.firstOrNull { samePerson(kept[it].name, contact.name) }
            if (at == null) {
                slots.add(kept.size)
                kept.add(contact)
            } else {
                kept[at] = merged(kept[at], contact)
            }
        }
        return kept
    }

    /** The form a number is recognised by, whatever way it was written. */
    private fun numberKey(number: String): String =
        NumberUtils.toNational(number).ifBlank { number.filter { !it.isWhitespace() } }

    /** Whether two names found on one number belong to one person. */
    internal fun samePerson(left: String, right: String): Boolean {
        val one = normaliseName(left)
        val other = normaliseName(right)
        if (one.isBlank() || other.isBlank()) return true
        if (one == other) return true
        // A whole word more, not a longer word: "Jean" is not "Jeanne".
        return one.startsWith("$other ") || other.startsWith("$one ")
    }

    private fun normaliseName(name: String): String =
        name.trim().replace(Regex("\\s+"), " ").lowercase()

    /**
     * What is kept of two rows for one person.
     *
     * The fuller name wins: an entry on a SIM card is often an abbreviation of
     * the one held in an account. A photograph and a star are kept from
     * whichever side carries them — the star especially, because it is read
     * from one side and written to the other.
     */
    private fun merged(kept: Contact, other: Contact): Contact = kept.copy(
        name = if (other.name.trim().length > kept.name.trim().length) other.name else kept.name,
        photoUri = kept.photoUri ?: other.photoUri,
        isFavorite = kept.isFavorite || other.isFavorite
    )

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
     * Name and picture of a number, in one question to the provider.
     *
     * An incoming call asked for the name, and then the picture would have
     * asked again: one lookup is what a ringing phone can afford.
     */
    @SuppressLint("MissingPermission")
    suspend fun contactFor(context: Context, number: String): Pair<String?, String?> =
        withContext(Dispatchers.IO) {
            if (!hasPermission(context) || number.isBlank()) return@withContext null to null
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number)
            )
            try {
                context.contentResolver.query(
                    uri,
                    arrayOf(
                        ContactsContract.PhoneLookup.DISPLAY_NAME,
                        ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI
                    ),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        return@withContext cursor.getString(0) to cursor.getString(1)
                    }
                }
            } catch (e: Exception) {
                return@withContext null to null
            }
            null to null
        }

    // ------------------------------------------------------------------ writing

    /**
     * Creates a contact on the phone itself.
     *
     * The raw contact is written without an account, which is what the system
     * calls a device contact: it belongs to the phone, not to a synchronised
     * account the user may not have.
     */
    suspend fun create(
        context: Context,
        name: String,
        number: String,
        picture: ByteArray? = null
    ): Boolean = withContext(Dispatchers.IO) {
            if (!hasWritePermission(context) || number.isBlank()) return@withContext false
            val operations = arrayListOf(
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                    .build(),
                nameInsert(name),
                numberInsert(number)
            )
            if (picture != null) operations += pictureInsert(picture)
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

    /**
     * Stars a contact, or takes the star away.
     *
     * The favourites tab of the app reads the star of the phone itself, so a
     * contact starred here is starred everywhere — and the tab stops being a
     * list nothing could ever be added to.
     */
    suspend fun setFavorite(context: Context, contact: Contact, favorite: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            if (!hasWritePermission(context)) return@withContext false
            try {
                val values = android.content.ContentValues().apply {
                    put(ContactsContract.Contacts.STARRED, if (favorite) 1 else 0)
                }
                // The identifier in the address, for the same reason as the
                // deletion below: a condition on the whole table is the form
                // the provider is least willing to accept.
                context.contentResolver.update(
                    android.content.ContentUris.withAppendedId(
                        ContactsContract.Contacts.CONTENT_URI,
                        contact.id
                    ),
                    values,
                    null,
                    null
                ) > 0
            } catch (e: Exception) {
                false
            }
        }

    /**
     * Removes the contact from the phone, with every number it carried.
     *
     * Two ways, because the first is not accepted everywhere. The identifier
     * belongs in the address itself: the provider of the contacts refuses a
     * deletion aimed at the whole table with a condition attached — it raised
     * an exception, the exception was caught, and the user was told the contact
     * could not be *saved* while it sat there undeleted.
     *
     * When that address deletes nothing, the raw contacts behind it are deleted
     * instead. A contact is an aggregate of rows from the accounts of the
     * telephone — the SIM card, a Google account — and on some telephones that
     * is the only level a deletion is allowed at.
     */
    suspend fun delete(context: Context, contact: Contact): Boolean =
        withContext(Dispatchers.IO) {
            if (!hasWritePermission(context)) return@withContext false
            val byId = try {
                context.contentResolver.delete(
                    android.content.ContentUris.withAppendedId(
                        ContactsContract.Contacts.CONTENT_URI,
                        contact.id
                    ),
                    null,
                    null
                ) > 0
            } catch (e: Exception) {
                false
            }
            if (byId) return@withContext true
            try {
                context.contentResolver.delete(
                    ContactsContract.RawContacts.CONTENT_URI,
                    ContactsContract.RawContacts.CONTACT_ID + " = ?",
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

    // ------------------------------------------------------------------- photo

    /** Small cache: a list scrolls past the same faces again and again. */
    private val thumbnails = android.util.LruCache<String, Bitmap>(80)

    /**
     * The picture of a contact, ready to be drawn.
     *
     * Contact thumbnails are tiny, but decoding one at every scroll frame is
     * not, so they are kept once read.
     */
    suspend fun thumbnail(context: Context, photoUri: String): Bitmap? =
        withContext(Dispatchers.IO) {
            thumbnails.get(photoUri)?.let { return@withContext it }
            try {
                context.contentResolver.openInputStream(Uri.parse(photoUri))?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }?.also { thumbnails.put(photoUri, it) }
            } catch (e: Exception) {
                null
            }
        }

    /**
     * Reads a picked image down to what a contact picture needs.
     *
     * A photo straight from the camera weighs several megabytes; the contacts
     * provider wants something small, and the screen never shows more than a
     * circle of a few hundred pixels.
     */
    suspend fun readPicture(context: Context, uri: Uri, maxSize: Int = 512): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                val largest = maxOf(bounds.outWidth, bounds.outHeight)
                var sample = 1
                while (largest / sample > maxSize) sample *= 2

                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                val bitmap = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                } ?: return@withContext null

                val bytes = java.io.ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, bytes)
                bitmap.recycle()
                bytes.toByteArray()
            } catch (e: Exception) {
                null
            }
        }

    /** Replaces the picture of a contact, or removes it when [picture] is null. */
    suspend fun setPicture(context: Context, contact: Contact, picture: ByteArray?): Boolean =
        withContext(Dispatchers.IO) {
            if (!hasWritePermission(context)) return@withContext false
            val rawContactId = rawContactIdOf(context, contact.id) ?: return@withContext false
            val operations = arrayListOf(
                ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                    .withSelection(
                        ContactsContract.Data.RAW_CONTACT_ID + " = ? AND " +
                            ContactsContract.Data.MIMETYPE + " = ?",
                        arrayOf(
                            rawContactId.toString(),
                            ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE
                        )
                    )
                    .build()
            )
            if (picture != null) operations += pictureInsert(picture, rawContactId)
            try {
                context.contentResolver.applyBatch(ContactsContract.AUTHORITY, operations)
                contact.photoUri?.let { thumbnails.remove(it) }
                true
            } catch (e: Exception) {
                false
            }
        }

    private fun pictureInsert(picture: ByteArray, rawContactId: Long? = null) =
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
                ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE
            )
            .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, picture)
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
