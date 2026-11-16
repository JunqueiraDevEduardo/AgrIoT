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

/** Simulação do aquecimento por histerese (relé): liga em T < Tset − h, desliga em T ≥ Tset. */
class HistFragment : Fragment(R.layout.fragment_hist) {

    private class Range(val min: Double, val step: Double, val max: Double)

    private val h = Handler(Looper.getMainLooper())
    private lateinit var chart: LineChartView
    private lateinit var chip: TextView
    private lateinit var btnPausar: Button
    private lateinit var mComut: TextView
    private lateinit var mLigado: TextView
    private lateinit var mAmpl: TextView

    private val tick = object : Runnable {
        override fun run() {
            if (HistSim.running) HistSim.step()
            refresh()
            h.postDelayed(this, 100) // 1 s simulado = 100 ms reais
        }
    }

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        chart = v.findViewById(R.id.chartHist)
        chart.maxPoints = HistSim.MAX_POINTS
        chart.yMin = 14.0
        chart.yMax = 36.0
        chip = v.findViewById(R.id.chipSimH)
        btnPausar = v.findViewById(R.id.btnPausarH)
        mComut = v.findViewById(R.id.hComut)
        mLigado = v.findViewById(R.id.hLigado)
        mAmpl = v.findViewById(R.id.hAmpl)

        btnPausar.setOnClickListener { HistSim.running = !HistSim.running; refresh() }
        v.findViewById<Button>(R.id.btnReporH).setOnClickListener { HistSim.reset(); refresh() }
        bindSliders()

        runAsync({ Backend.repo().histerese() }) { r ->
            if (!isAdded || view == null) return@runAsync
            r.onSuccess {
                Params.setpoint = it.setpoint; Params.h = it.h
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
        slider(v, R.id.sbSetpointH, R.id.tvSetpointH, Range(15.0, 0.5, 40.0), { Params.setpoint }, { Params.setpoint = it }) { fmt(it) + " °C" }
        slider(v, R.id.sbH, R.id.tvH, Range(0.5, 0.5, 6.0), { Params.h }, { Params.h = it }) { fmt(it) + " °C" }
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
                val p = HistParams(Params.setpoint, Params.h)
                runAsync({ Backend.repo().setHisterese(p) }) { }
            }
        })
    }

    private fun refresh() {
        if (!isAdded || view == null) return
        val ctx = requireContext()
        val rele = HistSim.reles.map { if (it) 18.5 else 15.5 }
        chart.setData(
            listOf(
                LineChartView.Serie(rele, ContextCompat.getColor(ctx, R.color.icon_purple), width = 1.8f),
                LineChartView.Serie(HistSim.limites, ContextCompat.getColor(ctx, R.color.icon_blue), dashed = true, width = 2f),
                LineChartView.Serie(HistSim.setpoints, ContextCompat.getColor(ctx, R.color.icon_orange), dashed = true, width = 2f),
                LineChartView.Serie(HistSim.temps, ContextCompat.getColor(ctx, R.color.green_primary), width = 3f)
            )
        )
        mComut.text = HistSim.comutacoes.toString()
        mLigado.text = fmtPt(HistSim.tempoLigadoPercent(), 0) + " %"
        mAmpl.text = fmtPt(HistSim.amplitude(), 1) + " °C"
        chip.text = if (!HistSim.running) "Em pausa" else if (HistSim.rele) "Relé ligado" else "Relé desligado"
        btnPausar.text = if (HistSim.running) "PAUSAR" else "RETOMAR"
    }
}
