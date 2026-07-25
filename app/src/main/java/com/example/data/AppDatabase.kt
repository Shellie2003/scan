package com.example.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.MessageTemplate
import com.example.data.model.Offer
import com.example.data.model.Operator
import com.example.data.model.ScanHistory
import com.example.data.model.SimProfile

@Database(
    entities = [
        Operator::class,
        ScanHistory::class,
        Offer::class,
        SimProfile::class,
        MessageTemplate::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun operatorDao(): OperatorDao
    abstract fun scanHistoryDao(): ScanHistoryDao
    abstract fun offerDao(): OfferDao
    abstract fun simProfileDao(): SimProfileDao
    abstract fun messageTemplateDao(): MessageTemplateDao
}

/**
 * v1 held only the operator name/template and a bare scan history.
 * v2 makes every dialable code editable and adds plans, SIM profiles and message templates.
 * The migration is written by hand so users keep the operators they had configured.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE operators ADD COLUMN codeLength INTEGER NOT NULL DEFAULT 14")
        db.execSQL("ALTER TABLE operators ADD COLUMN balanceTemplate TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE operators ADD COLUMN transferTemplate TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE operators ADD COLUMN shareTemplate TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE operators ADD COLUMN serviceNumber TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE operators ADD COLUMN prefixes TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE operators ADD COLUMN keywords TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE operators ADD COLUMN colorHex TEXT NOT NULL DEFAULT '#607D8B'")
        db.execSQL("ALTER TABLE operators ADD COLUMN accentHex TEXT NOT NULL DEFAULT '#FFFFFF'")
        db.execSQL("ALTER TABLE operators ADD COLUMN stripUssdSymbols INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE operators ADD COLUMN templatesVerified INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE operators ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")

        db.execSQL("ALTER TABLE scan_history ADD COLUMN operatorName TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE scan_history ADD COLUMN dialedCode TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE scan_history ADD COLUMN subscriptionId INTEGER NOT NULL DEFAULT -1")
        db.execSQL("ALTER TABLE scan_history ADD COLUMN simLabel TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE scan_history ADD COLUMN kind TEXT NOT NULL DEFAULT 'recharge'")
        db.execSQL("ALTER TABLE scan_history ADD COLUMN label TEXT NOT NULL DEFAULT ''")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `offers` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`operatorName` TEXT NOT NULL, " +
                "`category` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`price` TEXT NOT NULL, " +
                "`description` TEXT NOT NULL, " +
                "`validity` TEXT NOT NULL, " +
                "`ussdCode` TEXT NOT NULL, " +
                "`shareCode` TEXT NOT NULL DEFAULT '', " +
                "`isFavorite` INTEGER NOT NULL DEFAULT 0, " +
                "`isCustom` INTEGER NOT NULL DEFAULT 0, " +
                "`sortOrder` INTEGER NOT NULL DEFAULT 0)"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sim_profiles` (" +
                "`subscriptionId` INTEGER NOT NULL, " +
                "`label` TEXT NOT NULL DEFAULT '', " +
                "`operatorName` TEXT NOT NULL DEFAULT '', " +
                "`phoneNumber` TEXT NOT NULL DEFAULT '', " +
                "`isPreferred` INTEGER NOT NULL DEFAULT 0, " +
                "`enabled` INTEGER NOT NULL DEFAULT 1, " +
                "PRIMARY KEY(`subscriptionId`))"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `message_templates` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`body` TEXT NOT NULL, " +
                "`sortOrder` INTEGER NOT NULL DEFAULT 0)"
        )
    }
}
