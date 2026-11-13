package pt.isep.agriot

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class SensoresFragment : Fragment(R.layout.fragment_sensores) {

    private val h = Handler(Looper.getMainLooper())
    private lateinit var chart: LineChartView
    private val tick = object : Runnable {
        override fun run() {
            load()
            h.postDelayed(this, 5000)
        }
    }

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        chart = v.findViewById(R.id.chartTemp)
    }

    override fun onResume() {
        super.onResume()
        h.post(tick)
    }

    override fun onPause() {
        super.onPause()
        h.removeCallbacksAndMessages(null)
    }

    private fun setChip(id: Int, value: Double, min: Double, max: Double, normalText: String = "Normal") {
        val tv = requireView().findViewById<TextView>(id)
        val ok = value in min..max
        tv.text = if (ok) normalText else if (value < min) "Baixo" else "Alto"
        tv.setBackgroundResource(if (ok) R.drawable.bg_chip_green else R.drawable.bg_chip_warn)
        tv.setTextColor(ContextCompat.getColor(requireContext(), if (ok) R.color.chip_green_text else R.color.chip_warn_text))
    }

    private fun load() {
        runAsync({ Backend.repo().sensores() }) { r ->
            if (!isAdded || view == null) return@runAsync
            r.onSuccess { s ->
                val v = requireView()
                v.findViewById<TextView>(R.id.valTemp).text = fmt(s.temperatura)
                v.findViewById<TextView>(R.id.valHumAr).text = fmt(s.humidadeAr, 0)
                v.findViewById<TextView>(R.id.valHumSolo).text = fmt(s.humidadeSolo, 0)
                v.findViewById<TextView>(R.id.valPressao).text = fmt(s.pressao, 0)
                setChip(R.id.chipTemp, s.temperatura, 18.0, 30.0)
                setChip(R.id.chipHumAr, s.humidadeAr, 40.0, 80.0)
                setChip(R.id.chipHumSolo, s.humidadeSolo, 30.0, 60.0)
                setChip(R.id.chipPressao, s.pressao, 980.0, 1040.0, "Normal ao nível do mar")
                chart.setData(
                    listOf(LineChartView.Serie(s.historico, ContextCompat.getColor(requireContext(), R.color.green_primary), fill = true))
                )
            }
            r.onFailure {
                Toast.makeText(requireContext(), it.message ?: "Erro", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
