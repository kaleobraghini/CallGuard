package com.callguard.whitelist.screening

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent

/** Função de sistema "Filtragem de chamadas / ID de chamadas e spam". Não exige ser o discador padrão. */
object RoleHelper {

    private fun manager(context: Context): RoleManager? = context.getSystemService(RoleManager::class.java)

    fun isAvailable(context: Context): Boolean =
        manager(context)?.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) == true

    fun isHeld(context: Context): Boolean =
        manager(context)?.let { it.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) && it.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) } == true

    fun requestIntent(context: Context): Intent? =
        manager(context)?.takeIf { it.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) }
            ?.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
}
