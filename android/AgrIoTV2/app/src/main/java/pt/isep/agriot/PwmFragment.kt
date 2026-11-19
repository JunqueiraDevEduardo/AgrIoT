package pt.isep.agriot

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import kotlin.math.roundToInt

/**
 * Controlo e monitorização por PWM da velocidade da ventoinha (secção 3.1 do relatório).
 * Permite regular manualmente as rotações por minuto (RPM) sem ficar fixo a 100%.
 */
class PwmFragment : Fragment(R.layout.fragment_pwm) {

    private val h = Handler(Looper.getMainLooper())
    private var loading = false
    private var currentDuty = 45.0
    private var pidAtivo = false

    private lateinit var chipEstado: TextView
    private lateinit var tvDutyVal: TextView
    private lateinit var tvRpmVal: TextView
    private lateinit var tvSliderDutyDisplay: TextView
    private lateinit var swPwmAtivo: SwitchCompat
    private lateinit var sbDuty: SeekBar
    private lateinit var cardPidWarning: View
    private lateinit var btnDesativarPid: Button

    private lateinit var btnPwm0: Button
    private lateinit var btnPwm25: Button
    private lateinit var btnPwm50: Button
    private lateinit var btnPwm75: Button
    private lateinit var btnPwm100: Button

    private val tick = object : Runnable {
        override fun run() {
            load()
            h.postDelayed(this, 3000)
        }
    }

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        chipEstado = v.findViewById(R.id.chipPwmEstado)
        tvDutyVal = v.findViewById(R.id.tvPwmDutyVal)
        tvRpmVal = v.findViewById(R.id.tvPwmRpmVal)
        tvSliderDutyDisplay = v.findViewById(R.id.tvSliderDutyDisplay)
        swPwmAtivo = v.findViewById(R.id.swPwmAtivo)
        sbDuty = v.findViewById(R.id.sbDuty)
        cardPidWarning = v.findViewById(R.id.cardPidWarning)
        btnDesativarPid = v.findViewById(R.id.btnDesativarPid)

        btnPwm0 = v.findViewById(R.id.btnPwm0)
        btnPwm25 = v.findViewById(R.id.btnPwm25)
        btnPwm50 = v.findViewById(R.id.btnPwm50)
        btnPwm75 = v.findViewById(R.id.btnPwm75)
        btnPwm100 = v.findViewById(R.id.btnPwm100)

        // Switch principal de controlo manual PWM
        swPwmAtivo.setOnCheckedChangeListener { _, checked ->
            if (loading) return@setOnCheckedChangeListener
            if (pidAtivo) {
                Toast.makeText(requireContext(), "Desative o PID para controlo manual", Toast.LENGTH_SHORT).show()
                swPwmAtivo.isChecked = false
                return@setOnCheckedChangeListener
            }
            val targetDuty = if (checked) (if (currentDuty > 0) currentDuty else 45.0) else 0.0
            sendPwm(targetDuty, checked)
        }

