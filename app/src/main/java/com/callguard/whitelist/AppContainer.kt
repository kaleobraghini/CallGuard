package com.callguard.whitelist

import android.app.Application
import android.content.Context
import com.callguard.whitelist.core.CallDecisionEngine
import com.callguard.whitelist.data.AppDatabase
import com.callguard.whitelist.data.BlocklistIndex
import com.callguard.whitelist.data.BlocklistRepository
import com.callguard.whitelist.data.BackupManager
import com.callguard.whitelist.data.ContactsReader
import com.callguard.whitelist.data.HistoryRepository
import com.callguard.whitelist.data.SettingsRepository
import com.callguard.whitelist.data.WhitelistIndex
import com.callguard.whitelist.data.WhitelistRepository
import com.callguard.whitelist.screening.BlockedCallNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Injeção de dependências manual: tudo é criado sob demanda e compartilhado pelo processo. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** Escopo do processo: sobrevive ao ciclo de vida do serviço de filtragem. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy { AppDatabase.create(appContext) }
    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }
    val whitelistIndex: WhitelistIndex by lazy { WhitelistIndex(database) }
    val blocklistIndex: BlocklistIndex by lazy { BlocklistIndex(database) }
    val blocklist: BlocklistRepository by lazy { BlocklistRepository(database, settings) }
    val engine: CallDecisionEngine by lazy { CallDecisionEngine(whitelistIndex, blocklist = blocklistIndex) }
    val whitelist: WhitelistRepository by lazy { WhitelistRepository(database, settings) }
    val history: HistoryRepository by lazy { HistoryRepository(database) }
    val backup: BackupManager by lazy { BackupManager(appContext, database, whitelist) }
    val contacts: ContactsReader by lazy { ContactsReader(appContext) }
    val notifier: BlockedCallNotifier by lazy { BlockedCallNotifier(appContext) }
}

class CallGuardApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifier.createChannel()
        container.appScope.launch {
            runCatching {
                if (!container.settings.defaultCategoriesCreated()) {
                    container.whitelist.ensureDefaultCategories()
                    container.settings.markDefaultCategoriesCreated()
                }
            }
            container.whitelistIndex.warmUp()
        }
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as CallGuardApp).container
