package com.callguard.whitelist.core

/**
 * Configuração regional usada para completar números que chegam sem código do país ou sem DDD.
 *
 * @param countryCode código do país, só dígitos (ex.: "55" para o Brasil).
 * @param areaCode DDD padrão do usuário (ex.: "47"). Opcional; usado apenas para números locais.
 */
data class NormalizerConfig(
    val countryCode: String = "55",
    val areaCode: String = "",
) {
    val cc: String = countryCode.filter { it in '0'..'9' }
    val ac: String = areaCode.filter { it in '0'..'9' }
}

/**
 * Converte qualquer formatação de número telefônico em uma chave canônica:
 * apenas dígitos, no formato internacional E.164 sem o "+" (ex.: "5547999999999").
 *
 * A mesma função é usada ao cadastrar um número e ao receber uma chamada,
 * então formatos diferentes do mesmo número geram a mesma chave.
 *
 * É código Kotlin puro (sem Android) para ser rápido e testável.
 */
object PhoneNormalizer {

    /** Menor quantidade de dígitos aceita como número válido para a whitelist. */
    const val MIN_DIGITS = 3

    fun digitsOnly(raw: String?): String = raw?.filter { it in '0'..'9' }.orEmpty()

    /**
     * @return a chave canônica, ou null quando o texto não contém dígitos suficientes.
     */
    fun normalize(raw: String?, config: NormalizerConfig = NormalizerConfig()): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        val digits = digitsOnly(trimmed)
        if (digits.length < MIN_DIGITS) return null

        // "+" explícito: o número já é internacional.
        if (trimmed.startsWith("+")) return digits

        return if (config.cc == "55") normalizeBrazil(digits, config.ac)
        else normalizeGeneric(digits, config.cc, config.ac)
    }

    /**
     * Brasil:
     *  - 00 + operadora(2) + número internacional      -> número internacional
     *  - 55 + DDD + número (12 ou 13 dígitos)          -> já canônico
     *  - 0 + operadora(2) + DDD + número (13/14 dígitos)-> 55 + DDD + número
     *  - 0 + DDD + número (11/12 dígitos)              -> 55 + DDD + número
     *  - DDD + número (10/11 dígitos)                  -> 55 + DDD + número
     *  - número local (8/9 dígitos) + DDD configurado  -> 55 + DDD + número
     */
    private fun normalizeBrazil(digits: String, ac: String): String {
        var d = digits
        if (d.startsWith("00") && d.length > 6) {
            return d.drop(4) // 00 + código da operadora + número internacional
        }
        if (d.startsWith("55") && (d.length == 12 || d.length == 13)) return d
        if (d.startsWith("0")) {
            d = when (d.length) {
                11, 12 -> d.drop(1)   // 0 + DDD + número
                13, 14 -> d.drop(3)   // 0 + operadora + DDD + número
                else -> d.trimStart('0')
            }
        }
        return when (d.length) {
            10, 11 -> "55$d"
            8, 9 -> if (ac.length == 2) "55$ac$d" else d
            else -> d // códigos curtos ou formatos desconhecidos: mantém os dígitos
        }
    }

    /** Demais países: regras genéricas de prefixo internacional "00" e prefixo nacional "0". */
    private fun normalizeGeneric(digits: String, cc: String, ac: String): String {
        if (cc.isEmpty()) return digits
        if (digits.startsWith("00") && digits.length > 6) return digits.drop(2)
        if (digits.startsWith("0")) return cc + digits.drop(1)
        if (digits.startsWith(cc) && digits.length >= 11) return digits
        if (digits.length <= 8 && ac.isNotEmpty()) return cc + ac + digits
        if (digits.length >= 7) return cc + digits
        return digits
    }

    /**
     * Chaves que podem representar o número recebido. A principal é a canônica; os dígitos brutos
     * entram como segunda opção para formatos que a normalização não reconhece (códigos curtos).
     */
    fun candidateKeys(raw: String?, config: NormalizerConfig): Set<String> {
        val canonical = normalize(raw, config) ?: return emptySet()
        val digits = digitsOnly(raw)
        return if (digits.length >= MIN_DIGITS && digits != canonical) setOf(canonical, digits)
        else setOf(canonical)
    }

    /** Formatação amigável para exibir na interface (nunca usada na decisão). */
    fun format(key: String?): String {
        if (key.isNullOrEmpty()) return "Número oculto"
        if (key.startsWith("55") && (key.length == 12 || key.length == 13)) {
            val ddd = key.substring(2, 4)
            val local = key.substring(4)
            val split = local.length - 4
            return "+55 $ddd ${local.substring(0, split)}-${local.substring(split)}"
        }
        return if (key.length >= 7) "+$key" else key
    }
}
