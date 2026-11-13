package pt.isep.agriot

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment

class AtuadoresFragment : Fragment(R.layout.fragment_atuadores) {

    private var loading = false
    private lateinit var rows: List<Triple<String, SwitchCompat, TextView>>

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        rows = listOf(
            Triple("luz", v.findViewById<SwitchCompat>(R.id.swLuz), v.findViewById<TextView>(R.id.stLuz)),
            Triple("ventoinha", v.findViewById<SwitchCompat>(R.id.swVentoinha), v.findViewById<TextView>(R.id.stVentoinha)),
            Triple("aquecedor", v.findViewById<SwitchCompat>(R.id.swAquecedor), v.findViewById<TextView>(R.id.stAquecedor)),
            Triple("pid", v.findViewById<SwitchCompat>(R.id.swPid), v.findViewById<TextView>(R.id.stPid))
        )
        for ((nome, sw, st) in rows) {
            sw.setOnCheckedChangeListener { _, checked ->
                if (loading) return@setOnCheckedChangeListener
                st.text = if (checked) "Ligada" else "Desligada"
                runAsync({ Backend.repo().setAtuador(nome, checked) }) { r ->
                    if (!isAdded || view == null) return@runAsync
                    r.onFailure {
                        Toast.makeText(requireContext(), it.message ?: "Erro", Toast.LENGTH_SHORT).show()
                        load()
                    }
                }
            }
        }
        load()
    }

    private fun load() {
        runAsync({ Backend.repo().atuadores() }) { r ->
            if (!isAdded || view == null) return@runAsync
            r.onSuccess { a ->
                loading = true
                val values = listOf(a.luz, a.ventoinha, a.aquecedor, a.pid)
                rows.forEachIndexed { i, row ->
                    row.second.isChecked = values[i]
                    row.third.text = if (values[i]) "Ligada" else "Desligada"
                }
                loading = false
            }
            r.onFailure { Toast.makeText(requireContext(), it.message ?: "Erro", Toast.LENGTH_SHORT).show() }
        }
    }
}
