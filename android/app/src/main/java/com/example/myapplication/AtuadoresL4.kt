package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageButton
import android.widget.Switch
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

class AtuadoresL4 : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.atuadores_l4)
        Log.d("***TAG", "onCreate")

        val voltar: ImageButton = findViewById(R.id.voltar)
        voltar.setOnClickListener { finish() }

        val estadoLuz: TextView = findViewById(R.id.LUZ)
        val switchLuz: Switch = findViewById(R.id.switchLuz)
        switchLuz.setOnCheckedChangeListener { _, isChecked ->
            estadoLuz.text = if (isChecked) "Ligada" else "Desligada"
            // TODO: enviar comando real para o atuador "Luz"
        }

        val estadoVentoinha: TextView = findViewById(R.id.Ventoinha)
        val switchVentoinha: Switch = findViewById(R.id.switchVentoinha)
        switchVentoinha.setOnCheckedChangeListener { _, isChecked ->
            estadoVentoinha.text = if (isChecked) "Ligada" else "Desligada"
            // TODO: enviar comando real para o atuador "Ventoinha"
        }

        val estadoAquecedor: TextView = findViewById(R.id.aquecedorEstado)
        val switchAquecedor: Switch = findViewById(R.id.switchAquecedor)
        switchAquecedor.setOnCheckedChangeListener { _, isChecked ->
            estadoAquecedor.text = if (isChecked) "Ligada" else "Desligada"
            // TODO: enviar comando real para o atuador "Aquecedor"
        }

        val switchPid: Switch = findViewById(R.id.switchPid)
        switchPid.setOnCheckedChangeListener { _, _ ->
            // TODO: ativar/desativar controlo PID
        }

        val configurarPi: Button = findViewById(R.id.btnConfigurarPi)
        configurarPi.setOnClickListener {
            startActivity(Intent(this, PiAquecimentoL6::class.java))
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