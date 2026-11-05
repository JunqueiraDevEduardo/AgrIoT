package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomePageL2 : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var relogio: TextView

    private val atualizarRelogio = object : Runnable {
        override fun run() {
            val formato = SimpleDateFormat("HH:mm:ss EEE dd/MM", Locale("pt", "PT"))
            relogio.text = formato.format(Date())
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.home_page_l2)
        Log.d("***TAG", "onCreate")

        relogio = findViewById(R.id.relogio)

        val menuSensores: LinearLayout = findViewById(R.id.menuSensores)
        val menuAtuadores: LinearLayout = findViewById(R.id.menuAtuadores)
        val menuEstufa: LinearLayout = findViewById(R.id.menuEstufa)
        val logout: ImageButton = findViewById(R.id.logout)

        menuSensores.setOnClickListener {
            startActivity(Intent(this, SensoresL3::class.java))
        }

        menuAtuadores.setOnClickListener {
            startActivity(Intent(this, AtuadoresL4::class.java))
        }

        menuEstufa.setOnClickListener {
            // TODO: criar ecrã "Estufa" (estado geral e histórico)
        }

        logout.setOnClickListener {
            val intent = Intent(this, IniciarSessaoL1::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d("***TAG", "onStart")
        handler.post(atualizarRelogio)
    }

    override fun onPause() {
        super.onPause()
        Log.d("***TAG", "onPause")
    }

    override fun onStop() {
        super.onStop()
        Log.d("***TAG", "onStop")
        handler.removeCallbacks(atualizarRelogio)
    }
}
