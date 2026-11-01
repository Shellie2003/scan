package com.example.data

import android.content.Context
import com.example.data.model.MessageTemplate
import com.example.data.model.Offer
import com.example.data.model.Operator
import com.example.data.model.SimProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Everything the user typed, in one file they own.
 *
 * Uninstalling the application — which a signature conflict can force — takes
 * the operator codes, the plans created by hand, the nicknames of the SIM cards
 * and the message templates with it. None of that comes back on its own, and
 * retyping a withdrawal code from memory is how money ends up at the wrong
 * number. So it can be written out, and read back.
 *
 * What is deliberately **not** in the file: the code of the locked discussions
 * and the list of discussions it protects. A backup travels — through a
 * messaging app, a memory card, a borrowed computer — and a lock that travels
 * with it is not a lock. The history of the scans is not saved either: it is a
 * log, not a setting.
 */
object SettingsBackup {

    /** Written into the file, so an older application can refuse a newer file. */
    private const val VERSION = 1

    private const val KEY_VERSION = "version"
    private const val KEY_CREATED = "created"
    private const val KEY_OPERATORS = "operators"
    private const val KEY_OFFERS = "offers"
    private const val KEY_TEMPLATES = "templates"
    private const val KEY_SIMS = "sims"
    private const val KEY_SETTINGS = "settings"
    private const val KEY_BLOCKED = "blocked"

    /** What an import changed, to be able to say it rather than claim it. */
    data class Report(
        val operators: Int = 0,
        val offers: Int = 0,
        val templates: Int = 0,
        val sims: Int = 0,
        val blocked: Int = 0,
        val failed: Boolean = false
    ) {
        val total: Int get() = operators + offers + templates + sims + blocked
    }

    // ------------------------------------------------------------------ writing

    suspend fun export(context: Context): String = withContext(Dispatchers.IO) {
        val database = DatabaseProvider.getDatabase(context)
        val root = JSONObject()
        root.put(KEY_VERSION, VERSION)
        root.put(KEY_CREATED, System.currentTimeMillis())

        val operators = JSONArray()
        database.operatorDao().getAllOperatorsOnce().forEach { operators.put(it.toJson()) }
        root.put(KEY_OPERATORS, operators)

        val offers = JSONArray()
        database.offerDao().getCustomOnce().forEach { offers.put(it.toJson()) }
        root.put(KEY_OFFERS, offers)

        val templates = JSONArray()
        database.messageTemplateDao().getAllOnce().forEach { templates.put(it.toJson()) }
        root.put(KEY_TEMPLATES, templates)

        val sims = JSONArray()
        database.simProfileDao().getAllOnce().forEach { sims.put(it.toJson()) }
        root.put(KEY_SIMS, sims)

        root.put(KEY_SETTINGS, settingsToJson(context))

        val blocked = JSONArray()
        BlockedNumbers.all(context).forEach { blocked.put(it) }
        root.put(KEY_BLOCKED, blocked)

        root.toString(2)
    }

    // ------------------------------------------------------------------ reading

    /**
     * Reads a file back, adding to what is there rather than replacing it.
     *
     * An operator of the same name is updated, keeping its row; a template of
     * the same title is left alone; a SIM profile belongs to a subscription id
     * and is written over. Nothing is deleted: a backup is there to give back,
     * not to take away.
     */
    suspend fun import(context: Context, text: String): Report = withContext(Dispatchers.IO) {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            return@withContext Report(failed = true)
        }
        if (root.optInt(KEY_VERSION, 0) > VERSION) return@withContext Report(failed = true)

        val database = DatabaseProvider.getDatabase(context)
        var operators = 0
        var offers = 0
        var templates = 0
        var sims = 0
        var blocked = 0

