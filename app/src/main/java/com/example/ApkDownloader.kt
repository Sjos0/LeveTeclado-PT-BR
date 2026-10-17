package com.example

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Baixa o APK da atualização para uma pasta própria do app (cache interno). */
object ApkDownloader {

    /** Lançada quando o usuário cancela o download no meio. */
    class CancelledException : Exception("Download cancelado")

    fun updatesDir(context: Context): File = File(context.cacheDir, "updates").apply { mkdirs() }

    /** Apaga todos os arquivos de atualização (APK final e parciais). */
    fun deleteAll(context: Context) {
        updatesDir(context).listFiles()?.forEach { it.delete() }
    }

    /**
     * Baixa o APK. [onProgress] recebe (bytes baixados, total ou -1).
     * Se [isCancelled] virar true, para e lança [CancelledException].
     * Em qualquer falha ou cancelamento, o arquivo parcial é apagado.
     */
    fun download(
        context: Context,
        url: String,
        version: String,
        isCancelled: () -> Boolean,
        onProgress: (Long, Long) -> Unit
    ): File {
        deleteAll(context)
        val finalFile = File(updatesDir(context), "termclado-v$version.apk")
        val partFile = File(updatesDir(context), "termclado-v$version.apk.part")
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 15_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Termclado-Android")
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("O servidor respondeu com o código $code")
            }
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                partFile.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var done = 0L
                    while (true) {
                        if (isCancelled()) throw CancelledException()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        done += read
                        onProgress(done, total)
                    }
                }
            }
            if (!partFile.renameTo(finalFile)) {
                throw IllegalStateException("Não foi possível salvar o arquivo baixado")
            }
            return finalFile
        } catch (e: Exception) {
            partFile.delete()
            finalFile.delete()
            throw e
        } finally {
            connection?.disconnect()
        }
    }
}

/** Confere o APK baixado antes de pedir a instalação. */
object ApkVerifier {

    /** Retorna null se o APK está ok, ou a mensagem com o problema. */
    fun verify(context: Context, apk: File, installedVersionCode: Int): String? {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val archive = pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNATURES)
            ?: return "O arquivo baixado não é um APK válido"
        if (archive.packageName != context.packageName) {
            return "O APK baixado é de outro aplicativo"
        }
        @Suppress("DEPRECATION")
        val archiveCode = archive.versionCode
        if (archiveCode <= installedVersionCode) {
            return "O APK baixado não é mais novo que a versão instalada"
        }
        @Suppress("DEPRECATION")
        val installed = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
        @Suppress("DEPRECATION")
        val fromApk = archive.signatures
        if (installed == null || fromApk == null) {
            return "Não foi possível conferir a assinatura do APK"
        }
        val sameSigner = fromApk.any { a ->
            installed.any { b -> a.toByteArray().contentEquals(b.toByteArray()) }
        }
        return if (sameSigner) null else "O APK baixado foi assinado com outra chave. A instalação foi cancelada."
    }
}

/** Envia o APK ao instalador do sistema por uma sessão, que informa se o usuário cancelou. */
object ApkInstaller {
    const val ACTION_INSTALL_STATUS = "com.example.action.INSTALL_STATUS"

    fun canInstallPackages(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("termclado.apk", 0, apk.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                val statusIntent = Intent(context, InstallResultReceiver::class.java)
                    .setAction(ACTION_INSTALL_STATUS)
                val pending = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    statusIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                session.commit(pending.intentSender)
            }
        } catch (e: Exception) {
            installer.abandonSession(sessionId)
            throw e
        }
    }
}
