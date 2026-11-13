package pt.isep.agriot

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment(R.layout.fragment_home) {

    private val h = Handler(Looper.getMainLooper())
    private lateinit var clock: TextView
    private lateinit var chip: TextView
    private lateinit var miniTemp: TextView
    private lateinit var miniHum: TextView
    private lateinit var miniSolo: TextView

    private val tickClock = object : Runnable {
        override fun run() {
            clock.text = SimpleDateFormat("HH:mm:ss  dd/MM/yy", Locale("pt", "PT")).format(Date())
            h.postDelayed(this, 1000)
        }
    }
    private val tickData = object : Runnable {
        override fun run() {
            load()
            h.postDelayed(this, 5000)
        }
    }

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        clock = v.findViewById(R.id.clock)
        chip = v.findViewById(R.id.estufaEstado)
        miniTemp = v.findViewById(R.id.miniTemp)
        miniHum = v.findViewById(R.id.miniHum)
        miniSolo = v.findViewById(R.id.miniSolo)
        val main = activity as MainActivity

        v.findViewById<TextView>(R.id.greeting).text = "Olá, ${Session.nome}"
        chip.text = "●  ${Session.estufa} · Online"

        v.findViewById<View>(R.id.btnLogout).setOnClickListener { main.logout() }
        v.findViewById<View>(R.id.tileSensores).setOnClickListener { main.selectTab(1) }
        v.findViewById<View>(R.id.tileAtuadores).setOnClickListener { main.selectTab(2) }
        v.findViewById<View>(R.id.tilePid).setOnClickListener { main.selectTab(3) }
        val tileAdmin = v.findViewById<View>(R.id.tileAdmin)
        tileAdmin.alpha = if (Session.admin) 1f else 0.5f
        tileAdmin.setOnClickListener {
            if (Session.admin) main.selectTab(4)
            else Toast.makeText(requireContext(), "Apenas administradores têm acesso a esta área.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        h.post(tickClock)
        h.post(tickData)
    }

    override fun onPause() {
        super.onPause()
        h.removeCallbacksAndMessages(null)
    }

    private fun load() {
        runAsync({ Backend.repo().sensores() }) { r ->
            if (!isAdded || view == null) return@runAsync
            r.onSuccess {
                chip.text = "●  ${Session.estufa} · Online"
                miniTemp.text = fmt(it.temperatura) + "°C"
                miniHum.text = fmt(it.humidadeAr, 0) + "%"
                miniSolo.text = fmt(it.humidadeSolo, 0) + "%"
            }
            r.onFailure { chip.text = "●  ${Session.estufa} · Offline" }
        }
    }
}
