package com.example

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.example.keyboard.KeyboardPreferences
import com.example.keyboard.LatinIMEKeyboardService

class MainActivity : Activity() {

    private lateinit var prefs: KeyboardPreferences
    private lateinit var statusText1: TextView
    private lateinit var statusText2: TextView
    private lateinit var btnEnable: Button
    private lateinit var btnSelect: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = KeyboardPreferences(this)

        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0F172A")) // Slate-900
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(36), dp(20), dp(36))
        }
        scrollView.addView(root)

        // Título Principal
        val title = TextView(this).apply {
            text = "Teclado PT-BR"
            textSize = 26f
            setTextColor(Color.parseColor("#F8FAFC"))
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        root.addView(title)

        // Subtítulo
        val subtitle = TextView(this).apply {
            text = "Leve, ultra-rápido e 100% offline para português brasileiro."
            textSize = 14f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, dp(4), 0, dp(24))
        }
        root.addView(subtitle)

        // Card Passo 1: Ativar Teclado
        root.addView(createSectionHeader("1. Ativar nos Métodos de Entrada"))
        statusText1 = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#CBD5E1"))
            setPadding(0, dp(4), 0, dp(8))
        }
        root.addView(statusText1)

        btnEnable = createStyledButton("Ativar nas Configurações") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        root.addView(btnEnable)

        // Espaçador
        root.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(20)) })

        // Card Passo 2: Selecionar Teclado
        root.addView(createSectionHeader("2. Definir como Teclado Atual"))
        statusText2 = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#CBD5E1"))
            setPadding(0, dp(4), 0, dp(8))
        }
        root.addView(statusText2)

        btnSelect = createStyledButton("Escolher Teclado") {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }
        root.addView(btnSelect)

        // Espaçador
        root.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(20)) })

        // Card Configuração de Vibração
        root.addView(createSectionHeader("Opções e Economia de Bateria"))
        val switchVibrate = Switch(this).apply {
            text = "Vibração ao tocar teclas"
            setTextColor(Color.parseColor("#F1F5F9"))
            textSize = 15f
            isChecked = prefs.vibrateOnPress
            setOnCheckedChangeListener { _, isChecked ->
                prefs.vibrateOnPress = isChecked
            }
            setPadding(0, dp(8), 0, dp(4))
        }
        root.addView(switchVibrate)

        val vibrateNote = TextView(this).apply {
            text = "(Desligado por padrão para economizar bateria e memória)."
            textSize = 12f
            setTextColor(Color.parseColor("#64748B"))
            setPadding(0, 0, 0, dp(20))
        }
        root.addView(vibrateNote)

        // Card Passo 3: Área de Teste
        root.addView(createSectionHeader("3. Testar Digitação"))
        val testInput = EditText(this).apply {
            hint = "Toque aqui para testar o teclado…"
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.parseColor("#F8FAFC"))
            setBackgroundColor(Color.parseColor("#1E293B"))
            setPadding(dp(16), dp(16), dp(16), dp(16))
            textSize = 16f
        }
        root.addView(testInput)

        // Espaçador
        root.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(24)) })

        // Informações de Sistema & RAM
        val infoBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1E293B"))
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val infoTitle = TextView(this).apply {
            text = "Consumo & Segurança"
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(Color.parseColor("#38BDF8"))
        }
        val infoDesc = TextView(this).apply {
            text = "• RAM esperada: < 10 MB (ideal para celulares com < 900 MB de RAM)\n" +
                    "• Sem permissão de INTERNET no AndroidManifest\n" +
                    "• Sem serviços em segundo plano ou wake locks\n" +
                    "• Compatível com Android 8.0 (API 26) ou superior\n" +
                    "• Núcleo nativo em Rust compilado com cargo-ndk"
            textSize = 13f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, dp(6), 0, 0)
        }
        infoBox.addView(infoTitle)
        infoBox.addView(infoDesc)
        root.addView(infoBox)

        setContentView(scrollView)
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val list = imm.enabledInputMethodList
        val myPackage = packageName
        val isEnabled = list.any { it.packageName == myPackage }

        val currentIme = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        val isSelected = currentIme != null && currentIme.contains(myPackage)

        if (isEnabled) {
            statusText1.text = "✓ Teclado PT-BR está ativado no sistema."
            statusText1.setTextColor(Color.parseColor("#4ADE80")) // Verde
            btnEnable.isEnabled = false
            btnEnable.text = "Ativado com Sucesso"
        } else {
            statusText1.text = "✗ Teclado ainda não está ativado no Android."
            statusText1.setTextColor(Color.parseColor("#F87171")) // Vermelho
            btnEnable.isEnabled = true
            btnEnable.text = "Ativar nas Configurações"
        }

        if (isSelected) {
            statusText2.text = "✓ Teclado PT-BR é o teclado ativo no momento."
            statusText2.setTextColor(Color.parseColor("#4ADE80"))
            btnSelect.isEnabled = false
            btnSelect.text = "Teclado em Uso"
        } else {
            statusText2.text = "Toque abaixo para selecionar o Teclado PT-BR."
            statusText2.setTextColor(Color.parseColor("#CBD5E1"))
            btnSelect.isEnabled = true
            btnSelect.text = "Escolher como Ativo"
        }
    }

    private fun createSectionHeader(title: String): TextView {
        return TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(Color.parseColor("#F8FAFC"))
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(0, 0, 0, (4 * resources.displayMetrics.density).toInt())
        }
    }

    private fun createStyledButton(label: String, onClick: () -> Unit): Button {
        val density = resources.displayMetrics.density
        return Button(this).apply {
            text = label
            setBackgroundColor(Color.parseColor("#2563EB")) // Azul moderno
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding((16 * density).toInt(), (12 * density).toInt(), (16 * density).toInt(), (12 * density).toInt())
            setOnClickListener { onClick() }
        }
    }
}
