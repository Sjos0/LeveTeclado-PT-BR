package com.example

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.example.keyboard.KeyboardPreferences

class MainActivity : Activity() {

    private lateinit var prefs: KeyboardPreferences
    private val density by lazy { resources.displayMetrics.density }

    private fun dp(value: Int) = (value * density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = KeyboardPreferences(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212")) // Fundo escuro como no Gboard
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Top App Bar no estilo exato do Gboard
        val appBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
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
            text = "Configurações do Teclado"
            textSize = 21f
            setTextColor(Color.parseColor("#F4F4F5"))
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            setPadding(dp(12), 0, 0, 0)
        }
        appBar.addView(headerTitle)
        root.addView(appBar)

        // Lista de opções rolável
        val scrollView = ScrollView(this).apply {
            isFillViewport = true
        }

        val listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(32))
        }

        // 1. Idiomas
        listContainer.addView(createSettingsItem(
            iconRes = R.drawable.ic_settings_language,
            title = "Idiomas",
            subtitle = "Português (Brasil) (QWERTY)"
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
            subtitle = "Escuro dinâmico (baixo consumo de energia)"
        ) {
            showThemeDialog()
        })

        // 4. Correções e sugestões
        listContainer.addView(createSettingsItem(
            iconRes = R.drawable.ic_settings_correction,
            title = "Correções e sugestões",
            subtitle = "Autocorreção conservadora, sugestões contextuais"
        ) {
            showCorrectionsDialog()
        })

        // 5. Testar digitação (atalho rápido para testar a velocidade e símbolos)
        listContainer.addView(createSettingsItem(
            iconRes = R.drawable.ic_settings_keyboard,
            title = "Testar teclado",
            subtitle = "Experimente a digitação rápida e seleção de símbolos"
        ) {
            showTestKeyboardDialog()
        })

        // 6. Dicionário
        listContainer.addView(createSettingsItem(
            iconRes = R.drawable.ic_settings_dictionary,
            title = "Dicionário",
            subtitle = "Dicionário offline pt-BR compacto (~8.3 KB)"
        ) {
            showDictionaryDialog()
        })

        // 7. Privacidade
        listContainer.addView(createSettingsItem(
            iconRes = R.drawable.ic_settings_privacy,
            title = "Privacidade",
            subtitle = "100% offline • Sem acesso à internet ou coleta"
        ) {
            showPrivacyDialog()
        })

        // 8. Sobre
        listContainer.addView(createSettingsItem(
            iconRes = R.drawable.ic_settings_info,
            title = "Sobre",
            subtitle = "Teclado PT-BR v1.0 • Otimizado para RAM < 900 MB"
        ) {
            showAboutDialog()
        })

        scrollView.addView(listContainer)
        root.addView(scrollView)

        setContentView(root)
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
            setPadding(dp(20), dp(16), dp(20), dp(16))
            isClickable = true
            isFocusable = true
            val typedValue = TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true)
            setBackgroundResource(typedValue.resourceId)
            setOnClickListener { onClick() }
        }

        val icon = ImageView(this).apply {
            setImageResource(iconRes)
            setColorFilter(Color.parseColor("#E2E8F0"))
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
            textSize = 13.5f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, dp(3), 0, 0)
        }
        textContainer.addView(subtitleView)

        row.addView(textContainer)
        return row
    }

    private fun showLanguageDialog() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val isEnabled = imm.enabledInputMethodList.any { it.packageName == packageName }

        val currentIme = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        val isSelected = currentIme != null && currentIme.contains(packageName)

        val status = StringBuilder()
        status.append("Layout: Português (Brasil) QWERTY\n\n")
        status.append(if (isEnabled) "✓ Ativado nos métodos de entrada\n" else "✗ Não ativado nas configurações\n")
        status.append(if (isSelected) "✓ Teclado atualmente selecionado e ativo\n" else "✗ Não é o teclado padrão atual\n")

        AlertDialog.Builder(this)
            .setTitle("Idiomas e Ativação")
            .setMessage(status.toString())
            .setPositiveButton("Escolher como Ativo") { _, _ ->
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
            text = "Resposta tátil ao digitar (desligado por padrão para economizar bateria)."
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
            text = "Primeira letra em maiúscula no início de frases e após ponto final."
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
            .setMessage("Tema Escuro Dinâmico de Alta Eficiência\n\n• Otimizado para telas OLED e LCD para reduzir consumo de bateria.\n• Teclas de alto contraste e layout QWERTY limpo com 'ç' visível e botão de símbolos ?123 em destaque.")
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
            text = "Corrige apenas erros próximos de palavras reais. Pressionar Backspace logo após a correção desfaz a alteração imediatamente."
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 13f
            setPadding(0, dp(2), 0, dp(16))
        }
        container.addView(switchAutoCorrect)
        container.addView(autoCorrectSub)

        AlertDialog.Builder(this)
            .setTitle("Correções e Sugestões")
            .setView(container)
            .setPositiveButton("Pronto", null)
            .show()
    }

    private fun showTestKeyboardDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(16))
        }

        val hintText = TextView(this).apply {
            text = "Digite rapidamente abaixo para testar a velocidade e a tecla ?123 de símbolos:"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 14f
            setPadding(0, 0, 0, dp(12))
        }
        container.addView(hintText)

        val editTest = EditText(this).apply {
            hint = "Experimente digitar aqui..."
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.parseColor("#F4F4F5"))
            setBackgroundColor(Color.parseColor("#27272A"))
            setPadding(dp(16), dp(16), dp(16), dp(16))
            textSize = 16f
        }
        container.addView(editTest)

        AlertDialog.Builder(this)
            .setTitle("Testar Digitação")
            .setView(container)
            .setPositiveButton("Concluir", null)
            .show()

        editTest.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(editTest, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun showDictionaryDialog() {
        AlertDialog.Builder(this)
            .setTitle("Dicionário Offline")
            .setMessage("Vocabulário do Português do Brasil\n\n• Formato binário indexado ultra-compacto (~8.3 KB).\n• Vocabulário baseado no Projeto Vero (LGPLv3/MPL 1.1) e domínio público.\n• Carregamento instantâneo via mmap/buffer com consumo quase nulo de memória RAM.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showPrivacyDialog() {
        AlertDialog.Builder(this)
            .setTitle("Privacidade e Segurança")
            .setMessage("100% Offline por Design\n\n• O aplicativo NÃO possui permissão de internet (android.permission.INTERNET sequer existe no AndroidManifest.xml).\n• Não envia dados para a nuvem.\n• Sem telemetria, sem anúncios e sem rastreadores.")
            .setPositiveButton("Entendido", null)
            .show()
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Sobre o Teclado PT-BR")
            .setMessage("Teclado Leve PT-BR v1.0\n\n• Desenvolvido para celulares com menos de 900 MB de RAM e 16 GB de armazenamento interno.\n• Consumo PSS real inferior a 10 MB de RAM.\n• Núcleo nativo em Rust compilado com cargo-ndk.\n• Compatível com Android 8.0 (API 26) ou superior.")
            .setPositiveButton("Fechar", null)
            .show()
    }
}
