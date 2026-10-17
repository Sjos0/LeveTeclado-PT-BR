package com.example

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Checa atualizações enquanto o teclado está em uso (sem serviço extra em segundo plano)
 * e avisa com uma notificação. Verifica no máximo a cada 12 horas e avisa uma vez por versão.
 */
object UpdateNotifier {
    private const val PREFS = "update_prefs"
    private const val KEY_LAST_CHECK = "last_check_ms"
    private const val KEY_LAST_NOTIFIED = "last_notified_version"
    private const val CHANNEL_ID = "termclado_updates"
    private const val NOTIFICATION_ID = 1001
    private const val CHECK_INTERVAL_MS = 12L * 60L * 60L * 1000L

    fun maybeCheck(context: Context, scope: CoroutineScope) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_LAST_CHECK, 0L) < CHECK_INTERVAL_MS) return
        prefs.edit().putLong(KEY_LAST_CHECK, now).apply()

        scope.launch {
            val result = UpdateChecker.checkForUpdates()
            if (result is UpdateChecker.UpdateResult.NewVersion &&
                prefs.getString(KEY_LAST_NOTIFIED, null) != result.latestVersion
            ) {
                if (notify(context, result.latestVersion)) {
                    prefs.edit().putString(KEY_LAST_NOTIFIED, result.latestVersion).apply()
                }
            }
        }
    }

    private fun notify(context: Context, latestVersion: String): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Atualizações do Termclado", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("show_update", true)
        }
        val pending = PendingIntent.getActivity(
            context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Termclado v$latestVersion disponível")
            .setContentText("Toque para ver e baixar a atualização.")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
        return true
    }
}
