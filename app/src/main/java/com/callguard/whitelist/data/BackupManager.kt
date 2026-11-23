package com.callguard.whitelist.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Exportação/importação da whitelist em JSON, via seletor de arquivos do Android (SAF).
 * Nada sai do aparelho sem o usuário escolher o destino.
 */
class BackupManager(
    private val context: Context,
    private val db: AppDatabase,
    private val repository: WhitelistRepository,
) {

    suspend fun export(uri: Uri): Int = withContext(Dispatchers.IO) {
        val categories = db.categoryDao().getAll()
        val byId = categories.associateBy { it.id }
        val entries = db.whitelistDao().getAll()

        val json = JSONObject().apply {
            put("format", FORMAT)
            put("version", 1)
            put("exportedAt", System.currentTimeMillis())
            put("categories", JSONArray().apply {
                categories.forEach { c -> put(JSONObject().put("name", c.name).put("color", c.color)) }
            })
            put("entries", JSONArray().apply {
                entries.forEach { e ->
                    put(JSONObject().apply {
                        put("number", e.number)
                        put("name", e.name ?: "")
                        put("category", e.categoryId?.let { byId[it]?.name } ?: "")
                        put("notes", e.notes ?: "")
                        put("createdAt", e.createdAt)
                    })
                }
            })
        }
        val out = context.contentResolver.openOutputStream(uri, "wt")
            ?: error("Não foi possível abrir o arquivo")
        out.bufferedWriter(Charsets.UTF_8).use { it.write(json.toString(2)) }
        entries.size
    }

    suspend fun import(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: error("Não foi possível ler o arquivo")
        val root = JSONObject(text)
        require(root.optString("format") == FORMAT) { "Arquivo não é um backup do CallGuard" }

        root.optJSONArray("categories")?.let { arr ->
            for (i in 0 until arr.length()) {
                val c = arr.getJSONObject(i)
                val name = c.optString("name").trim()
                if (name.isNotEmpty()) {
                    repository.addCategory(name, c.optInt("color", WhitelistRepository.DEFAULT_CATEGORY_COLORS[0]))
                }
            }
        }

        val candidates = mutableListOf<ImportCandidate>()
        root.optJSONArray("entries")?.let { arr ->
            for (i in 0 until arr.length()) {
                val e = arr.getJSONObject(i)
                candidates += ImportCandidate(
                    number = e.optString("number"),
                    name = e.optString("name"),
                    categoryName = e.optString("category"),
                    notes = e.optString("notes"),
                    createdAt = e.optLong("createdAt").takeIf { it > 0 },
                )
            }
        }
        repository.importAll(candidates)
    }

    companion object {
        const val FORMAT = "callguard-whitelist"
    }
}
