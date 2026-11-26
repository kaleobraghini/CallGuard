package com.callguard.whitelist.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

enum class Decision { ALLOW, REJECT }

/** O que fazer quando a whitelist não pode ser consultada (erro ou tempo esgotado). */
enum class FailurePolicy { ALLOW, REJECT }

/** O que fazer com chamadas de número privado/oculto/indisponível, quando o Android as encaminha. */
enum class HiddenPolicy { ALLOW, REJECT }

enum class DecisionReason {
    WHITELISTED,
    NOT_WHITELISTED,
    PROTECTION_OFF,
    EMPTY_WHITELIST,
    HIDDEN_ALLOWED,
    HIDDEN_REJECTED,
    LOOKUP_FAILED_ALLOWED,
    LOOKUP_FAILED_REJECTED,
    SETTINGS_UNAVAILABLE,
    /** Número na lista de bloqueados (por você ou automaticamente). */
    BLOCKED,
}

data class EngineSettings(
    val protectionEnabled: Boolean,
    val failurePolicy: FailurePolicy,
    val hiddenPolicy: HiddenPolicy,
    val normalizer: NormalizerConfig,
)

data class DecisionResult(
    val decision: Decision,
    val reason: DecisionReason,
    val normalized: String?,
)

/**
 * Fonte de consulta do motor. Recebe SOMENTE chaves numéricas: nomes, categorias e
 * observações não existem nesta interface, por construção.
 */
interface WhitelistLookup {
    suspend fun isEmpty(): Boolean
    suspend fun containsAny(keys: Set<String>): Boolean
}

/** Lista de bloqueados: números sempre rejeitados, mesmo com a proteção desligada. */
interface BlocklistLookup {
    suspend fun containsAny(keys: Set<String>): Boolean
}

/**
 * Motor de decisão. Independente de Android e da interface.
 * O número telefônico é o único critério de autorização.
 */
class CallDecisionEngine(
    private val lookup: WhitelistLookup,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val blocklist: BlocklistLookup? = null,
) {

    suspend fun decide(rawNumber: String?, numberHidden: Boolean, settings: EngineSettings): DecisionResult {
        val keys = PhoneNormalizer.candidateKeys(rawNumber, settings.normalizer)
        val canonical = keys.firstOrNull()

        // Número oculto/indisponível: não é uma falha de consulta nem um "número não autorizado".
        if (numberHidden || keys.isEmpty()) {
            if (!settings.protectionEnabled) return DecisionResult(Decision.ALLOW, DecisionReason.PROTECTION_OFF, null)
            return when (settings.hiddenPolicy) {
                HiddenPolicy.ALLOW -> DecisionResult(Decision.ALLOW, DecisionReason.HIDDEN_ALLOWED, null)
                HiddenPolicy.REJECT -> DecisionResult(Decision.REJECT, DecisionReason.HIDDEN_REJECTED, null)
            }
        }

        // Bloqueados valem sempre (também com a proteção desligada), a não ser que o número esteja na whitelist.
        val blocked: Boolean? = try {
            withTimeoutOrNull(timeoutMs) { blocklist?.containsAny(keys) ?: false }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

        if (!settings.protectionEnabled) {
            if (blocked == true) {
                val whitelisted = runCatching { withTimeoutOrNull(timeoutMs) { lookup.containsAny(keys) } }.getOrNull() == true
                if (!whitelisted) return DecisionResult(Decision.REJECT, DecisionReason.BLOCKED, canonical)
            }
            return DecisionResult(Decision.ALLOW, DecisionReason.PROTECTION_OFF, canonical)
        }

        val outcome: LookupOutcome = try {
            withTimeoutOrNull(timeoutMs) {
                when {
                    lookup.isEmpty() -> if (blocked == true) LookupOutcome.Blocked else LookupOutcome.Empty
                    lookup.containsAny(keys) -> LookupOutcome.Found
                    blocked == true -> LookupOutcome.Blocked
                    else -> LookupOutcome.NotFound
                }
            } ?: LookupOutcome.Failed // tempo esgotado
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LookupOutcome.Failed // banco indisponível, disco cheio, etc.
        }

        return when (outcome) {
            LookupOutcome.Found -> DecisionResult(Decision.ALLOW, DecisionReason.WHITELISTED, canonical)
            LookupOutcome.NotFound -> DecisionResult(Decision.REJECT, DecisionReason.NOT_WHITELISTED, canonical)
            LookupOutcome.Blocked -> DecisionResult(Decision.REJECT, DecisionReason.BLOCKED, canonical)
            // Whitelist vazia: não bloqueia tudo por acidente.
            LookupOutcome.Empty -> DecisionResult(Decision.ALLOW, DecisionReason.EMPTY_WHITELIST, canonical)
            LookupOutcome.Failed -> when (settings.failurePolicy) {
                FailurePolicy.ALLOW -> DecisionResult(Decision.ALLOW, DecisionReason.LOOKUP_FAILED_ALLOWED, canonical)
                FailurePolicy.REJECT -> DecisionResult(Decision.REJECT, DecisionReason.LOOKUP_FAILED_REJECTED, canonical)
            }
        }
    }

    private enum class LookupOutcome { Found, NotFound, Empty, Blocked, Failed }

    companion object {
        /** O Android dá 5 s; respondemos bem antes disso mesmo no pior caso. */
        const val DEFAULT_TIMEOUT_MS = 3_000L
    }
}
