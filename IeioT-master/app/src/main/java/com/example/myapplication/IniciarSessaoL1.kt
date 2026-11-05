package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

class IniciarSessaoL1 : AppCompatActivity() {

    private var passwordVisivel = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.iniciar_sessao_l1)
        Log.d("***TAG", "onCreate")

        val idCliente: EditText = findViewById(R.id.insertidcliente)
        val palavraPasse: EditText = findViewById(R.id.insertpalavrapass)
        val togglePassword: ImageButton = findViewById(R.id.togglePassword)
        val entrar: Button = findViewById(R.id.entrar)

        // Mostrar / esconder a palavra-passe
        togglePassword.setOnClickListener {
            passwordVisivel = !passwordVisivel
            if (passwordVisivel) {
                palavraPasse.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                togglePassword.setImageResource(R.drawable.ic_eye_off)
            } else {
                palavraPasse.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                togglePassword.setImageResource(R.drawable.ic_eye)
            }
            palavraPasse.setSelection(palavraPasse.text.length)
        }

        // Clique no botão ENTRAR
        entrar.setOnClickListener {
            val id = idCliente.text.toString().trim()
            val pass = palavraPasse.text.toString().trim()

            if (id.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Preenche o ID Cliente e a Palavra-passe", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // TODO: validar credenciais junto do backend / dispositivo IoT
            val intent = Intent(this, HomePageL2::class.java)
            startActivity(intent)
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d("***TAG", "onPause")
    }

    override fun onStop() {
        super.onStop()
        Log.d("***TAG", "onStop")
    }

    override fun onStart() {
        super.onStart()
        Log.d("***TAG", "onStart")
    }
}
