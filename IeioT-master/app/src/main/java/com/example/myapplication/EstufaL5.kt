package com.example.myapplication

import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

class EstufaL5 : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.estufa_l5)
        Log.d("***TAG", "onCreate")

        val voltar: ImageButton = findViewById(R.id.voltar)
        voltar.setOnClickListener { finish() }

        // TODO: substituir os valores estáticos (estado atual, atuadores, histórico
        //       e mínimo/média/máximo) pelos dados reais da estufa.
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
