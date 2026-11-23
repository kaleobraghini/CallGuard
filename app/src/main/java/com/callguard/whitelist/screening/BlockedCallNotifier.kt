package com.callguard.whitelist.screening

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.callguard.whitelist.MainActivity
import com.callguard.whitelist.R
import com.callguard.whitelist.core.PhoneNormalizer

class BlockedCallNotifier(private val context: Context) {

    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Chamadas bloqueadas",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Aviso discreto quando uma chamada fora da whitelist é rejeitada" }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun notifyBlocked(normalized: String?, timestamp: Long) {
        if (!canNotify()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_HISTORY)
        }
        val pending = PendingIntent.getActivity(
            context, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setContentTitle("Chamada bloqueada")
            .setContentText(PhoneNormalizer.format(normalized))
            .setWhen(timestamp)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setGroup(GROUP)
            .build()
        try {
            NotificationManagerCompat.from(context).notify((timestamp % Int.MAX_VALUE).toInt(), notification)
        } catch (e: SecurityException) {
            // Permissão revogada entre a verificação e o envio: ignora.
        }
    }

    companion object {
        const val CHANNEL_ID = "blocked_calls"
        private const val GROUP = "blocked"
    }
}
