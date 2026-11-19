package pt.isep.agriot

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import kotlin.math.roundToInt

class AtuadoresFragment : Fragment(R.layout.fragment_atuadores) {

    private val h = Handler(Looper.getMainLooper())
    private var loading = false
    private lateinit var rows: List<Triple<String, SwitchCompat, TextView>>
    private lateinit var swPwm: SwitchCompat
    private lateinit var stPwm: TextView
    private lateinit var sbVentoinhaPwm: SeekBar
    private lateinit var tvVentoinhaPwmVal: TextView
    private var currentDuty = 45.0

    private val tick = object : Runnable {
        override fun run() {
            load()
            h.postDelayed(this, 4000)
        }
    }

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        swPwm = v.findViewById(R.id.swPwm)
        stPwm = v.findViewById(R.id.stPwm)
        sbVentoinhaPwm = v.findViewById(R.id.sbVentoinhaPwm)
        tvVentoinhaPwmVal = v.findViewById(R.id.tvVentoinhaPwmVal)

        rows = listOf(
            Triple("luz", v.findViewById(R.id.swLuz), v.findViewById(R.id.stLuz)),
            Triple("ventoinha", v.findViewById(R.id.swVentoinha), v.findViewById(R.id.stVentoinha)),
            Triple("pwm", swPwm, stPwm),
            Triple("aquecedor", v.findViewById(R.id.swAquecedor), v.findViewById(R.id.stAquecedor)),
            Triple("pid", v.findViewById(R.id.swPid), v.findViewById(R.id.stPid)),
            Triple("histerese", v.findViewById(R.id.swHist), v.findViewById(R.id.stHist))
        )

        for ((nome, sw, st) in rows) {
            sw.setOnCheckedChangeListener { _, checked ->
                if (loading) return@setOnCheckedChangeListener
                st.text = if (checked) "Ligado" else "Desligado"
                val work: () -> Unit = when (nome) {
                    "pwm" -> {
                        { Backend.repo().setPwm(if (checked) (if (currentDuty > 0) currentDuty else 45.0) else 0.0, checked) }
                    }
                    "ventoinha" -> {
                        { Backend.repo().setPwm(if (checked) (if (currentDuty > 0) currentDuty else 45.0) else 0.0, checked) }
                    }
                    else -> {
                        { Backend.repo().setAtuador(nome, checked) }
                    }
                }
                runAsync(work) { r ->
                    if (!isAdded || view == null) return@runAsync
                    r.onFailure { Toast.makeText(requireContext(), it.message ?: "Erro", Toast.LENGTH_SHORT).show() }
                    load() // atualiza também os atuadores que dependem do PID / histerese
                }
            }
        }

        sbVentoinhaPwm.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentDuty = progress.toDouble()
                    val rpm = if (progress > 0) (600 + progress * 38) else 0
                    tvVentoinhaPwmVal.text = "$progress % · $rpm RPM"
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val p = seekBar?.progress ?: 0
                currentDuty = p.toDouble()
                runAsync({ Backend.repo().setPwm(currentDuty, p > 0) }) { r ->
                    if (!isAdded || view == null) return@runAsync
                    r.onFailure { Toast.makeText(requireContext(), it.message ?: "Erro", Toast.LENGTH_SHORT).show() }
                    load()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        h.post(tick)
    }

    override fun onPause() {
        super.onPause()
        h.removeCallbacksAndMessages(null)
    }

    private fun load() {
        runAsync({ Backend.repo().atuadores() }) { r ->
            if (!isAdded || view == null) return@runAsync
            r.onSuccess { a ->
                loading = true
                currentDuty = a.duty

                // Valores dos switches: luz, ventoinha, pwm, aquecedor, pid, histerese
                val ventLigada = a.ventoinha || a.duty > 0
                val pwmLigado = a.pwm || (ventLigada && !a.pid)
                val values = listOf(a.luz, ventLigada, pwmLigado, a.aquecedor, a.pid, a.histerese)
                rows.forEachIndexed { i, row -> row.second.isChecked = values[i] }

                fun onOff(b: Boolean) = if (b) "Ligada" else "Desligada"
                rows[0].third.text = onOff(a.luz)
                rows[1].third.text = when {
                    a.pid -> "PID · " + fmt(a.duty, 0) + " % · " + a.rpm + " RPM"
                    ventLigada -> "Ligada · " + fmt(a.duty, 0) + " % · " + a.rpm + " RPM"
                    else -> "Desligada"
                }
                rows[2].third.text = when {
                    a.pid -> "Bloqueado pelo PID"
                    pwmLigado -> "Ligado · " + fmt(a.duty, 0) + " % · " + a.rpm + " RPM"
                    else -> "Desligado"
                }
                rows[3].third.text = if (a.histerese) "Histerese · " + (if (a.aquecedor) "relé ligado" else "relé desligado") else onOff(a.aquecedor)
                rows[4].third.text = if (a.pid) "Ventoinha controlada pelo PID" else "Desligado"
                rows[5].third.text = if (a.histerese) "Aquecedor controlado por histerese" else "Desligada"

                // Slider PWM de rotações
                sbVentoinhaPwm.progress = a.duty.roundToInt()
                val rpmEstimado = if (a.rpm > 0) a.rpm else if (a.duty > 0) (600 + a.duty * 38).toInt() else 0
                tvVentoinhaPwmVal.text = "${fmt(a.duty, 0)} % · $rpmEstimado RPM"

                // atuador automático: o interruptor manual fica bloqueado
                val manualPermitido = !a.pid
                rows[1].second.isEnabled = manualPermitido
                rows[1].second.alpha = if (manualPermitido) 1f else 0.45f
                rows[2].second.isEnabled = manualPermitido
                rows[2].second.alpha = if (manualPermitido) 1f else 0.45f
                sbVentoinhaPwm.isEnabled = manualPermitido
                sbVentoinhaPwm.alpha = if (manualPermitido) 1f else 0.45f

                rows[3].second.isEnabled = !a.histerese
                rows[3].second.alpha = if (a.histerese) 0.45f else 1f

                loading = false
            }
            r.onFailure { Toast.makeText(requireContext(), it.message ?: "Erro", Toast.LENGTH_SHORT).show() }
        }
    }
}
