package pt.isep.agriot

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import kotlin.math.roundToInt

class PidFragment : Fragment(R.layout.fragment_pid) {

    private class Range(val min: Double, val step: Double, val max: Double)

    private val h = Handler(Looper.getMainLooper())
    private lateinit var chart: LineChartView
    private lateinit var chip: TextView
    private lateinit var btnPausar: Button
    private lateinit var mOvershoot: TextView
    private lateinit var mTempo: TextView
    private lateinit var mErro: TextView

    private val tick = object : Runnable {
        override fun run() {
            if (PidSim.running) PidSim.step()
            refresh()
            h.postDelayed(this, 100) // 1 s simulado = 100 ms reais
        }
    }

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        chart = v.findViewById(R.id.chartPid)
        chart.maxPoints = PidSim.MAX_POINTS
        chart.yMin = 15.0
        chart.yMax = 40.0
        chip = v.findViewById(R.id.chipSim)
        btnPausar = v.findViewById(R.id.btnPausar)
        mOvershoot = v.findViewById(R.id.mOvershoot)
        mTempo = v.findViewById(R.id.mTempo)
        mErro = v.findViewById(R.id.mErro)

        btnPausar.setOnClickListener {
            PidSim.running = !PidSim.running
            refresh()
        }
        v.findViewById<Button>(R.id.btnRepor).setOnClickListener {
            PidSim.reset()
            refresh()
        }
        bindSliders()

        runAsync({ Backend.repo().pid() }) { r ->
            if (!isAdded || view == null) return@runAsync
            r.onSuccess {
                PidSim.setpoint = it.setpoint; PidSim.kp = it.kp; PidSim.ki = it.ki; PidSim.kd = it.kd
                bindSliders()
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

    private fun bindSliders() {
        val v = requireView()
        slider(v, R.id.sbSetpoint, R.id.tvSetpoint, Range(15.0, 0.5, 40.0), { PidSim.setpoint }, { PidSim.setpoint = it }) { fmt(it) + " °C" }
        slider(v, R.id.sbKp, R.id.tvKp, Range(0.0, 0.5, 50.0), { PidSim.kp }, { PidSim.kp = it }) { fmt(it) }
        slider(v, R.id.sbKi, R.id.tvKi, Range(0.0, 0.05, 5.0), { PidSim.ki }, { PidSim.ki = it }) { fmt(it, 2) }
        slider(v, R.id.sbKd, R.id.tvKd, Range(0.0, 1.0, 100.0), { PidSim.kd }, { PidSim.kd = it }) { fmt(it) }
    }

    private fun slider(
        v: View, sbId: Int, tvId: Int, r: Range,
        get: () -> Double, set: (Double) -> Unit, format: (Double) -> String
    ) {
        val sb = v.findViewById<SeekBar>(sbId)
        val tv = v.findViewById<TextView>(tvId)
        sb.setOnSeekBarChangeListener(null)
        sb.max = ((r.max - r.min) / r.step).roundToInt()
        sb.progress = ((get() - r.min) / r.step).roundToInt()
        tv.text = format(get())
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                val value = r.min + p * r.step
                if (fromUser) set(value)
                tv.text = format(value)
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {
                val p = PidParams(PidSim.setpoint, PidSim.kp, PidSim.ki, PidSim.kd)
                runAsync({ Backend.repo().setPid(p) }) { }
            }
        })
    }

    private fun refresh() {
        if (!isAdded || view == null) return
        val ctx = requireContext()
        val toRange = { out: Double -> 15.0 + out / 100.0 * 25.0 }
        chart.setData(
            listOf(
                LineChartView.Serie(PidSim.saidas.map(toRange), ContextCompat.getColor(ctx, R.color.icon_blue), width = 1.5f),
                LineChartView.Serie(PidSim.setpoints, ContextCompat.getColor(ctx, R.color.icon_orange), dashed = true, width = 2f),
                LineChartView.Serie(PidSim.temps, ContextCompat.getColor(ctx, R.color.green_primary), width = 3f)
            )
        )
        mOvershoot.text = fmtPt(PidSim.overshootPercent()) + " %"
        val t = PidSim.tempoResposta()
        mTempo.text = if (t < 0) "-- s" else "$t s"
        mErro.text = fmtPt(PidSim.erroFinal(), 2) + " °C"
        chip.text = if (PidSim.running) "A simular" else "Em pausa"
        btnPausar.text = if (PidSim.running) "PAUSAR" else "RETOMAR"
    }
}
