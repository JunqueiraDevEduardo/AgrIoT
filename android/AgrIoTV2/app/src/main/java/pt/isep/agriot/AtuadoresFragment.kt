package pt.isep.agriot

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment

class AtuadoresFragment : Fragment(R.layout.fragment_atuadores) {

    private val h = Handler(Looper.getMainLooper())
    private var loading = false
    private lateinit var rows: List<Triple<String, SwitchCompat, TextView>>

    private val tick = object : Runnable {
        override fun run() {
            load()
            h.postDelayed(this, 5000)
        }
    }

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        rows = listOf(
            Triple("luz", v.findViewById<SwitchCompat>(R.id.swLuz), v.findViewById<TextView>(R.id.stLuz)),
            Triple("ventoinha", v.findViewById<SwitchCompat>(R.id.swVentoinha), v.findViewById<TextView>(R.id.stVentoinha)),
            Triple("aquecedor", v.findViewById<SwitchCompat>(R.id.swAquecedor), v.findViewById<TextView>(R.id.stAquecedor)),
            Triple("pid", v.findViewById<SwitchCompat>(R.id.swPid), v.findViewById<TextView>(R.id.stPid)),
            Triple("histerese", v.findViewById<SwitchCompat>(R.id.swHist), v.findViewById<TextView>(R.id.stHist))
        )
        for ((nome, sw, st) in rows) {
            sw.setOnCheckedChangeListener { _, checked ->
                if (loading) return@setOnCheckedChangeListener
                st.text = if (checked) "Ligada" else "Desligada"
                runAsync({ Backend.repo().setAtuador(nome, checked) }) { r ->
                    if (!isAdded || view == null) return@runAsync
                    r.onFailure { Toast.makeText(requireContext(), it.message ?: "Erro", Toast.LENGTH_SHORT).show() }
                    load() // atualiza também os atuadores que dependem do PID / histerese
                }
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

    private fun load() {
        runAsync({ Backend.repo().atuadores() }) { r ->
            if (!isAdded || view == null) return@runAsync
            r.onSuccess { a ->
                loading = true
                val values = listOf(a.luz, a.ventoinha, a.aquecedor, a.pid, a.histerese)
                rows.forEachIndexed { i, row -> row.second.isChecked = values[i] }
                fun onOff(b: Boolean) = if (b) "Ligada" else "Desligada"
                rows[0].third.text = onOff(a.luz)
                rows[1].third.text = when {
                    a.pid -> "PID · " + fmt(a.duty, 0) + " % · " + a.rpm + " RPM"
                    a.ventoinha -> "Ligada · " + fmt(a.duty, 0) + " % · " + a.rpm + " RPM"
                    else -> "Desligada"
                }
                rows[2].third.text = if (a.histerese) "Histerese · " + (if (a.aquecedor) "relé ligado" else "relé desligado") else onOff(a.aquecedor)
                rows[3].third.text = if (a.pid) "Ventoinha controlada pelo PID" else "Desligado"
                rows[4].third.text = if (a.histerese) "Aquecedor controlado por histerese" else "Desligada"
                // atuador automático: o interruptor manual fica bloqueado
                rows[1].second.isEnabled = !a.pid
                rows[1].second.alpha = if (a.pid) 0.45f else 1f
                rows[2].second.isEnabled = !a.histerese
                rows[2].second.alpha = if (a.histerese) 0.45f else 1f
                loading = false
            }
            r.onFailure { Toast.makeText(requireContext(), it.message ?: "Erro", Toast.LENGTH_SHORT).show() }
        }
    }
}
