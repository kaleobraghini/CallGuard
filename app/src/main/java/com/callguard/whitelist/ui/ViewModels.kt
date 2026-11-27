package com.callguard.whitelist.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.callguard.whitelist.AppContainer
import com.callguard.whitelist.appContainer
import com.callguard.whitelist.core.FailurePolicy
import com.callguard.whitelist.core.HiddenPolicy
import com.callguard.whitelist.core.PhoneNormalizer
import com.callguard.whitelist.data.AppSettings
import com.callguard.whitelist.data.CallEvent
import com.callguard.whitelist.data.CategoryEntity
import com.callguard.whitelist.data.CategoryWithCount
import com.callguard.whitelist.data.ImportCandidate
import com.callguard.whitelist.data.SaveResult
import com.callguard.whitelist.data.ThemeMode
import com.callguard.whitelist.data.WhitelistEntry
import com.callguard.whitelist.data.WhitelistWithCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer) -> VM): VM {
    val container = LocalContext.current.appContainer
    return viewModel(factory = viewModelFactory { initializer { create(container) } })
}

private fun <T> kotlinx.coroutines.flow.Flow<T>.toState(vm: ViewModel, initial: T): StateFlow<T> =
    stateIn(vm.viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

fun SaveResult.errorMessage(): String? = when (this) {
    SaveResult.Ok -> null
    SaveResult.InvalidNumber -> "Número inválido. Digite pelo menos ${PhoneNormalizer.MIN_DIGITS} dígitos."
    is SaveResult.Duplicate -> "Este número já está na whitelist" +
        (existing.name?.let { " como \"$it\"" } ?: "") + "."
}

// ---------------- Início ----------------

data class HomeState(
    val protectionEnabled: Boolean = true,
    val allowedCount: Int = 0,
    val blockedCount: Int = 0,
)

class BlockedViewModel(private val c: AppContainer) : ViewModel() {
    val items: StateFlow<List<com.callguard.whitelist.data.BlockedNumber>> = c.blocklist.all.toState(this, emptyList())
    val categories: StateFlow<List<CategoryEntity>> = c.whitelist.categories.toState(this, emptyList())

    fun unblock(key: String) { viewModelScope.launch { c.blocklist.unblock(key) } }

    suspend fun addToWhitelist(number: String, name: String?, categoryId: Long?, notes: String?): String? =
        c.whitelist.add(number, name, categoryId, notes).errorMessage()

    suspend fun block(number: String): String? =
        if (c.blocklist.block(number)) null else "Número inválido ou já está na whitelist (tire de lá primeiro)."
}

class HomeViewModel(private val c: AppContainer) : ViewModel() {
    val blockedNumbers: StateFlow<Int> = c.blocklist.count.toState(this, 0)

    val state: StateFlow<HomeState> = combine(c.settings.settings, c.whitelist.count, c.history.blockedCount) { s, allowed, blocked ->
        HomeState(s.protectionEnabled, allowed, blocked)
    }.toState(this, HomeState())

    fun setProtection(enabled: Boolean) {
        viewModelScope.launch { c.settings.setProtection(enabled) }
    }
}

// ---------------- Whitelist ----------------

enum class SortMode(val label: String) { NAME("Nome"), RECENT("Mais recentes"), OLDEST("Mais antigos") }

/** Filtro de categoria: null = todas; NO_CATEGORY = sem categoria. */
const val NO_CATEGORY = -1L

class WhitelistViewModel(private val c: AppContainer) : ViewModel() {
    val query = MutableStateFlow("")
    val categoryFilter = MutableStateFlow<Long?>(null)
    val sort = MutableStateFlow(SortMode.NAME)

    val categories: StateFlow<List<CategoryEntity>> = c.whitelist.categories.toState(this, emptyList())

    val items: StateFlow<List<WhitelistWithCategory>> =
        combine(c.whitelist.entries, query, categoryFilter, sort) { all, q, cat, s ->
            val qText = q.trim()
            val qDigits = PhoneNormalizer.digitsOnly(qText)
            all.asSequence()
                .filter { item ->
                    when (cat) {
                        null -> true
                        NO_CATEGORY -> item.entry.categoryId == null
                        else -> item.entry.categoryId == cat
                    }
                }
                .filter { item ->
                    if (qText.isEmpty()) true
                    else (item.entry.name?.contains(qText, ignoreCase = true) == true) ||
                        (qDigits.isNotEmpty() && (item.entry.normalized.contains(qDigits) ||
                            PhoneNormalizer.digitsOnly(item.entry.number).contains(qDigits))) ||
                        (item.entry.notes?.contains(qText, ignoreCase = true) == true)
                }
                .let { seq ->
                    when (s) {
                        SortMode.NAME -> seq.sortedBy { (it.entry.name ?: it.entry.number).lowercase() }
                        SortMode.RECENT -> seq.sortedByDescending { it.entry.createdAt }
                        SortMode.OLDEST -> seq.sortedBy { it.entry.createdAt }
                    }
                }
                .toList()
        }.toState(this, emptyList())

    val totalCount: StateFlow<Int> = c.whitelist.count.toState(this, 0)

    suspend fun save(original: WhitelistEntry?, number: String, name: String?, categoryId: Long?, notes: String?): String? {
        val r = if (original == null) c.whitelist.add(number, name, categoryId, notes)
        else c.whitelist.update(original, number, name, categoryId, notes)
        return r.errorMessage()
    }

    fun delete(id: Long) {
        viewModelScope.launch { c.whitelist.delete(id) }
    }
}

// ---------------- Categorias ----------------

class CategoriesViewModel(private val c: AppContainer) : ViewModel() {
    val categories: StateFlow<List<CategoryWithCount>> = c.whitelist.categoriesWithCount.toState(this, emptyList())
    val entries: StateFlow<List<WhitelistWithCategory>> = c.whitelist.entries.toState(this, emptyList())

    suspend fun save(original: CategoryEntity?, name: String, color: Int): String? {
        if (name.isBlank()) return "Digite um nome."
        val ok = if (original == null) c.whitelist.addCategory(name, color)
        else c.whitelist.updateCategory(original, name, color)
        return if (ok) null else "Já existe uma categoria com esse nome."
    }

    fun delete(id: Long) {
        viewModelScope.launch { c.whitelist.deleteCategory(id) }
    }
}

// ---------------- Histórico ----------------

enum class HistoryFilter(val label: String) { ALL("Todas"), BLOCKED("Bloqueadas"), ALLOWED("Permitidas") }

data class HistoryRow(val event: CallEvent, val whitelistName: String?, val inWhitelist: Boolean, val blocked: Boolean = false)

class HistoryViewModel(private val c: AppContainer) : ViewModel() {
    val filter = MutableStateFlow(HistoryFilter.ALL)
    val categories: StateFlow<List<CategoryEntity>> = c.whitelist.categories.toState(this, emptyList())

    val rows: StateFlow<List<HistoryRow>> = combine(c.history.recent, c.whitelist.entries, c.blocklist.all, filter) { events, entries, blockedList, f ->
        val byKey = entries.associateBy { it.entry.normalized }
        val blockedKeys = blockedList.map { it.normalized }.toHashSet()
        events.asSequence()
            .filter {
                when (f) {
                    HistoryFilter.ALL -> true
                    HistoryFilter.BLOCKED -> !it.allowed
                    HistoryFilter.ALLOWED -> it.allowed
                }
            }
            .map { e ->
                val match = e.normalized?.let { byKey[it] }
                HistoryRow(e, match?.entry?.name, match != null, e.normalized != null && e.normalized in blockedKeys)
            }
            .toList()
    }.toState(this, emptyList())

    suspend fun addToWhitelist(number: String, name: String?, categoryId: Long?, notes: String?): String? =
        c.whitelist.add(number, name, categoryId, notes).errorMessage()

    /** Bloqueia ou desbloqueia (botão separado do "adicionar à whitelist"). */
    fun toggleBlock(row: HistoryRow) {
        val key = row.event.normalized ?: return
        viewModelScope.launch {
            if (row.blocked) c.blocklist.unblock(key) else c.blocklist.block(row.event.rawNumber ?: key)
        }
    }

    fun clear() {
        viewModelScope.launch { c.history.clear() }
    }
}

// ---------------- Configurações ----------------

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val settings: StateFlow<AppSettings> = c.settings.settings.toState(this, AppSettings())

    private val _messages = MutableStateFlow<String?>(null)
    val messages: StateFlow<String?> = _messages.asStateFlow()
    fun consumeMessage() { _messages.value = null }

    fun setFailurePolicy(p: FailurePolicy) = launch { c.settings.setFailurePolicy(p) }
    fun setHiddenPolicy(p: HiddenPolicy) = launch { c.settings.setHiddenPolicy(p) }
    fun setNotify(v: Boolean) = launch { c.settings.setNotifyOnBlock(v) }
    fun setLogAllowed(v: Boolean) = launch { c.settings.setLogAllowed(v) }
    fun setAutoBlock(v: Boolean) = launch { c.settings.setAutoBlock(v) }
    fun setHideFromCallLog(v: Boolean) = launch { c.settings.setHideFromCallLog(v) }
    fun setTheme(m: ThemeMode) = launch { c.settings.setTheme(m) }
    fun setDynamicColor(v: Boolean) = launch { c.settings.setDynamicColor(v) }

    fun saveRegion(country: String, area: String) = launch {
        val cc = country.filter(Char::isDigit)
        val ac = area.filter(Char::isDigit)
        if (cc.isEmpty() || cc.length > 3) { _messages.value = "Código do país inválido."; return@launch }
        if (ac.isNotEmpty() && ac.length != 2 && cc == "55") { _messages.value = "O DDD deve ter 2 dígitos."; return@launch }
        c.settings.setRegion(cc, ac)
        c.whitelist.renormalizeAll()
        _messages.value = "Região salva. Números da whitelist recalculados."
    }

    fun export(uri: Uri) = launch {
        _messages.value = try {
            val n = c.backup.export(uri)
            "$n números exportados."
        } catch (e: Exception) {
            "Falha ao exportar: ${e.message}"
        }
    }

    fun import(uri: Uri) = launch {
        _messages.value = try {
            val r = c.backup.import(uri)
            "${r.added} números importados, ${r.skipped} ignorados (duplicados ou inválidos)."
        } catch (e: Exception) {
            "Falha ao importar: ${e.message}"
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

// ---------------- Importação de contatos ----------------

data class ContactRow(val name: String, val number: String, val key: String, val alreadyAuthorized: Boolean)

data class ContactImportState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val rows: List<ContactRow> = emptyList(),
    val selected: Set<String> = emptySet(),
    val query: String = "",
    val categoryId: Long? = null,
    val error: String? = null,
)

class ContactImportViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(ContactImportState())
    val state: StateFlow<ContactImportState> = _state.asStateFlow()
    val categories: StateFlow<List<CategoryEntity>> = c.whitelist.categories.toState(this, emptyList())

    val visibleRows: StateFlow<List<ContactRow>> = _state.map { s ->
        val q = s.query.trim()
        val qd = PhoneNormalizer.digitsOnly(q)
        if (q.isEmpty()) s.rows
        else s.rows.filter { it.name.contains(q, ignoreCase = true) || (qd.isNotEmpty() && it.key.contains(qd)) }
    }.toState(this, emptyList())

    fun load() {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val cfg = c.settings.current().normalizerConfig
                val existing = c.database.whitelistDao().allNormalized().toHashSet()
                val seen = HashSet<String>()
                val rows = c.contacts.load().mapNotNull { dc ->
                    val key = PhoneNormalizer.normalize(dc.number, cfg) ?: return@mapNotNull null
                    if (!seen.add(key)) return@mapNotNull null
                    ContactRow(dc.name, dc.number, key, key in existing)
                }
                _state.update { it.copy(loading = false, loaded = true, rows = rows, selected = emptySet()) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = "Não foi possível ler a agenda: ${e.message}") }
            }
        }
    }

    fun toggle(key: String) = _state.update {
        it.copy(selected = if (key in it.selected) it.selected - key else it.selected + key)
    }

    fun selectAllVisible() {
        val keys = visibleRows.value.filter { !it.alreadyAuthorized }.map { it.key }
        _state.update { it.copy(selected = it.selected + keys) }
    }

    fun clearSelection() = _state.update { it.copy(selected = emptySet()) }
    fun setQuery(q: String) = _state.update { it.copy(query = q) }
    fun setCategory(id: Long?) = _state.update { it.copy(categoryId = id) }

    /** Copia os números selecionados para a whitelist local. Retorna a mensagem de resultado. */
    suspend fun importSelected(): String {
        val s = _state.value
        val chosen = s.rows.filter { it.key in s.selected && !it.alreadyAuthorized }
        if (chosen.isEmpty()) return "Nenhum contato selecionado."
        val r = c.whitelist.importAll(chosen.map { ImportCandidate(number = it.number, name = it.name) }, s.categoryId)
        val existing = c.database.whitelistDao().allNormalized().toHashSet()
        _state.update { st -> st.copy(selected = emptySet(), rows = st.rows.map { it.copy(alreadyAuthorized = it.key in existing) }) }
        return "${r.added} contatos autorizados" + if (r.skipped > 0) ", ${r.skipped} já existiam." else "."
    }
}
