package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppSettings
import com.example.data.DatabaseProvider
import com.example.data.DefaultData
import com.example.data.model.MessageTemplate
import com.example.data.model.Offer
import com.example.data.model.Operator
import com.example.data.model.ScanHistory
import com.example.data.model.SimProfile
import com.example.telephony.SimCard
import com.example.telephony.SimManager
import com.example.telephony.SimRouting
import com.example.telephony.SimRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Single source of truth for everything the user can configure: operators,
 * plans, SIM profiles, message templates and the operation history.
 *
 * All the screens share one instance, so an edit made in the settings is visible
 * on the scanner and on the plans list without any refresh.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val database = DatabaseProvider.getDatabase(application)
    private val operatorDao = database.operatorDao()
    private val offerDao = database.offerDao()
    private val historyDao = database.scanHistoryDao()
    private val simProfileDao = database.simProfileDao()
    private val templateDao = database.messageTemplateDao()

    val operators: StateFlow<List<Operator>> = operatorDao.getAllOperators()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val offers: StateFlow<List<Offer>> = offerDao.getAllOffers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val history: StateFlow<List<ScanHistory>> = historyDao.getAllHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val messageTemplates: StateFlow<List<MessageTemplate>> = templateDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val simProfiles: StateFlow<List<SimProfile>> = simProfileDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _sims = MutableStateFlow<List<SimCard>>(emptyList())

    /** SIM cards currently in the phone, enriched with the profiles of the user. */
    val sims: StateFlow<List<SimCard>> = _sims.asStateFlow()

    init {
        refreshSims()
        viewModelScope.launch {
            simProfiles.collect { profiles ->
                _sims.value = SimManager.getSims(getApplication<Application>(), profiles)
            }
        }
    }

    fun refreshSims() {
        viewModelScope.launch {
            val profiles = runCatching { simProfileDao.getAllOnce() }.getOrDefault(emptyList())
            _sims.value = SimManager.getSims(getApplication<Application>(), profiles)
        }
    }

    // ------------------------------------------------------------------ routing

    /** Which SIM an operation for [operator] will use. */
    fun routeFor(operator: Operator?, forcedSubscriptionId: Int = AppSettings.NO_SUBSCRIPTION): SimRouting =
        SimRouter.route(getApplication<Application>(), operator, _sims.value, forcedSubscriptionId)

    /** Which SIM an operation aimed at [number] will use. */
    fun routeForNumber(
        number: String,
        forcedSubscriptionId: Int = AppSettings.NO_SUBSCRIPTION
    ): SimRouting = SimRouter.routeForNumber(
        getApplication<Application>(),
        number,
        operators.value,
        _sims.value,
        forcedSubscriptionId
    )

    fun operatorNamed(name: String?): Operator? =
        name?.let { value -> operators.value.firstOrNull { it.name.equals(value, ignoreCase = true) } }

    // ---------------------------------------------------------------- operators

    fun saveOperator(operator: Operator) {
        viewModelScope.launch {
            if (operator.id == 0) operatorDao.insertOperator(operator)
            else operatorDao.updateOperator(operator)
        }
    }

    fun renameOperator(operator: Operator, newName: String) {
        viewModelScope.launch {
            val previous = operator.name
            operatorDao.updateOperator(operator.copy(name = newName))
            if (!previous.equals(newName, ignoreCase = true)) {
                offerDao.renameOperator(previous, newName)
            }
        }
    }

    fun deleteOperator(operator: Operator) {
        viewModelScope.launch { operatorDao.deleteOperator(operator) }
    }

    fun restoreDefaultOperators() {
        viewModelScope.launch {
            val existing = operatorDao.getAllOperatorsOnce()
            DefaultData.operators.forEach { factory ->
                val current = existing.firstOrNull { it.name.equals(factory.name, ignoreCase = true) }
                if (current == null) operatorDao.insertOperator(factory)
                else operatorDao.updateOperator(factory.copy(id = current.id))
            }
        }
    }

    // ------------------------------------------------------------------- offers

    fun saveOffer(offer: Offer) {
        viewModelScope.launch {
            if (offer.id == 0) offerDao.insert(offer.copy(isCustom = true))
            else offerDao.update(offer)
        }
    }

    fun deleteOffer(offer: Offer) {
        viewModelScope.launch { offerDao.delete(offer) }
    }

    fun toggleFavorite(offer: Offer) {
        viewModelScope.launch { offerDao.update(offer.copy(isFavorite = !offer.isFavorite)) }
    }

    /** Puts back the plans shipped with the app, keeping the ones created by the user. */
    fun restoreDefaultOffers() {
        viewModelScope.launch {
            offerDao.deleteBuiltIn()
            offerDao.insertAll(DefaultData.offers)
        }
    }

    // ------------------------------------------------------------- sim profiles

    fun saveSimProfile(profile: SimProfile) {
        viewModelScope.launch {
            simProfileDao.upsert(profile)
            refreshSims()
        }
    }

    fun setPreferredSim(operator: Operator?, sim: SimCard) {
        SimRouter.remember(getApplication<Application>(), operator, sim)
        viewModelScope.launch {
            if (operator != null) {
                simProfileDao.clearPreferredFor(operator.name)
                val existing = simProfileDao.get(sim.subscriptionId)
                simProfileDao.upsert(
                    (existing ?: SimProfile(subscriptionId = sim.subscriptionId)).copy(
                        isPreferred = true
                    )
                )
            }
            refreshSims()
        }
    }

    // ---------------------------------------------------------------- templates

    fun saveTemplate(template: MessageTemplate) {
        viewModelScope.launch {
            if (template.id == 0) templateDao.insert(template) else templateDao.update(template)
        }
    }

    fun deleteTemplate(template: MessageTemplate) {
        viewModelScope.launch { templateDao.delete(template) }
    }

    // ------------------------------------------------------------------ history

    fun record(
        operator: Operator?,
        code: String,
        dialedCode: String,
        sim: SimCard?,
        kind: String,
        label: String = ""
    ) {
        viewModelScope.launch {
            historyDao.insertHistory(
                ScanHistory(
                    operatorId = operator?.id ?: 0,
                    operatorName = operator?.name.orEmpty(),
                    code = code,
                    dialedCode = dialedCode,
                    subscriptionId = sim?.subscriptionId ?: AppSettings.NO_SUBSCRIPTION,
                    simLabel = sim?.title.orEmpty(),
                    kind = kind,
                    label = label
                )
            )
        }
    }

    fun clearHistory() {
        viewModelScope.launch { historyDao.clear() }
    }

    /** `true` when the same recharge code was already sent in the last 24 hours. */
    suspend fun wasUsedRecently(code: String): Boolean =
        historyDao.countRecentUses(code, System.currentTimeMillis() - 24 * 60 * 60 * 1000L) > 0
}
