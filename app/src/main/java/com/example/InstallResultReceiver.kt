package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast

/** Recebe o resultado da instalação. Se o usuário cancelar, o APK baixado é apagado. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ApkInstaller.ACTION_INSTALL_STATUS) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm: Intent? = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirm)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> ApkDownloader.deleteAll(context)
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                ApkDownloader.deleteAll(context)
                Toast.makeText(
                    context,
                    "Instalação cancelada. Toque em baixar de novo para tentar outra vez.",
                    Toast.LENGTH_LONG
                ).show()
            }
            else -> {
                ApkDownloader.deleteAll(context)
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "motivo desconhecido"
                Toast.makeText(context, "Não foi possível instalar: $message", Toast.LENGTH_LONG).show()
            }
        }
    }
}
