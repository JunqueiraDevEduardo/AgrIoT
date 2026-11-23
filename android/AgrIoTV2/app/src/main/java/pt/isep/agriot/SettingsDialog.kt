package pt.isep.agriot

import android.app.Activity
import android.app.Dialog
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

object SettingsDialog {

    fun show(activity: Activity) {
        val d = Dialog(activity)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        d.setContentView(R.layout.dialog_settings)
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)
        d.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.92).toInt(),
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        val cardPt = d.findViewById<View>(R.id.cardLangPt)
        val cardEn = d.findViewById<View>(R.id.cardLangEn)
        val ivCheckPt = d.findViewById<ImageView>(R.id.ivCheckPt)
        val ivCheckEn = d.findViewById<ImageView>(R.id.ivCheckEn)
        val tvServerUrl = d.findViewById<TextView>(R.id.tvServerUrlDisplay)
        val btnConfigServer = d.findViewById<Button>(R.id.btnConfigServer)
        val btnFechar = d.findViewById<Button>(R.id.btnFecharSettings)

        var selectedLang = LanguageManager.getLanguage(activity)

        fun updateLangSelection() {
            val isEn = selectedLang == "en"
            ivCheckPt.visibility = if (isEn) View.INVISIBLE else View.VISIBLE
            ivCheckEn.visibility = if (isEn) View.VISIBLE else View.INVISIBLE
            cardPt.setBackgroundResource(if (!isEn) R.drawable.bg_chip_green else R.drawable.bg_tile)
            cardEn.setBackgroundResource(if (isEn) R.drawable.bg_chip_green else R.drawable.bg_tile)
        }

        updateLangSelection()

        cardPt.setOnClickListener {
            selectedLang = "pt"
            updateLangSelection()
        }

        cardEn.setOnClickListener {
            selectedLang = "en"
            updateLangSelection()
        }

        fun updateServerDisplay() {
            tvServerUrl.text = if (Backend.url.isBlank()) {
                if (selectedLang == "en") "Demo Mode (Local simulation)" else "Modo Demonstração (Simulado)"
            } else {
                Backend.url
            }
        }
        updateServerDisplay()

        btnConfigServer.setOnClickListener {
            val et = EditText(activity).apply {
                hint = "http://100.x.y.z:5000"
                setText(Backend.url)
                setPadding(48, 32, 48, 32)
            }
            AlertDialog.Builder(activity)
                .setTitle(if (selectedLang == "en") "Server Configuration" else "Configuração do Servidor")
                .setMessage(
                    if (selectedLang == "en")
                        "Flask server IP (Tailscale). Leave empty for demo mode."
                    else
                        "Endereço IP do servidor Flask (Tailscale). Deixe vazio para modo demonstração."
                )
                .setView(et)
                .setPositiveButton(if (selectedLang == "en") "Save" else "Guardar") { _, _ ->
                    Backend.url = et.text.toString()
                    updateServerDisplay()
                    Toast.makeText(
                        activity,
                        if (Backend.url.isBlank()) {
                            if (selectedLang == "en") "Demo Mode active" else "Modo demonstração ativo"
                        } else {
                            "Servidor: ${Backend.url}"
                        },
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .setNegativeButton(if (selectedLang == "en") "Cancel" else "Cancelar", null)
                .show()
        }

        btnFechar.setOnClickListener {
            d.dismiss()
            if (selectedLang != LanguageManager.getLanguage(activity)) {
                LanguageManager.setLanguage(activity, selectedLang)
            }
        }

        d.show()
    }
}
