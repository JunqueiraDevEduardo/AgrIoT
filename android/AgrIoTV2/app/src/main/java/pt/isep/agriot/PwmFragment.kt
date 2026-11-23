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
 * Ecrã de Controlo e Simulação da Ventoinha por PWM.
 * Permite simulação gráfica contínua da resposta térmica e ajuste manual de rotações.
 */
class PwmFragment : Fragment(R.layout.fragment_pwm) {

    private val h = Handler(Looper.getMainLooper())
    private var loading = false
    private var pidAtivo = false

    private lateinit var chart: LineChartView
    private lateinit var chipSimPwm: TextView
    private lateinit var tvPwmSimRpm: TextView
    private lateinit var tvPwmSimTeq: TextView
    private lateinit var tvPwmSimTaxa: TextView
    private lateinit var btnPausarPwm: Button
    private lateinit var btnReporPwm: Button

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

    private val tickSim = object : Runnable {
        override fun run() {
            if (PwmSim.running) PwmSim.step()
            refreshSim()
            h.postDelayed(this, 100) // 1 s simulado = 100 ms reais
        }
    }

    private val tickTelemetry = object : Runnable {
        override fun run() {
            loadTelemetry()
            h.postDelayed(this, 3000)
        }
    }

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        chart = v.findViewById(R.id.chartPwm)
        chart.maxPoints = PwmSim.MAX_POINTS
        chart.yMin = 15.0
        chart.yMax = 40.0

        chipSimPwm = v.findViewById(R.id.chipSimPwm)
        tvPwmSimRpm = v.findViewById(R.id.tvPwmSimRpm)
        tvPwmSimTeq = v.findViewById(R.id.tvPwmSimTeq)
        tvPwmSimTaxa = v.findViewById(R.id.tvPwmSimTaxa)
        btnPausarPwm = v.findViewById(R.id.btnPausarPwm)
        btnReporPwm = v.findViewById(R.id.btnReporPwm)

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

        // Controlo da simulação (Pausar / Repor)
        btnPausarPwm.setOnClickListener {
            PwmSim.running = !PwmSim.running
            refreshSim()
        }
        btnReporPwm.setOnClickListener {
            PwmSim.reset()
            refreshSim()
        }

        // Switch de ativação manual do PWM
        swPwmAtivo.setOnCheckedChangeListener { _, checked ->
            if (loading) return@setOnCheckedChangeListener
            val targetDuty = if (checked) (if (PwmSim.duty > 0) PwmSim.duty else 45.0) else 0.0
            applyPwm(targetDuty, checked)
        }

        // Slider contínuo de PWM
        sbDuty.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val d = progress.toDouble()
                    PwmSim.setDuty(d)
                    updateSliderLabel(d)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val p = seekBar?.progress ?: 0
                val d = p.toDouble()
                applyPwm(d, p > 0)
            }
        })

        fun preset(d: Double) {
            sbDuty.progress = d.roundToInt()
            applyPwm(d, d > 0)
        }

        btnPwm0.setOnClickListener { preset(0.0) }
        btnPwm25.setOnClickListener { preset(25.0) }
        btnPwm50.setOnClickListener { preset(50.0) }
        btnPwm75.setOnClickListener { preset(75.0) }
        btnPwm100.setOnClickListener { preset(100.0) }

        // Botão para desativar PID caso esteja em controlo automático
        btnDesativarPid.setOnClickListener {
            runAsync({
                Backend.repo().setAtuador("pid", false)
                Backend.repo().setPwm(PwmSim.duty.coerceAtLeast(40.0), true)
            }) { r ->
                if (!isAdded || view == null) return@runAsync
                r.onSuccess {
                    Toast.makeText(requireContext(), "PID desativado. Modo PWM manual ativo.", Toast.LENGTH_SHORT).show()
                    loadTelemetry()
                }
                r.onFailure { Toast.makeText(requireContext(), it.message ?: "Erro", Toast.LENGTH_SHORT).show() }
            }
        }

        updateSliderLabel(PwmSim.duty)
        refreshSim()
    }

    override fun onResume() {
        super.onResume()
        h.post(tickSim)
        h.post(tickTelemetry)
    }

    override fun onPause() {
        super.onPause()
        h.removeCallbacksAndMessages(null)
    }

    private fun applyPwm(duty: Double, ligado: Boolean) {
        val target = if (ligado) duty else 0.0
        PwmSim.setDuty(target)
        updateSliderLabel(target)
        runAsync({ Backend.repo().setPwm(target, ligado) }) { r ->
            if (!isAdded || view == null) return@runAsync
            r.onFailure {
                Toast.makeText(requireContext(), it.message ?: "Erro ao definir PWM", Toast.LENGTH_SHORT).show()
            }
            loadTelemetry()
        }
    }

    private fun updateSliderLabel(d: Double) {
        val rpm = if (d > 0.0) (600 + d * 38).toInt() else 0
        tvSliderDutyDisplay.text = "${d.roundToInt()} % · $rpm RPM"
    }

    private fun refreshSim() {
        if (!isAdded || view == null) return
        val ctx = requireContext()
        val toRange = { out: Double -> 15.0 + out / 100.0 * 25.0 }

        chart.setData(
            listOf(
                LineChartView.Serie(PwmSim.saidas.map(toRange), ContextCompat.getColor(ctx, R.color.icon_blue), width = 1.5f),
                LineChartView.Serie(PwmSim.alvos, ContextCompat.getColor(ctx, R.color.icon_orange), dashed = true, width = 2f),
                LineChartView.Serie(PwmSim.temps, ContextCompat.getColor(ctx, R.color.green_primary), width = 3f)
            )
        )

        tvPwmSimRpm.text = "${PwmSim.rpm}"
        tvPwmSimTeq.text = "${fmtPt(PwmSim.tempEstabilizada())} °C"
        tvPwmSimTaxa.text = "${fmtPt(PwmSim.taxaVariacao())} °C/m"
        chipSimPwm.text = if (PwmSim.running) "A simular" else "Em pausa"
        btnPausarPwm.text = if (PwmSim.running) "PAUSAR" else "RETOMAR"
    }

    private fun loadTelemetry() {
        runAsync({ Backend.repo().atuadores() }) { r ->
            if (!isAdded || view == null) return@runAsync
            r.onSuccess { a ->
                loading = true
                pidAtivo = a.pid
                cardPidWarning.visibility = if (a.pid) View.VISIBLE else View.GONE

                val ventLigada = a.ventoinha || a.duty > 0.0
                swPwmAtivo.isChecked = ventLigada && !a.pid

                val manualPermitido = !a.pid
                swPwmAtivo.isEnabled = manualPermitido
                swPwmAtivo.alpha = if (manualPermitido) 1f else 0.45f
                sbDuty.isEnabled = manualPermitido
                sbDuty.alpha = if (manualPermitido) 1f else 0.45f
                listOf(btnPwm0, btnPwm25, btnPwm50, btnPwm75, btnPwm100).forEach {
                    it.isEnabled = manualPermitido
                    it.alpha = if (manualPermitido) 1f else 0.45f
                }

                if (!a.pid && a.duty > 0.0) {
                    PwmSim.setDuty(a.duty)
                    sbDuty.progress = a.duty.roundToInt()
                    updateSliderLabel(a.duty)
                }
                loading = false
            }
        }
    }
}
