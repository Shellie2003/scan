package com.example.data

import android.content.Context

/**
 * Fills the database with the factory settings the first time, and carries over
 * the customisations made with the previous version of the app.
 */
object DataSeeder {

    private const val PREFS = "app_prefs"
    private const val KEY_LEGACY_IMPORTED = "legacy_offer_codes_imported"
    private const val LEGACY_PREFIX = "custom_ussd_"

    suspend fun seed(context: Context, database: AppDatabase) {
        seedOperators(database)
        seedOffers(database)
        seedMessageTemplates(database)
        importLegacyOfferCodes(context, database)
    }

    private suspend fun seedOperators(database: AppDatabase) {
        val dao = database.operatorDao()
        val existing = dao.getAllOperatorsOnce()
        if (existing.isEmpty()) {
            dao.insertAll(DefaultData.operators)
            return
        }
        // Operators created before the codes became editable only carry a name and a
        // recharge template: complete them with the factory metadata of the same name.
        existing.forEach { operator ->
            val factory = DefaultData.operators.find { it.name.equals(operator.name, ignoreCase = true) }
                ?: return@forEach
            if (operator.prefixes.isEmpty() && operator.keywords.isEmpty() && operator.balanceTemplate.isEmpty()) {
                dao.updateOperator(
                    operator.copy(
                        codeLength = factory.codeLength,
                        balanceTemplate = factory.balanceTemplate,
                        transferTemplate = factory.transferTemplate,
                        shareTemplate = factory.shareTemplate,
                        serviceNumber = factory.serviceNumber,
                        prefixes = factory.prefixes,
                        keywords = factory.keywords,
                        colorHex = factory.colorHex,
                        accentHex = factory.accentHex,
                        stripUssdSymbols = factory.stripUssdSymbols,
                        sortOrder = factory.sortOrder
                    )
                )
            }
        }
    }

    private suspend fun seedOffers(database: AppDatabase) {
        val dao = database.offerDao()
        if (dao.count() == 0) {
            dao.insertAll(DefaultData.offers)
        }
    }

    private suspend fun seedMessageTemplates(database: AppDatabase) {
        val dao = database.messageTemplateDao()
        if (dao.count() == 0) {
            dao.insertAll(DefaultData.messageTemplates)
        }
    }

    /**
     * Before plans lived in the database, an edited plan code was stored in the
     * preferences under `custom_ussd_<operator>_<category>_<title>`. Move those
     * edits into the plans themselves so nobody loses their setup.
     */
    private suspend fun importLegacyOfferCodes(context: Context, database: AppDatabase) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_LEGACY_IMPORTED, false)) return

        val legacy = prefs.all.entries
            .filter { it.key.startsWith(LEGACY_PREFIX) && it.value is String }
            .associate { it.key.removePrefix(LEGACY_PREFIX) to it.value as String }

        if (legacy.isNotEmpty()) {
            val dao = database.offerDao()
            val operators = database.operatorDao().getAllOperatorsOnce()
            operators.forEach { operator ->
                dao.getForOperator(operator.name).forEach { offer ->
                    val key = "${offer.operatorName}_${offer.category}_${offer.title}"
                    val custom = legacy[key]
                    if (!custom.isNullOrBlank() && custom != offer.ussdCode) {
                        dao.update(offer.copy(ussdCode = custom))
                    }
                }
            }
        }
        prefs.edit().putBoolean(KEY_LEGACY_IMPORTED, true).apply()
    }
}
