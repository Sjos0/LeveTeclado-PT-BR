package com.example

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {

    sealed class UpdateResult {
        data class UpToDate(val currentVersion: String) : UpdateResult()
        data class NewVersion(
            val currentVersion: String,
            val latestVersion: String,
            val releaseName: String,
            val releaseNotes: String,
            val downloadUrl: String,
            val htmlUrl: String
        ) : UpdateResult()
        data class Error(val message: String) : UpdateResult()
    }

    suspend fun checkForUpdates(
        repoOwner: String = "kirakillerdeathsquad",
        repoName: String = "teclado-pt-br"
    ): UpdateResult = withContext(Dispatchers.IO) {
        val currentVersion = BuildConfig.VERSION_NAME
        var connection: HttpURLConnection? = null

        try {
            val url = URL("https://api.github.com/repos/$repoOwner/$repoName/releases/latest")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "Teclado-PT-BR-Android")
            }

            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val jsonString = reader.readText()
                reader.close()

                val json = JSONObject(jsonString)
                val rawTagName = json.optString("tag_name", "").trim()
                val latestVersion = rawTagName.removePrefix("v").removePrefix("V")
                val releaseName = json.optString("name", "Nova versão disponível")
                val releaseNotes = json.optString("body", "Melhorias de desempenho e correções.")
                val htmlUrl = json.optString("html_url", "https://github.com/$repoOwner/$repoName/releases")

                // Procurar arquivo APK nos assets da release
                var downloadUrl = htmlUrl
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val assetName = asset.optString("name", "")
                        if (assetName.endsWith(".apk", ignoreCase = true)) {
                            downloadUrl = asset.optString("browser_download_url", htmlUrl)
                            break
                        }
                    }
                }

                if (isNewerVersion(latestVersion, currentVersion)) {
                    UpdateResult.NewVersion(
                        currentVersion = currentVersion,
                        latestVersion = latestVersion,
                        releaseName = releaseName,
                        releaseNotes = releaseNotes,
                        downloadUrl = downloadUrl,
                        htmlUrl = htmlUrl
                    )
                } else {
                    UpdateResult.UpToDate(currentVersion)
                }
            } else if (responseCode == 404) {
                // Nenhuma release criada ainda no repositório
                UpdateResult.UpToDate(currentVersion)
            } else {
                UpdateResult.Error("Servidor GitHub retornou código: $responseCode")
            }
        } catch (e: Exception) {
            UpdateResult.Error(e.message ?: "Falha ao verificar conexão com a internet.")
        } finally {
            connection?.disconnect()
        }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        if (latest.isEmpty() || current.isEmpty()) return false
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}
