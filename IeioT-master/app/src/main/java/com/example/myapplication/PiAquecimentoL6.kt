package com.example.myapplication

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

/**
 * Ecrã de controlo PI do aquecimento.
 *
 * Por agora a temperatura é SIMULADA (planta de 1.ª ordem) para se poder ver o PI
 * a funcionar. Para ligar ao sensor real, substituir [lerTemperatura] e aplicar a
 * saída (%) ao aquecedor em [aplicarSaida].
 */
class PiAquecimentoL6 : AppCompatActivity() {

    private companion object {
        const val PASSO_SEGUNDOS = 1.0
        const val SETPOINT_MIN = 15.0
        const val SETPOINT_MAX = 40.0
        const val SETPOINT_PASSO = 0.5
        const val TEMP_AMBIENTE = 20.0
    }

    private val handler = Handler(Looper.getMainLooper())
    private val pi = PiController(kp = 10.0, ki = 0.4)

    private var setpoint = 28.0
    private var temperatura = 22.0 // valor simulado
    private var piAtivo = false

    private lateinit var temperaturaAtual: TextView
    private lateinit var setpointValor: TextView
    private lateinit var saidaPercent: TextView
    private lateinit var barraSaida: ProgressBar
    private lateinit var estadoPi: TextView
    private lateinit var descricaoPi: TextView

    private val ciclo = object : Runnable {
        override fun run() {
            val medida = lerTemperatura()
            val saida = if (piAtivo) pi.atualizar(setpoint, medida, PASSO_SEGUNDOS) else 0.0
            aplicarSaida(saida)
            simular(saida)
            mostrar(saida)
            handler.postDelayed(this, (PASSO_SEGUNDOS * 1000).toLong())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.pi_aquecimento_l6)
        Log.d("***TAG", "onCreate")

        temperaturaAtual = findViewById(R.id.temperaturaAtual)
        setpointValor = findViewById(R.id.setpointValor)
        saidaPercent = findViewById(R.id.saidaPercent)
        barraSaida = findViewById(R.id.barraSaida)
        estadoPi = findViewById(R.id.estadoPi)
        descricaoPi = findViewById(R.id.descricaoPi)

        val voltar: ImageButton = findViewById(R.id.voltar)
        val menos: TextView = findViewById(R.id.btnSetpointMenos)
        val mais: TextView = findViewById(R.id.btnSetpointMais)
        val switchPi: Switch = findViewById(R.id.switchPi)
        val editKp: EditText = findViewById(R.id.editKp)
        val editKi: EditText = findViewById(R.id.editKi)
        val aplicar: Button = findViewById(R.id.btnAplicar)

        voltar.setOnClickListener { finish() }

        menos.setOnClickListener { alterarSetpoint(-SETPOINT_PASSO) }
        mais.setOnClickListener { alterarSetpoint(SETPOINT_PASSO) }

        switchPi.setOnCheckedChangeListener { _, isChecked ->
            piAtivo = isChecked
            pi.reset()
            atualizarEstado()
            // TODO: informar o dispositivo IoT de que o aquecedor passa a modo PI / manual
        }

        aplicar.setOnClickListener {
            val kp = editKp.text.toString().replace(',', '.').toDoubleOrNull()
            val ki = editKi.text.toString().replace(',', '.').toDoubleOrNull()
            if (kp == null || ki == null || kp < 0.0 || ki < 0.0) {
                Toast.makeText(this, "Kp e Ki têm de ser números ≥ 0", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            pi.kp = kp
            pi.ki = ki
            pi.reset()
            Toast.makeText(this, "Parâmetros aplicados", Toast.LENGTH_SHORT).show()
            // TODO: enviar Kp, Ki e setpoint ao dispositivo IoT
        }

        atualizarEstado()
        mostrar(0.0)
    }

    private fun alterarSetpoint(delta: Double) {
        setpoint = (setpoint + delta).coerceIn(SETPOINT_MIN, SETPOINT_MAX)
        setpointValor.text = formatarTemp(setpoint)
        // TODO: enviar o novo setpoint ao dispositivo IoT
    }

    /** TODO: devolver a leitura real do sensor de temperatura. */
    private fun lerTemperatura(): Double = temperatura

    /** TODO: enviar a saída (0-100 %) ao aquecedor (p. ex. como duty-cycle). */
    @Suppress("UNUSED_PARAMETER")
    private fun aplicarSaida(saida: Double) = Unit

    /** Planta simulada: arrefece para a temperatura ambiente e aquece com a saída. */
    private fun simular(saida: Double) {
        temperatura += ((TEMP_AMBIENTE - temperatura) + 0.3 * saida) / 40.0 * PASSO_SEGUNDOS
    }

    private fun mostrar(saida: Double) {
        temperaturaAtual.text = formatarTemp(temperatura)
        setpointValor.text = formatarTemp(setpoint)
        saidaPercent.text = String.format(Locale.US, "%.0f %%", saida)
        barraSaida.progress = saida.toInt()
    }

    private fun atualizarEstado() {
        estadoPi.text = if (piAtivo) "●  PI ativo" else "●  PI desligado"
        descricaoPi.text = if (piAtivo) "Aquecedor controlado pelo PI" else "Aquecedor em modo manual"
    }

    private fun formatarTemp(valor: Double) = String.format(Locale.US, "%.1f °C", valor)

    override fun onStart() {
        super.onStart()
        Log.d("***TAG", "onStart")
        handler.post(ciclo)
    }

    override fun onPause() {
        super.onPause()
        Log.d("***TAG", "onPause")
    }

    override fun onStop() {
        super.onStop()
        Log.d("***TAG", "onStop")
        handler.removeCallbacks(ciclo)
    }
}
