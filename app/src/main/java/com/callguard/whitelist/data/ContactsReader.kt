package com.callguard.whitelist.data

import android.content.Context
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DeviceContact(val name: String, val number: String)

/**
 * Lê a agenda apenas quando o usuário abre a tela de importação.
 * O motor de bloqueio nunca consulta a agenda.
 */
class ContactsReader(private val context: Context) {

    suspend fun load(): List<DeviceContact> = withContext(Dispatchers.IO) {
        val result = mutableListOf<DeviceContact>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE ASC",
        )?.use { c ->
            val nameIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (c.moveToNext()) {
                val number = if (numIdx >= 0) c.getString(numIdx) else null
                if (number.isNullOrBlank()) continue
                val name = if (nameIdx >= 0) c.getString(nameIdx) else null
                result += DeviceContact(name = name?.takeIf { it.isNotBlank() } ?: number, number = number)
            }
        }
        result
    }
}