        try {
            val existingOperators = database.operatorDao().getAllOperatorsOnce()
            root.optJSONArray(KEY_OPERATORS)?.forEachObject { json ->
                val operator = json.toOperator() ?: return@forEachObject
                val current = existingOperators.firstOrNull {
                    it.name.equals(operator.name, ignoreCase = true)
                }
                if (current == null) {
                    database.operatorDao().insertOperator(operator)
                } else {
                    database.operatorDao().updateOperator(operator.copy(id = current.id))
                }
                operators++
            }

            val existingOffers = database.offerDao().getCustomOnce()
            root.optJSONArray(KEY_OFFERS)?.forEachObject { json ->
                val offer = json.toOffer() ?: return@forEachObject
                val known = existingOffers.any {
                    it.title.equals(offer.title, ignoreCase = true) &&
                        it.operatorName.equals(offer.operatorName, ignoreCase = true)
                }
                if (!known) {
                    database.offerDao().insert(offer)
                    offers++
                }
            }

            val existingTemplates = database.messageTemplateDao().getAllOnce()
            root.optJSONArray(KEY_TEMPLATES)?.forEachObject { json ->
                val template = json.toTemplate() ?: return@forEachObject
                val known = existingTemplates.any {
                    it.title.equals(template.title, ignoreCase = true)
                }
                if (!known) {
                    database.messageTemplateDao().insert(template)
                    templates++
                }
            }

            root.optJSONArray(KEY_SIMS)?.forEachObject { json ->
                val profile = json.toSimProfile() ?: return@forEachObject
                database.simProfileDao().upsert(profile)
                sims++
            }

            root.optJSONObject(KEY_SETTINGS)?.let { settingsFromJson(context, it) }

            root.optJSONArray(KEY_BLOCKED)?.let { array ->
                for (index in 0 until array.length()) {
                    val number = array.optString(index).orEmpty()
                    if (number.isNotBlank()) {
                        BlockedNumbers.block(context, number)
                        blocked++
                    }
                }
            }
        } catch (e: Exception) {
            // Whatever was read before the failure stays: a partial restore is
            // better than none, and the report says how much came back.
            return@withContext Report(operators, offers, templates, sims, blocked, failed = true)
        }

