package com.callguard.whitelist.core

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class CallDecisionEngineTest {

    private class FakeLookup(
        private val numbers: Set<String>,
        private val fail: Boolean = false,
        private val delayMs: Long = 0,
    ) : WhitelistLookup {
        var queriedKeys: Set<String> = emptySet()
        override suspend fun isEmpty(): Boolean {
            if (delayMs > 0) delay(delayMs)
            if (fail) throw IllegalStateException("db down")
            return numbers.isEmpty()
        }
        override suspend fun containsAny(keys: Set<String>): Boolean {
            queriedKeys = keys
            return keys.any { it in numbers }
        }
    }

    private val settings = EngineSettings(
        protectionEnabled = true,
        failurePolicy = FailurePolicy.ALLOW,
        hiddenPolicy = HiddenPolicy.REJECT,
        normalizer = NormalizerConfig("55", "47"),
    )

    private val joao = "5547999999999"

    private fun decide(lookup: WhitelistLookup, raw: String?, hidden: Boolean = false, s: EngineSettings = settings, timeout: Long = 3000) =
        runBlocking { CallDecisionEngine(lookup, timeout).decide(raw, hidden, s) }

    @Test fun whitelistedNumberInAnyFormatIsAllowed() {
        val lookup = FakeLookup(setOf(joao))
        listOf("+5547999999999", "047999999999", "(47) 99999-9999", "99999-9999").forEach {
            val r = decide(lookup, it)
            assertEquals(it, Decision.ALLOW, r.decision)
            assertEquals(DecisionReason.WHITELISTED, r.reason)
        }
    }

    @Test fun unknownNumberIsRejected() {
        val r = decide(FakeLookup(setOf(joao)), "+55 11 98888-7777")
        assertEquals(Decision.REJECT, r.decision)
        assertEquals(DecisionReason.NOT_WHITELISTED, r.reason)
        assertEquals("5511988887777", r.normalized)
    }

    @Test fun onlyNumericKeysReachTheLookup() {
        val lookup = FakeLookup(setOf(joao))
        decide(lookup, "+55 47 99999-9999")
        assertEquals(true, lookup.queriedKeys.all { k -> k.all { it in '0'..'9' } })
    }

    @Test fun protectionOffAllowsEverything() {
        val r = decide(FakeLookup(setOf(joao)), "+5511988887777", s = settings.copy(protectionEnabled = false))
        assertEquals(Decision.ALLOW, r.decision)
        assertEquals(DecisionReason.PROTECTION_OFF, r.reason)
    }

    @Test fun emptyWhitelistDoesNotBlockEverything() {
        val r = decide(FakeLookup(emptySet()), "+5511988887777")
        assertEquals(Decision.ALLOW, r.decision)
        assertEquals(DecisionReason.EMPTY_WHITELIST, r.reason)
    }

    @Test fun hiddenNumberFollowsHiddenPolicy() {
        val lookup = FakeLookup(setOf(joao))
        assertEquals(DecisionReason.HIDDEN_REJECTED, decide(lookup, null, hidden = true).reason)
        assertEquals(DecisionReason.HIDDEN_REJECTED, decide(lookup, "", hidden = false).reason)
        val allow = decide(lookup, null, hidden = true, s = settings.copy(hiddenPolicy = HiddenPolicy.ALLOW))
        assertEquals(Decision.ALLOW, allow.decision)
        assertEquals(DecisionReason.HIDDEN_ALLOWED, allow.reason)
    }

    @Test fun lookupFailureIsNotTreatedAsUnauthorized() {
        val failing = FakeLookup(setOf(joao), fail = true)
        val allow = decide(failing, "+5511988887777")
        assertEquals(Decision.ALLOW, allow.decision)
        assertEquals(DecisionReason.LOOKUP_FAILED_ALLOWED, allow.reason)

        val reject = decide(failing, "+5511988887777", s = settings.copy(failurePolicy = FailurePolicy.REJECT))
        assertEquals(Decision.REJECT, reject.decision)
        assertEquals(DecisionReason.LOOKUP_FAILED_REJECTED, reject.reason)
    }

    @Test fun slowLookupTimesOutAndUsesFailurePolicy() {
        val slow = FakeLookup(setOf(joao), delayMs = 500)
        val r = decide(slow, joao, timeout = 50)
        assertEquals(DecisionReason.LOOKUP_FAILED_ALLOWED, r.reason)
    }
}
