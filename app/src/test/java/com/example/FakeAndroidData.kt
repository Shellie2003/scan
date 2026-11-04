package com.example

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

/**
 * The rows a telephone would hand over, for a machine that has no telephone.
 *
 * The screens read their contacts, their messages and their call history from
 * the providers of Android. On a build machine those providers answer nothing,
 * and every list comes out empty — which is exactly why the first pictures of
 * the real screens were useless.
 *
 * Rather than one fake per provider, this answers any query by looking at the
 * columns it was asked for and filling each one from its name. A repository
 * that asks for `display_name` gets a name, one that asks for `address` gets a
 * number, one that asks for `body` gets a message. It does not matter which
 * provider is being imitated, nor which projection the caller chose — and the
 * repositories choose different ones.
 */
class FakeAndroidData : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val columns = projection ?: arrayOf("_id")
        val cursor = MatrixCursor(columns.map { it }.toTypedArray())
        PEOPLE.forEachIndexed { index, person ->
            cursor.addRow(columns.map { column -> value(column, index, person) })
        }
        return cursor
    }

    /** What a column of that name is worth, for the row of that person. */
    private fun value(column: String, index: Int, person: Person): Any? {
        val name = column.substringAfterLast('.').lowercase()
        return when {
            name == "_id" || name.endsWith("_id") && name != "thread_id" -> (index + 1).toLong()
            name == "thread_id" -> (index + 1).toLong()
            name.contains("display_name") || name == "name" -> person.name
            name.contains("number") || name == "address" || name == "data1" -> person.number
            name == "body" || name.contains("snippet") -> person.message
            name == "date" || name.contains("date") || name.contains("time") -> person.at
            name == "starred" -> if (index == 0) 1L else 0L
            name == "type" -> person.type
            name == "read" || name == "seen" -> 1L
            name.contains("photo") || name.contains("thumbnail") -> null
            name == "duration" -> 42L
            name == "subscription_id" || name == "sub_id" -> 1L
            name == "mimetype" -> "vnd.android.cursor.item/phone_v2"
            else -> null
        }
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, s: String?, a: Array<out String>?): Int = 0

    data class Person(
        val name: String,
        val number: String,
        val message: String,
        val at: Long,
        val type: Long
    )

    companion object {
        /** Names and numbers of here, so the pictures look like Madagascar. */
        val PEOPLE = listOf(
            Person("Rakoto", "0341234567", "Tonga ve ny vola?", 1_762_000_000_000L, 1L),
            Person("Naivo", "0321112233", "Misaotra betsaka!", 1_761_990_000_000L, 2L),
            Person("Soa", "0334445566", "Rahampitso ihany", 1_761_900_000_000L, 1L),
            Person("Hery", "0347778899", "Azafady fa hadino", 1_761_800_000_000L, 3L),
            Person("Fara", "0383334455", "Efa nalefako ny kaody", 1_761_700_000_000L, 2L)
        )

        /** Every authority the screens of the application read from. */
        val AUTHORITIES = listOf(
            "com.android.contacts",
            "sms",
            "mms",
            "mms-sms",
            "call_log"
        )
    }
}
