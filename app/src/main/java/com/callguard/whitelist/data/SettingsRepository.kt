package com.callguard.whitelist.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.callguard.whitelist.core.EngineSettings
import com.callguard.whitelist.core.FailurePolicy
import com.callguard.whitelist.core.HiddenPolicy
import com.callguard.whitelist.core.NormalizerConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val protectionEnabled: Boolean = true,
    val failurePolicy: FailurePolicy = FailurePolicy.ALLOW,
    val hiddenPolicy: HiddenPolicy = HiddenPolicy.REJECT,
    val countryCode: String = "55",
    val areaCode: String = "",
    val notifyOnBlock: Boolean = true,
    val logAllowedCalls: Boolean = true,
    /** Ao rejeitar um número fora da whitelist, coloca ele também na lista de bloqueados. */
    val autoBlock: Boolean = true,
    /** Não deixar as chamadas rejeitadas aparecerem no registro do app Telefone. */
    val hideFromCallLog: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
) {
    val normalizerConfig: NormalizerConfig get() = NormalizerConfig(countryCode, areaCode)

    fun toEngineSettings() = EngineSettings(
        protectionEnabled = protectionEnabled,
        failurePolicy = failurePolicy,
        hiddenPolicy = hiddenPolicy,
        normalizer = normalizerConfig,
    )
}

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {

    private val store = context.applicationContext.settingsStore

    private object Keys {
        val protection = booleanPreferencesKey("protection_enabled")
        val failure = stringPreferencesKey("failure_policy")
        val hidden = stringPreferencesKey("hidden_policy")
        val country = stringPreferencesKey("country_code")
        val area = stringPreferencesKey("area_code")
        val notify = booleanPreferencesKey("notify_on_block")
        val logAllowed = booleanPreferencesKey("log_allowed")
        val autoBlock = booleanPreferencesKey("auto_block")
        val hideLog = booleanPreferencesKey("hide_from_call_log")
        val theme = stringPreferencesKey("theme_mode")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val defaultsCreated = booleanPreferencesKey("default_categories_created")
    }

    val settings: Flow<AppSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            val d = AppSettings()
            AppSettings(
                protectionEnabled = p[Keys.protection] ?: d.protectionEnabled,
                failurePolicy = p[Keys.failure]?.let { runCatching { FailurePolicy.valueOf(it) }.getOrNull() } ?: d.failurePolicy,
                hiddenPolicy = p[Keys.hidden]?.let { runCatching { HiddenPolicy.valueOf(it) }.getOrNull() } ?: d.hiddenPolicy,
                countryCode = p[Keys.country] ?: d.countryCode,
                areaCode = p[Keys.area] ?: d.areaCode,
                notifyOnBlock = p[Keys.notify] ?: d.notifyOnBlock,
                logAllowedCalls = p[Keys.logAllowed] ?: d.logAllowedCalls,
                autoBlock = p[Keys.autoBlock] ?: d.autoBlock,
                hideFromCallLog = p[Keys.hideLog] ?: d.hideFromCallLog,
                themeMode = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.themeMode,
                dynamicColor = p[Keys.dynamic] ?: d.dynamicColor,
            )
        }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setProtection(enabled: Boolean) = store.edit { it[Keys.protection] = enabled }
    suspend fun setFailurePolicy(p: FailurePolicy) = store.edit { it[Keys.failure] = p.name }
    suspend fun setHiddenPolicy(p: HiddenPolicy) = store.edit { it[Keys.hidden] = p.name }
    suspend fun setRegion(country: String, area: String) = store.edit {
        it[Keys.country] = country.filter(Char::isDigit)
        it[Keys.area] = area.filter(Char::isDigit)
    }
    suspend fun setNotifyOnBlock(v: Boolean) = store.edit { it[Keys.notify] = v }
    suspend fun setLogAllowed(v: Boolean) = store.edit { it[Keys.logAllowed] = v }
    suspend fun setAutoBlock(v: Boolean) = store.edit { it[Keys.autoBlock] = v }
    suspend fun setHideFromCallLog(v: Boolean) = store.edit { it[Keys.hideLog] = v }
    suspend fun setTheme(mode: ThemeMode) = store.edit { it[Keys.theme] = mode.name }
    suspend fun setDynamicColor(v: Boolean) = store.edit { it[Keys.dynamic] = v }

    /** Garante que as categorias de exemplo sejam criadas uma única vez. */
    suspend fun defaultCategoriesCreated(): Boolean = store.data.first()[Keys.defaultsCreated] == true
    suspend fun markDefaultCategoriesCreated() = store.edit { it[Keys.defaultsCreated] = true }
}
