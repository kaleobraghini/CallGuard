package com.callguard.whitelist.screening

import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.TelecomManager
import android.util.Log
import com.callguard.whitelist.appContainer
import com.callguard.whitelist.core.Decision
import com.callguard.whitelist.core.DecisionReason
import com.callguard.whitelist.core.DecisionResult
import com.callguard.whitelist.data.AppSettings
import com.callguard.whitelist.data.CallEvent
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Serviço vinculado pelo próprio Android (Telecom) a cada chamada recebida que ele decide
 * encaminhar para filtragem. Não roda em segundo plano: só existe durante a análise.
 *
 * Limitações reais da API (sem ser o discador padrão):
 *  - Chamadas de números salvos na agenda NÃO são encaminhadas; elas tocam normalmente.
 *  - É preciso responder em até 5 s; o motor usa 3 s de limite e depois aplica a política de falha.
 *  - Números privados/ocultos chegam sem número (handle nulo) ou com apresentação restrita,
 *    e alguns aparelhos nem os encaminham.
 */
class WhitelistScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        val container = applicationContext.appContainer
        val responded = AtomicBoolean(false)
        val receivedAt = System.currentTimeMillis()

        val rawNumber: String? = callDetails.handle?.schemeSpecificPart
        val hidden = callDetails.handlePresentation != TelecomManager.PRESENTATION_ALLOWED ||
            rawNumber.isNullOrBlank()

        container.appScope.launch {
            var settings: AppSettings? = null
            val result: DecisionResult = try {
                settings = withTimeoutOrNull(SETTINGS_TIMEOUT_MS) { container.settings.current() }
                val s = settings
                if (s == null) {
                    DecisionResult(Decision.ALLOW, DecisionReason.SETTINGS_UNAVAILABLE, null)
                } else {
                    container.engine.decide(rawNumber, hidden, s.toEngineSettings())
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Falha inesperada na análise", t)
                DecisionResult(Decision.ALLOW, DecisionReason.SETTINGS_UNAVAILABLE, null)
            }

            if (responded.compareAndSet(false, true)) {
                respondToCall(callDetails, buildResponse(result.decision, settings?.hideFromCallLog ?: true))
            }

            // Tudo abaixo acontece DEPOIS da resposta ao Android, para não atrasar a chamada.
            try {
                val s = settings ?: AppSettings()
                val blocked = result.decision == Decision.REJECT
                if (blocked || s.logAllowedCalls) {
                    container.history.record(
                        CallEvent(
                            rawNumber = rawNumber?.takeIf { !hidden },
                            normalized = result.normalized,
                            timestamp = receivedAt,
                            allowed = !blocked,
                            reason = result.reason.name,
                        )
                    )
                }
                if (blocked && s.autoBlock && result.reason == DecisionReason.NOT_WHITELISTED) {
                    result.normalized?.let { container.blocklist.block(rawNumber ?: it, auto = true) }
                }
                if (blocked && s.notifyOnBlock) {
                    container.notifier.notifyBlocked(result.normalized, receivedAt)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Não foi possível registrar o histórico", t)
            }
        }
    }

    private fun buildResponse(decision: Decision, hideFromLog: Boolean): CallResponse {
        val builder = CallResponse.Builder()
        if (decision == Decision.REJECT) {
            builder
                .setDisallowCall(true)   // não deixa a chamada chegar ao usuário
                .setRejectCall(true)     // rejeita (o chamador ouve como ocupado/recusado)
                .setSkipCallLog(hideFromLog)   // ajuste: esconder ou não do registro do app Telefone
                .setSkipNotification(true) // sem notificação de chamada perdida do sistema
        }
        return builder.build()
    }

    companion object {
        private const val TAG = "CallGuardScreening"
        private const val SETTINGS_TIMEOUT_MS = 1_000L
    }
}
