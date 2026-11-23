package com.callguard.whitelist.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNormalizerTest {

    private val br = NormalizerConfig(countryCode = "55", areaCode = "47")
    private val brNoDdd = NormalizerConfig(countryCode = "55", areaCode = "")

    private fun n(raw: String?, cfg: NormalizerConfig = br) = PhoneNormalizer.normalize(raw, cfg)

    @Test fun internationalWithPlus() {
        assertEquals("5547999999999", n("+55 47 99999-9999"))
        assertEquals("5547999999999", n("+55 (47) 9 9999-9999"))
        assertEquals("12125551234", n("+1 (212) 555-1234"))
    }

    @Test fun brazilianFormatsAllMatch() {
        val expected = "5547999999999"
        listOf(
            "+5547999999999",
            "5547999999999",
            "47999999999",
            "(47) 99999-9999",
            "047999999999",
            "0 47 99999 9999",
            "0154799999999 9".replace(" ", ""), // 0 + operadora 15 + DDD + número
            "0 21 47 99999-9999",
            "99999-9999", // local, DDD 47 configurado
            "tel:+55-47-99999-9999",
        ).forEach { assertEquals("formato: $it", expected, n(it)) }
    }

    @Test fun landlineEightDigits() {
        assertEquals("554733334444", n("(47) 3333-4444"))
        assertEquals("554733334444", n("3333-4444"))
        assertEquals("554733334444", n("+55 47 3333-4444"))
        assertEquals("554733334444", n("04733334444"))
    }

    @Test fun localWithoutConfiguredAreaCodeStaysAsDigits() {
        assertEquals("999999999", n("99999-9999", brNoDdd))
    }

    @Test fun ddd55IsNotConfusedWithCountryCode() {
        // DDD 55 (RS) + celular de 9 dígitos = 11 dígitos nacionais
        assertEquals("5555999998888", n("(55) 99999-8888"))
        assertEquals("5555999998888", n("+55 55 99999-8888"))
    }

    @Test fun tollFreeAndShortCodes() {
        assertEquals("558001234567", n("0800 123 4567"))
        assertEquals(n("0800-123-4567"), n("08001234567"))
        assertEquals("190", n("190"))
    }

    @Test fun internationalFromBrazilWithCarrier() {
        assertEquals("12125551234", n("00 21 1 212 555 1234"))
    }

    @Test fun invalidInputs() {
        assertNull(n(null))
        assertNull(n(""))
        assertNull(n("   "))
        assertNull(n("abc"))
        assertNull(n("12"))
    }

    @Test fun genericCountry() {
        val de = NormalizerConfig(countryCode = "49")
        assertEquals("4930123456", n("030 123456", de))
        assertEquals("4930123456", n("+49 30 123456", de))
        assertEquals("4930123456", n("0049 30 123456", de))
        val us = NormalizerConfig(countryCode = "1")
        assertEquals("12125551234", n("(212) 555-1234", us))
        assertEquals("12125551234", n("1 212 555 1234", us))
    }

    @Test fun candidateKeysContainCanonicalFirst() {
        val keys = PhoneNormalizer.candidateKeys("(47) 99999-9999", br)
        assertEquals("5547999999999", keys.first())
        assertTrue(keys.contains("47999999999"))
        assertTrue(PhoneNormalizer.candidateKeys(null, br).isEmpty())
    }

    @Test fun formatForDisplay() {
        assertEquals("+55 47 99999-9999", PhoneNormalizer.format("5547999999999"))
        assertEquals("+55 47 3333-4444", PhoneNormalizer.format("554733334444"))
        assertEquals("Número oculto", PhoneNormalizer.format(null))
    }
}
