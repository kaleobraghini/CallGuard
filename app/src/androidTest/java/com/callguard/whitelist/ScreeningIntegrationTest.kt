package com.callguard.whitelist

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.callguard.whitelist.core.CallDecisionEngine
import com.callguard.whitelist.core.Decision
import com.callguard.whitelist.core.DecisionReason
import com.callguard.whitelist.core.EngineSettings
import com.callguard.whitelist.core.FailurePolicy
import com.callguard.whitelist.core.HiddenPolicy
import com.callguard.whitelist.core.NormalizerConfig
import com.callguard.whitelist.core.PhoneNormalizer
import com.callguard.whitelist.data.AppDatabase
import com.callguard.whitelist.data.CategoryEntity
import com.callguard.whitelist.data.WhitelistEntry
import com.callguard.whitelist.data.WhitelistIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Testes de integração do caminho real de decisão: Room (SQLite no aparelho) -> WhitelistIndex -> motor.
 * O objeto Call.Details do Android não pode ser instanciado em testes, por isso o serviço é
 * exercitado através do mesmo motor e índice que ele usa em produção.
 */
@RunWith(AndroidJUnit4::class)
class ScreeningIntegrationTest {

    private lateinit var db: AppDatabase
    private lateinit var index: WhitelistIndex
    private lateinit var engine: CallDecisionEngine

    private val cfg = NormalizerConfig("55", "47")
    private val settings = EngineSettings(true, FailurePolicy.ALLOW, HiddenPolicy.REJECT, cfg)

    @Before fun setUp() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        index = WhitelistIndex(db)
        engine = CallDecisionEngine(index)
    }

    @After fun tearDown() = db.close()

    private suspend fun add(number: String, name: String? = null, categoryId: Long? = null): Long =
        db.whitelistDao().insert(
            WhitelistEntry(number = number, normalized = PhoneNormalizer.normalize(number, cfg)!!, name = name, categoryId = categoryId)
        )

    /** O InvalidationTracker do Room é assíncrono; aguarda o índice refletir a escrita. */
    private suspend fun awaitDecision(raw: String, expected: Decision): Decision {
        var last = Decision.ALLOW
        repeat(40) {
            last = engine.decide(raw, false, settings).decision
            if (last == expected) return last
            delay(50)
        }
        return last
    }

    @Test fun whitelistedNumberAllowedAndOthersRejected() = runBlocking {
        add("+55 47 99999-9999", name = "João")
        assertEquals(Decision.ALLOW, awaitDecision("047999999999", Decision.ALLOW))
        val r = engine.decide("+55 11 98888-7777", false, settings)
        assertEquals(Decision.REJECT, r.decision)
        assertEquals(DecisionReason.NOT_WHITELISTED, r.reason)
    }

    @Test fun indexIsRebuiltAfterProcessRestart() = runBlocking {
        add("(47) 3333-4444")
        // Simula processo novo: índice e motor recém-criados, sem cache em memória.
        val freshEngine = CallDecisionEngine(WhitelistIndex(db))
        assertEquals(Decision.ALLOW, freshEngine.decide("+554733334444", false, settings).decision)
    }

    @Test fun removingNumberStartsBlockingIt() = runBlocking {
        val id = add("+5547999999999")
        add("+5547988887777") // mantém a lista não vazia
        assertEquals(Decision.ALLOW, awaitDecision("+5547999999999", Decision.ALLOW))
        db.whitelistDao().deleteById(id)
        assertEquals(Decision.REJECT, awaitDecision("+5547999999999", Decision.REJECT))
    }

    @Test fun duplicateNumbersInDifferentFormatsAreRejectedByDatabase() = runBlocking {
        add("+55 47 99999-9999")
        try {
            add("(47) 99999-9999")
            fail("Duplicado deveria ser recusado")
        } catch (e: SQLiteConstraintException) {
            // esperado: índice único na chave normalizada
        }
    }

    @Test fun deletingCategoryKeepsNumbersAuthorized() = runBlocking {
        val catId = db.categoryDao().insert(CategoryEntity(name = "Trabalho", color = 0))
        val id = add("+5547999999999", name = "João", categoryId = catId)
        db.whitelistDao().clearCategory(catId)
        db.categoryDao().deleteById(catId)
        val entry = db.whitelistDao().getAll().single { it.id == id }
        assertNull(entry.categoryId)
        assertEquals(Decision.ALLOW, awaitDecision("+5547999999999", Decision.ALLOW))
    }

    @Test fun nameAndCategoryDoNotInfluenceDecision() = runBlocking {
        val catId = db.categoryDao().insert(CategoryEntity(name = "Bloquear", color = 0))
        add("+5547999999999", name = "Bloqueado", categoryId = catId)
        assertEquals(Decision.ALLOW, awaitDecision("+5547999999999", Decision.ALLOW))
    }
}