        // SeekBar do Duty Cycle
        sbDuty.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentDuty = progress.toDouble()
                    val rpmCalc = if (progress > 0) (600 + progress * 38) else 0
                    tvSliderDutyDisplay.text = "$progress %"
                    tvDutyVal.text = "$progress %"
                    tvRpmVal.text = "$rpmCalc RPM"
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val p = seekBar?.progress ?: 0
                currentDuty = p.toDouble()
                sendPwm(currentDuty, p > 0)
            }
        })

        // Níveis pré-definidos rápidos
        fun preset(d: Double) {
            if (pidAtivo) {
                Toast.makeText(requireContext(), "Desative o PID para controlo manual", Toast.LENGTH_SHORT).show()
                return
            }
            currentDuty = d
            sbDuty.progress = d.roundToInt()
            tvSliderDutyDisplay.text = "${d.roundToInt()} %"
            sendPwm(d, d > 0)
        }

        btnPwm0.setOnClickListener { preset(0.0) }
        btnPwm25.setOnClickListener { preset(25.0) }
        btnPwm50.setOnClickListener { preset(50.0) }
        btnPwm75.setOnClickListener { preset(75.0) }
        btnPwm100.setOnClickListener { preset(100.0) }

        // Botão para desativar PID e assumir controlo manual PWM
        btnDesativarPid.setOnClickListener {
            runAsync({
                Backend.repo().setAtuador("pid", false)
                Backend.repo().setPwm(currentDuty.coerceAtLeast(40.0), true)
            }) { r ->
                if (!isAdded || view == null) return@runAsync
                r.onSuccess {
                    Toast.makeText(requireContext(), "PID desativado. Modo PWM manual ativo.", Toast.LENGTH_SHORT).show()
                    load()
                }
                r.onFailure { Toast.makeText(requireContext(), it.message ?: "Erro", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        h.post(tick)
    }

    override fun onPause() {
        super.onPause()
        h.removeCallbacksAndMessages(null)
    }

    private fun sendPwm(duty: Double, ligado: Boolean) {
        runAsync({ Backend.repo().setPwm(duty, ligado) }) { r ->
            if (!isAdded || view == null) return@runAsync
            r.onFailure {
                Toast.makeText(requireContext(), it.message ?: "Erro ao definir PWM", Toast.LENGTH_SHORT).show()
            }
            load()
        }
    }

    private fun load() {
        runAsync({ Backend.repo().atuadores() }) { r ->
            if (!isAdded || view == null) return@runAsync
            r.onSuccess { a ->
                loading = true
                pidAtivo = a.pid
                currentDuty = a.duty

                tvDutyVal.text = fmt(a.duty, 0) + " %"
                tvRpmVal.text = if (a.ventoinha && a.rpm > 0) "${a.rpm} RPM" else if (a.duty > 0) "${(600 + a.duty * 38).toInt()} RPM" else "0 RPM"
                tvSliderDutyDisplay.text = fmt(a.duty, 0) + " %"
                sbDuty.progress = a.duty.roundToInt()

                val ventLigada = a.ventoinha || (a.duty > 0)
                swPwmAtivo.isChecked = ventLigada && !a.pid

                when {
                    a.pid -> {
                        chipEstado.text = "PID Automático"
                        chipEstado.setBackgroundResource(R.drawable.bg_chip_warn)
                        chipEstado.setTextColor(ContextCompat.getColor(requireContext(), R.color.chip_warn_text))
                        cardPidWarning.visibility = View.VISIBLE
                    }
                    ventLigada -> {
                        chipEstado.text = "Manual · " + fmt(a.duty, 0) + " %"
                        chipEstado.setBackgroundResource(R.drawable.bg_chip_green)
                        chipEstado.setTextColor(ContextCompat.getColor(requireContext(), R.color.chip_green_text))
                        cardPidWarning.visibility = View.GONE
                    }
                    else -> {
                        chipEstado.text = "Desligada"
                        chipEstado.setBackgroundResource(R.drawable.bg_chip_gray)
                        chipEstado.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                        cardPidWarning.visibility = View.GONE
                    }
                }

                // Quando o PID está ativo, o controlo manual fica bloqueado
                val manualPermitido = !a.pid
                swPwmAtivo.isEnabled = manualPermitido
                swPwmAtivo.alpha = if (manualPermitido) 1f else 0.45f
                sbDuty.isEnabled = manualPermitido
                sbDuty.alpha = if (manualPermitido) 1f else 0.45f
                listOf(btnPwm0, btnPwm25, btnPwm50, btnPwm75, btnPwm100).forEach {
                    it.isEnabled = manualPermitido
                    it.alpha = if (manualPermitido) 1f else 0.45f
                }

                loading = false
            }
            r.onFailure { Toast.makeText(requireContext(), it.message ?: "Erro", Toast.LENGTH_SHORT).show() }
        }
    }
}
