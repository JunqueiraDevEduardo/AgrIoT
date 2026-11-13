package pt.isep.agriot

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = ContextCompat.getColor(this, R.color.bg_dark)
        setContentView(R.layout.activity_login)

        val etId: EditText = findViewById(R.id.etId)
        val etPass: EditText = findViewById(R.id.etPass)
        val btn: Button = findViewById(R.id.btnEntrar)
        wireEye(etPass, findViewById<ImageButton>(R.id.eyePass))

        findViewById<TextView>(R.id.tvForgot).setOnClickListener {
            Toast.makeText(this, "Contacte o administrador para repor a palavra-passe.", Toast.LENGTH_LONG).show()
        }

        // Pressão longa no logótipo: configurar o endereço do servidor (vazio = modo demonstração)
        findViewById<android.view.View>(R.id.brand).setOnLongClickListener {
            showServerDialog()
            true
        }

        btn.setOnClickListener {
            val id = etId.text.toString().trim()
            val pass = etPass.text.toString()
            if (id.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Preencha o ID Cliente e a Palavra-passe", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            btn.isEnabled = false
            btn.text = "A ENTRAR..."
            runAsync({ Backend.repo().login(id, pass) }) { r ->
                btn.isEnabled = true
                btn.text = "ENTRAR"
                r.onSuccess { u ->
                    Session.id = u.id
                    Session.nome = u.nome
                    Session.estufa = u.estufa
                    Session.admin = u.perfil == "admin"
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
                r.onFailure {
                    Toast.makeText(this, it.message ?: "Erro ao iniciar sessão", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showServerDialog() {
        val et = EditText(this).apply {
            hint = "http://100.x.y.z:5000"
            setText(Backend.url)
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle("Servidor")
            .setMessage("Endereço do servidor Flask (Tailscale). Deixe vazio para usar o modo demonstração.")
            .setView(et)
            .setPositiveButton("Guardar") { _, _ ->
                Backend.url = et.text.toString()
                Toast.makeText(
                    this,
                    if (Backend.url.isBlank()) "Modo demonstração" else "Servidor: ${Backend.url}",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
