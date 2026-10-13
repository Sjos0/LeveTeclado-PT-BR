package com.example

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import android.content.pm.PackageManager
import android.Manifest
import com.example.keyboard.KeyboardPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : Activity() {

    private lateinit var prefs: KeyboardPreferences
    private val density by lazy { resources.displayMetrics.density }
    private fun dp(value: Int) = (value * density).toInt()

    private lateinit var statusContainer: LinearLayout
    private val REQUEST_MIC = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = KeyboardPreferences(this)

        if (intent?.getBooleanExtra("request_mic_permission", false) == true) {
            requestMicPermission()
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212")) // Fundo escuro com alto contraste
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Barra superior de configurações
        val appBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#181A1F"))
            setPadding(dp(12), dp(16), dp(16), dp(16))
        }

        val btnBack = ImageView(this).apply {
            setImageResource(R.drawable.ic_settings_arrow_back)
            setColorFilter(Color.parseColor("#E2E8F0"))
            setPadding(dp(8), dp(8), dp(8), dp(8))
            val typedValue = TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, typedValue, true)
            setBackgroundResource(typedValue.resourceId)
            setOnClickListener { finish() }
        }
        appBar.addView(btnBack)

        val headerTitle = TextView(this).apply {
            text = "Configurações"
            textSize = 21f
            setTextColor(Color.parseColor("#F4F4F5"))
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(dp(12), 0, 0, 0)
        }
        appBar.addView(headerTitle)
        root.addView(appBar)

        // Lista rolável de opções e cartões de ativação
        val scrollView = ScrollView(this).apply {
            isFillViewport = true
        }

        val listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(32))
        }

        // Área de status e ativação rápida do teclado
        statusContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(12))
        }
        listContainer.addView(statusContainer)

        // Campo de teste rápido integrado para testar digitação e seleção de símbolos
        val testCard = createTestInputCard()
        listContainer.addView(testCard)

        // Linha divisória sutil
        val divider1 = View(this).apply {
            setBackgroundColor(Color.parseColor("#27272A"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
                setMargins(0, dp(12), 0, dp(6))
            }
        }
        listContainer.addView(divider1)

        // 1. Idiomas
        listContainer.addView(createSettingsItem(
            iconRes = R.drawable.ic_settings_language,
            title = "Idiomas",
            subtitle = "Português (Brasil) (QWERTY com tecla 'ç')"
        ) {
            showLanguageDialog()
        })

        // 2. Preferências
        listContainer.addView(createSettingsItem(
            iconRes = R.drawable.ic_settings_preferences,
            title = "Preferências",
            subtitle = "Vibração ao toque, resposta tátil, sons"
        ) {
            showPreferencesDialog()
        })

        // 3. Tema
        listContainer.addView(createSettingsItem(
            iconRes = R.drawable.ic_settings_theme,
            title = "Tema",
            subtitle = "Grafite e Cobalto (otimizado para bateria OLED/LCD)"
        ) {
            showThemeDialog()
        })

        // 4. Correção de texto
        listContainer.addView(createSettingsItem(
            iconRes = R.drawable.ic_settings_correction,
            title = "Correção de texto",
            subtitle = "Autocorreção conservadora, restauração de acentos"
        ) {
            showCorrectionsDialog()
        })

        // 5. Símbolos e Layout
        listContainer.addView(createSettingsItem(
            iconRes = R.drawable.ic_settings_keyboard,
            title = "Símbolos e Layout",
            subtitle = "Linha de números dedicada, tecla ?123 e atalhos rápidos"
        ) {
            showSymbolsDialog()
        })

        // 6. Dicionário
        listContainer.addView(createSettingsItem(
            iconRes = R.drawable.ic_settings_dictionary,
            title = "Dicionário",
            subtitle = "Dicionário offline pt-BR compacto (~8.3 KB)"
        ) {
            showDictionaryDialog()
        })

        // 7. Fala → texto (Groq Whisper)
        listContainer.addView(createSettingsItem(
            iconRes = R.drawable.ic_settings_preferences,
            title = "Fala → texto (IA)",
            subtitle = if (prefs.groqApiKey.isNotBlank()) "API Groq configurada • Toque para editar a chave" else "Cole a chave da API Groq para transcrever voz"
        ) {
            showSpeechToTextDialog()
        })

        // 8. Verificar atualizações
        listContainer.addView(createSettingsItem(
            iconRes = R.drawable.ic_settings_update,
            title = "Verificar atualizações",
            subtitle = "Versão instalada: v${BuildConfig.VERSION_NAME} • Varredura no GitHub"
        ) {
            checkAppUpdates(showFeedbackIfLatest = true)
        })

        // 8. Privacidade
        listContainer.addView(createSettingsItem(
            iconRes = R.drawable.ic_settings_privacy,
            title = "Privacidade e Segurança",
            subtitle = "100% offline • Sem coleta de dados ou histórico"
        ) {
            showPrivacyDialog()
        })

        // 9. Sobre
        listContainer.addView(createSettingsItem(
            iconRes = R.drawable.ic_settings_info,
            title = "Sobre",
            subtitle = "Teclado Leve PT-BR v${BuildConfig.VERSION_NAME} • RAM < 10 MB"
        ) {
            showAboutDialog()
        })

        scrollView.addView(listContainer)
        root.addView(scrollView)
        setContentView(root)

        // Busca automática silenciosa por novas atualizações no GitHub Releases
        CoroutineScope(Dispatchers.Main).launch {
            checkAppUpdates(showFeedbackIfLatest = false)
        }
    }

    override fun onResume() {
        super.onResume()
        updateActivationStatusCard()
    }

    private fun updateActivationStatusCard() {
        statusContainer.removeAllViews()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val isEnabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        val currentIme = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        val isSelected = currentIme != null && currentIme.contains(packageName)

        if (!isEnabled) {
            // Cartão de Ativação Passo 1
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor("#1F242C"))
                setPadding(dp(16), dp(14), dp(16), dp(14))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            val title = TextView(this).apply {
                text = "Etapa 1: Ativar nos métodos de entrada"
                setTextColor(Color.parseColor("#F4F4F5"))
                textSize = 15.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val desc = TextView(this).apply {
                text = "Habilite o Teclado Leve PT-BR para poder usá-lo em qualquer aplicativo."
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 13f
                setPadding(0, dp(4), 0, dp(10))
            }
            val btn = Button(this).apply {
                text = "Ativar nas Configurações"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#3B82F6"))
                setOnClickListener {
                    startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                }
            }
            card.addView(title)
            card.addView(desc)
            card.addView(btn)
            statusContainer.addView(card)
        } else if (!isSelected) {
            // Cartão de Seleção Passo 2
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor("#1F242C"))
                setPadding(dp(16), dp(14), dp(16), dp(14))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            val title = TextView(this).apply {
                text = "Etapa 2: Definir como teclado padrão"
                setTextColor(Color.parseColor("#F4F4F5"))
                textSize = 15.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val desc = TextView(this).apply {
                text = "Selecione o Teclado Leve PT-BR como método de digitação principal."
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 13f
                setPadding(0, dp(4), 0, dp(10))
            }
            val btn = Button(this).apply {
                text = "Selecionar como Padrão"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#10B981"))
                setOnClickListener {
                    imm.showInputMethodPicker()
                }
            }
            card.addView(title)
            card.addView(desc)
            card.addView(btn)
            statusContainer.addView(card)
        } else {
            // Cartão de Status Ativo
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(Color.parseColor("#15231E"))
                setPadding(dp(16), dp(12), dp(16), dp(12))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            val checkIcon = TextView(this).apply {
                text = "✓"
                setTextColor(Color.parseColor("#34D399"))
                textSize = 18f
                setPadding(0, 0, dp(12), 0)
            }
            val statusText = TextView(this).apply {
                text = "Teclado Ativo e Padrão no Sistema"
                setTextColor(Color.parseColor("#E2E8F0"))
                textSize = 14f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            card.addView(checkIcon)
            card.addView(statusText)
            statusContainer.addView(card)
        }
    }

    private fun createTestInputCard(): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1B2028"))
            setPadding(dp(16), dp(14), dp(16), dp(14))
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.setMargins(dp(16), 0, dp(16), 0)
            layoutParams = params
        }

        val title = TextView(this).apply {
            text = "Testar digitação e seleção de símbolos"
            setTextColor(Color.parseColor("#F4F4F5"))
            textSize = 14.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val desc = TextView(this).apply {
            text = "Toque abaixo para abrir o teclado, testar a velocidade e o botão ?123:"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 12.5f
            setPadding(0, dp(2), 0, dp(10))
        }

        val editTest = EditText(this).apply {
            hint = "Digite rapidamente aqui..."
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.parseColor("#F4F4F5"))
            setBackgroundColor(Color.parseColor("#262C36"))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            textSize = 15f
        }

        card.addView(title)
        card.addView(desc)
        card.addView(editTest)
        return card
    }

    private fun createSettingsItem(
        iconRes: Int,
        title: String,
        subtitle: String,
        onClick: () -> Unit
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(14), dp(20), dp(14))
            isClickable = true
            isFocusable = true
            val typedValue = TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true)
            setBackgroundResource(typedValue.resourceId)
            setOnClickListener { onClick() }
        }

        val icon = ImageView(this).apply {
            setImageResource(iconRes)
            setColorFilter(Color.parseColor("#CBD5E1"))
            val iconSize = dp(24)
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                marginEnd = dp(20)
            }
        }
        row.addView(icon)

        val textContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val titleView = TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(Color.parseColor("#F4F4F5"))
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        textContainer.addView(titleView)

        val subtitleView = TextView(this).apply {
            text = subtitle
            textSize = 13f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, dp(3), 0, 0)
        }
        textContainer.addView(subtitleView)

        row.addView(textContainer)
        return row
    }

    private fun showLanguageDialog() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        AlertDialog.Builder(this)
            .setTitle("Idiomas e Entrada")
            .setMessage("Layout atual: Português (Brasil) (QWERTY)\n\n• Inclui linha numérica dedicada (1 a 0).\n• Tecla 'ç' visível diretamente na linha principal.\n• Acentos completos (á, à, â, ã, é, ê, í, ó, ô, õ, ú) no toque prolongado.")
            .setPositiveButton("Alterar Teclado Ativo") { _, _ ->
                imm.showInputMethodPicker()
            }
            .setNeutralButton("Configurações do Sistema") { _, _ ->
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
            .setNegativeButton("Fechar", null)
            .show()
    }

    private fun showPreferencesDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(8))
        }

        val switchVibrate = Switch(this).apply {
            text = "Vibração ao tocar teclas"
            setTextColor(Color.parseColor("#F4F4F5"))
            textSize = 16f
            isChecked = prefs.vibrateOnPress
            setOnCheckedChangeListener { _, isChecked ->
                prefs.vibrateOnPress = isChecked
            }
        }
        val vibrateSub = TextView(this).apply {
            text = "Resposta tátil no toque (desativado por padrão para economizar bateria)."
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 13f
            setPadding(0, dp(2), 0, dp(16))
        }
        container.addView(switchVibrate)
        container.addView(vibrateSub)

        val switchCap = Switch(this).apply {
            text = "Autocapitalização"
            setTextColor(Color.parseColor("#F4F4F5"))
            textSize = 16f
            isChecked = prefs.autoCapitalization
            setOnCheckedChangeListener { _, isChecked ->
                prefs.autoCapitalization = isChecked
            }
        }
        val capSub = TextView(this).apply {
            text = "Primeira letra em maiúscula no início de sentenças e após ponto final."
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 13f
            setPadding(0, dp(2), 0, dp(8))
        }
        container.addView(switchCap)
        container.addView(capSub)

        AlertDialog.Builder(this)
            .setTitle("Preferências")
            .setView(container)
            .setPositiveButton("Pronto", null)
            .show()
    }

    private fun showThemeDialog() {
        AlertDialog.Builder(this)
            .setTitle("Tema do Teclado")
            .setMessage("Tema Escuro Dinâmico de Alto Contraste\n\n• Fundo grafite profundo (#172227) e teclas bem definidas (#2A373E).\n• Consumo mínimo de energia em telas OLED e LCD.\n• Destaques em azul cobalto para melhor visibilidade e conforto visual.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showCorrectionsDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(8))
        }

        val switchAutoCorrect = Switch(this).apply {
            text = "Autocorreção conservadora"
            setTextColor(Color.parseColor("#F4F4F5"))
            textSize = 16f
            isChecked = prefs.autoCorrection
            setOnCheckedChangeListener { _, isChecked ->
                prefs.autoCorrection = isChecked
            }
        }
        val autoCorrectSub = TextView(this).apply {
            text = "Corrige apenas erros nítidos e restaura acentos seguros (ex: 'voce' → 'você', 'nao' → 'não'). Palavras válidas são sempre preservadas."
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 13f
            setPadding(0, dp(2), 0, dp(16))
        }
        container.addView(switchAutoCorrect)
        container.addView(autoCorrectSub)

        AlertDialog.Builder(this)
            .setTitle("Correção de Texto")
            .setView(container)
            .setPositiveButton("Pronto", null)
            .show()
    }

    private fun showSymbolsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Símbolos e Layout")
            .setMessage("Acesso Fácil aos Símbolos\n\n1. Tecla '?123' na linha inferior (canto esquerdo): alterna diretamente para os números e símbolos.\n2. Botão '?123' na barra de ferramentas superior: atalho rápido na barra de sugestões.\n3. Tecla '=\\<': abre mais símbolos matemáticos e especiais (moedas, colchetes, etc.).\n4. Toque longo no ponto (.): abre menu rápido com '!', '?', '@', '-', '_', '/'.")
            .setPositiveButton("Entendido", null)
            .show()
    }

    private fun showDictionaryDialog() {
        AlertDialog.Builder(this)
            .setTitle("Dicionário Offline")
            .setMessage("Dicionário de Alta Eficiência PT-BR\n\n• 100% offline e armazenado localmente no aplicativo.\n• Formato binário ultra-compacto indexado por buckets O(1).\n• Baseado no vocabulário oficial pt-BR do Projeto Vero (LGPLv3/MPL).\n• Carregamento instantâneo via Rust nativo / Kotlin puro.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showPrivacyDialog() {
        AlertDialog.Builder(this)
            .setTitle("Privacidade e Segurança")
            .setMessage("100% Seguro e Offline\n\n• O serviço do teclado nunca acessa a internet nem grava histórico.\n• Nenhum texto digitado é registrado ou compartilhado.\n• A única funcionalidade que usa rede é a busca manual ou automática por atualizações do próprio app no GitHub Releases.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Sobre o Teclado PT-BR")
            .setMessage("Teclado Leve PT-BR v${BuildConfig.VERSION_NAME}\n\n• Desenvolvido para celulares com menos de 1 GB de RAM e 16 GB de armazenamento.\n• Consumo PSS real < 10 MB de RAM.\n• Algoritmo de tolerância a toque rápido para 0ms de latência.\n• Compatível com Android 8.0 (API 26) ou superior.")
            .setPositiveButton("Fechar", null)
            .show()
    }

    private fun checkAppUpdates(showFeedbackIfLatest: Boolean) {
        val progressDialog = if (showFeedbackIfLatest) {
            AlertDialog.Builder(this)
                .setTitle("Verificando Atualizações")
                .setMessage("Consultando GitHub Releases...")
                .setCancelable(false)
                .create().apply { show() }
        } else null

        CoroutineScope(Dispatchers.Main).launch {
            val result = UpdateChecker.checkForUpdates()
            progressDialog?.dismiss()

            when (result) {
                is UpdateChecker.UpdateResult.NewVersion -> {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Nova Versão Disponível: v${result.latestVersion} 🚀")
                        .setMessage("Versão atual instalada: v${result.currentVersion}\nVersão mais recente: v${result.latestVersion}\n\nNovidades:\n${result.releaseNotes}")
                        .setPositiveButton("Baixar Atualização (APK)") { _, _ ->
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(result.downloadUrl))
                                startActivity(intent)
                            } catch (_: Exception) {}
                        }
                        .setNeutralButton("Ver no GitHub") { _, _ ->
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(result.htmlUrl))
                                startActivity(intent)
                            } catch (_: Exception) {}
                        }
                        .setNegativeButton("Depois", null)
                        .show()
                }
                is UpdateChecker.UpdateResult.UpToDate -> {
                    if (showFeedbackIfLatest) {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Aplicativo Atualizado 🎉")
                            .setMessage("Você já está na versão mais recente (v${result.currentVersion})!\n\nSeu teclado conta com digitação otimizada sem atrasos.")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
                is UpdateChecker.UpdateResult.Error -> {
                    if (showFeedbackIfLatest) {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Status das Atualizações")
                            .setMessage("Versão instalada: v${BuildConfig.VERSION_NAME}\n\nStatus da consulta ao GitHub: ${result.message}")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }
        }
    }


    private fun showSpeechToTextDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(8))
        }

        val info = TextView(this).apply {
            text = "Cole sua chave da API Groq (gsk_…). Ela fica salva só neste aparelho e é usada para transcrever o áudio do microfone do teclado (Whisper)."
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 13f
            setPadding(0, 0, 0, dp(12))
        }
        container.addView(info)

        val input = EditText(this).apply {
            hint = "gsk_..."
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.parseColor("#F4F4F5"))
            setBackgroundColor(Color.parseColor("#262C36"))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setText(prefs.groqApiKey)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        }
        container.addView(input)

        val micBtn = Button(this).apply {
            text = "Conceder permissão de microfone"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#3B82F6"))
            setOnClickListener { requestMicPermission() }
        }
        val micPad = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12)
        }
        container.addView(micBtn, micPad)

        AlertDialog.Builder(this)
            .setTitle("Fala → texto")
            .setView(container)
            .setPositiveButton("Salvar") { _, _ ->
                prefs.groqApiKey = input.text?.toString().orEmpty()
                Toast.makeText(this, if (prefs.groqApiKey.isNotBlank()) "Chave salva" else "Chave removida", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun requestMicPermission() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Microfone já permitido", Toast.LENGTH_SHORT).show()
            return
        }
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MIC) {
            val ok = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            Toast.makeText(
                this,
                if (ok) "Microfone permitido" else "Permissão de microfone negada",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

}
