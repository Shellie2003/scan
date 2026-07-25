package com.example.telephony

import android.content.Context
import com.example.data.AppSettings
import com.example.data.model.Operator

enum class RoutingStatus {
    /** [SimRouting.sim] can be used right away. */
    READY,

    /** Several cards match: the user has to pick one. */
    NEEDS_CHOICE,

    /** Cards are present but none belongs to the operator of the operation. */
    NO_SIM_FOR_OPERATOR,

    /** No SIM at all, or the phone state permission is missing. */
    NO_SIM
}

/**
 * Which SIM card an operation will go through, and why.
 */
data class SimRouting(
    val operator: Operator?,
    val sim: SimCard?,
    val candidates: List<SimCard>,
    val status: RoutingStatus,
    /** `true` when [sim] does not belong to [operator] — an explicit fallback. */
    val isFallback: Boolean = false
) {
    val isReady: Boolean get() = status == RoutingStatus.READY && sim != null
}

/**
 * Chooses the SIM card an operation runs on.
 *
 * The rule that matters when a phone carries several cards of the same operator:
 * the app never silently guesses. One matching card is used directly, several
 * matching cards raise [RoutingStatus.NEEDS_CHOICE] unless the user has already
 * saved a preference for that operator, and the chosen card is always displayed
 * before the operation runs.
 */
object SimRouter {

    fun route(
        context: Context,
        operator: Operator?,
        sims: List<SimCard>,
        forcedSubscriptionId: Int = AppSettings.NO_SUBSCRIPTION
    ): SimRouting {
        val usable = sims.filter { it.enabled }

        if (forcedSubscriptionId != AppSettings.NO_SUBSCRIPTION) {
            val forced = usable.firstOrNull { it.subscriptionId == forcedSubscriptionId }
            if (forced != null) {
                return SimRouting(
                    operator = operator,
                    sim = forced,
                    candidates = usable,
                    status = RoutingStatus.READY,
                    isFallback = operator != null && !SimManager.matches(forced, operator)
                )
            }
        }

        if (usable.isEmpty()) {
            return SimRouting(operator, null, emptyList(), RoutingStatus.NO_SIM)
        }

        if (operator == null) {
            val preferred = usable.firstOrNull {
                it.subscriptionId == AppSettings.defaultCallSubscription(context)
            }
            return when {
                preferred != null && !AppSettings.alwaysAskSim(context) ->
                    SimRouting(null, preferred, usable, RoutingStatus.READY)
                usable.size == 1 ->
                    SimRouting(null, usable.first(), usable, RoutingStatus.READY)
                else ->
                    SimRouting(null, null, usable, RoutingStatus.NEEDS_CHOICE)
            }
        }

        val candidates = SimManager.simsForOperator(usable, operator)
        if (candidates.isEmpty()) {
            return SimRouting(operator, null, usable, RoutingStatus.NO_SIM_FOR_OPERATOR)
        }

        val remembered = candidates.firstOrNull {
            it.subscriptionId == AppSettings.preferredSubscription(context, operator.name)
        }
        return when {
            AppSettings.alwaysAskSim(context) && candidates.size > 1 ->
                SimRouting(operator, remembered, candidates, RoutingStatus.NEEDS_CHOICE)
            remembered != null ->
                SimRouting(operator, remembered, candidates, RoutingStatus.READY)
            candidates.size == 1 ->
                SimRouting(operator, candidates.first(), candidates, RoutingStatus.READY)
            else ->
                SimRouting(operator, null, candidates, RoutingStatus.NEEDS_CHOICE)
        }
    }

    /**
     * Routing for an operation aimed at [number]: the SIM of the same operator as
     * the destination is preferred, which keeps the call or the transfer on-net.
     */
    fun routeForNumber(
        context: Context,
        number: String,
        operators: List<Operator>,
        sims: List<SimCard>,
        forcedSubscriptionId: Int = AppSettings.NO_SUBSCRIPTION
    ): SimRouting {
        if (!AppSettings.smartSimRouting(context) && forcedSubscriptionId == AppSettings.NO_SUBSCRIPTION) {
            return route(context, null, sims)
        }
        val operator = NumberUtils.operatorForNumber(number, operators)
        val routing = route(context, operator, sims, forcedSubscriptionId)
        // No card of the destination operator: fall back to the default SIM but say so.
        if (routing.status == RoutingStatus.NO_SIM_FOR_OPERATOR) {
            val fallback = route(context, null, sims)
            return fallback.copy(
                operator = operator,
                isFallback = fallback.sim != null,
                status = if (fallback.sim != null) RoutingStatus.READY else fallback.status
            )
        }
        return routing
    }

    fun remember(context: Context, operator: Operator?, sim: SimCard) {
        if (operator != null) {
            AppSettings.setPreferredSubscription(context, operator.name, sim.subscriptionId)
        } else {
            AppSettings.setDefaultCallSubscription(context, sim.subscriptionId)
        }
    }
}