        Report(operators, offers, templates, sims, blocked)
    }

    // ----------------------------------------------------------------- entities

    private fun Operator.toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("template", template)
        put("codeLength", codeLength)
        put("balanceTemplate", balanceTemplate)
        put("transferTemplate", transferTemplate)
        put("shareTemplate", shareTemplate)
        put("withdrawTemplate", withdrawTemplate)
        put("serviceNumber", serviceNumber)
        put("prefixes", prefixes)
        put("keywords", keywords)
        put("colorHex", colorHex)
        put("accentHex", accentHex)
        put("stripUssdSymbols", stripUssdSymbols)
        put("templatesVerified", templatesVerified)
        put("sortOrder", sortOrder)
        put("isDefault", isDefault)
    }

    private fun JSONObject.toOperator(): Operator? {
        val name = optString("name").orEmpty()
        if (name.isBlank()) return null
        return Operator(
            name = name,
            template = optString("template").orEmpty(),
            isDefault = optBoolean("isDefault", false),
            codeLength = optInt("codeLength", 14),
            balanceTemplate = optString("balanceTemplate").orEmpty(),
            transferTemplate = optString("transferTemplate").orEmpty(),
            shareTemplate = optString("shareTemplate").orEmpty(),
            withdrawTemplate = optString("withdrawTemplate").orEmpty(),
            serviceNumber = optString("serviceNumber").orEmpty(),
            prefixes = optString("prefixes").orEmpty(),
            keywords = optString("keywords").orEmpty(),
            colorHex = optString("colorHex").ifBlank { "#607D8B" },
            accentHex = optString("accentHex").ifBlank { "#FFFFFF" },
            stripUssdSymbols = optBoolean("stripUssdSymbols", false),
            templatesVerified = optBoolean("templatesVerified", false),
            sortOrder = optInt("sortOrder", 0)
        )
    }

    private fun Offer.toJson(): JSONObject = JSONObject().apply {
        put("operatorName", operatorName)
        put("category", category)
        put("title", title)
        put("price", price)
        put("description", description)
        put("validity", validity)
        put("ussdCode", ussdCode)
        put("shareCode", shareCode)
        put("isFavorite", isFavorite)
        put("sortOrder", sortOrder)
    }

    private fun JSONObject.toOffer(): Offer? {
        val title = optString("title").orEmpty()
        val code = optString("ussdCode").orEmpty()
        if (title.isBlank() || code.isBlank()) return null
        return Offer(
            operatorName = optString("operatorName").orEmpty(),
            category = optString("category").ifBlank { DefaultData.CATEGORY_INTERNET },
            title = title,
            price = optString("price").orEmpty(),
            description = optString("description").orEmpty(),
            validity = optString("validity").orEmpty(),
            ussdCode = code,
            shareCode = optString("shareCode").orEmpty(),
            isFavorite = optBoolean("isFavorite", false),
            isCustom = true,
            sortOrder = optInt("sortOrder", 0)
        )
    }

    private fun MessageTemplate.toJson(): JSONObject = JSONObject().apply {
        put("title", title)
        put("body", body)
        put("sortOrder", sortOrder)
    }

    private fun JSONObject.toTemplate(): MessageTemplate? {
        val title = optString("title").orEmpty()
        val body = optString("body").orEmpty()
        if (title.isBlank() && body.isBlank()) return null
        return MessageTemplate(title = title, body = body, sortOrder = optInt("sortOrder", 0))
    }

    private fun SimProfile.toJson(): JSONObject = JSONObject().apply {
        put("subscriptionId", subscriptionId)
        put("label", label)
        put("operatorName", operatorName)
        put("phoneNumber", phoneNumber)
        put("isPreferred", isPreferred)
        put("enabled", enabled)
    }

    private fun JSONObject.toSimProfile(): SimProfile? {
        if (!has("subscriptionId")) return null
        return SimProfile(
            subscriptionId = optInt("subscriptionId", AppSettings.NO_SUBSCRIPTION),
            label = optString("label").orEmpty(),
            operatorName = optString("operatorName").orEmpty(),
            phoneNumber = optString("phoneNumber").orEmpty(),
            isPreferred = optBoolean("isPreferred", false),
            enabled = optBoolean("enabled", true)
        )
    }

    // ----------------------------------------------------------------- settings

    private fun settingsToJson(context: Context): JSONObject = JSONObject().apply {
        put("smartSimRouting", AppSettings.smartSimRouting(context))
        put("alwaysAskSim", AppSettings.alwaysAskSim(context))
        put("confirmBeforeDial", AppSettings.confirmBeforeDial(context))
        put("inAppUssd", AppSettings.inAppUssd(context))
        put("autoValidation", AppSettings.autoValidation(context))
        put("bulkSmsDelay", AppSettings.bulkSmsDelayMs(context))
        put("themeMode", AppSettings.themeMode(context))
        put("dynamicColors", AppSettings.dynamicColors(context))
        put("messageSound", AppSettings.messageSound(context))
        put("defaultCallSub", AppSettings.defaultCallSubscription(context))
        put("defaultSmsSub", AppSettings.defaultSmsSubscription(context))
    }

    private fun settingsFromJson(context: Context, json: JSONObject) {
        if (json.has("smartSimRouting")) {
            AppSettings.setSmartSimRouting(context, json.optBoolean("smartSimRouting", true))
        }
        if (json.has("alwaysAskSim")) {
            AppSettings.setAlwaysAskSim(context, json.optBoolean("alwaysAskSim", false))
        }
        if (json.has("confirmBeforeDial")) {
            AppSettings.setConfirmBeforeDial(context, json.optBoolean("confirmBeforeDial", true))
        }
        if (json.has("inAppUssd")) {
            AppSettings.setInAppUssd(context, json.optBoolean("inAppUssd", true))
        }
        if (json.has("autoValidation")) {
            AppSettings.setAutoValidation(context, json.optBoolean("autoValidation", false))
        }
        if (json.has("bulkSmsDelay")) {
            AppSettings.setBulkSmsDelayMs(context, json.optLong("bulkSmsDelay", 400L))
        }
        if (json.has("themeMode")) {
            AppSettings.setThemeMode(context, json.optString("themeMode").ifBlank { "system" })
        }
        if (json.has("dynamicColors")) {
            AppSettings.setDynamicColors(context, json.optBoolean("dynamicColors", false))
        }
        if (json.has("messageSound")) {
            AppSettings.setMessageSound(context, json.optString("messageSound").orEmpty())
        }
        if (json.has("defaultCallSub")) {
            AppSettings.setDefaultCallSubscription(
                context,
                json.optInt("defaultCallSub", AppSettings.NO_SUBSCRIPTION)
            )
        }
        if (json.has("defaultSmsSub")) {
            AppSettings.setDefaultSmsSubscription(
                context,
                json.optInt("defaultSmsSub", AppSettings.NO_SUBSCRIPTION)
            )
        }
    }

    /** The name the file is offered under, with the day in it. */
    fun suggestedFileName(): String {
        val day = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date())
        return "scan-sauvegarde-$day.json"
    }

    private inline fun JSONArray.forEachObject(action: (JSONObject) -> Unit) {
        for (index in 0 until length()) {
            optJSONObject(index)?.let(action)
        }
    }
}
