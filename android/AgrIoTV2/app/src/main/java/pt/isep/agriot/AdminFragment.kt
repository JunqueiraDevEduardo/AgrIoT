package pt.isep.agriot

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment

class AdminFragment : Fragment(R.layout.fragment_admin) {

    private var users: List<Utilizador> = emptyList()
    private var query = ""
    private val estufasRelatorio = listOf("Estufa #1", "Estufa #2", "Estufa #3", "Todas as Estufas (Consolidado)")

    private lateinit var spEstufa: Spinner
    private lateinit var tvResumoTemp: TextView
    private lateinit var tvResumoPwm: TextView
    private lateinit var tvResumoUsers: TextView
    private lateinit var btnGerar: AppCompatButton
    private lateinit var btnPartilhar: AppCompatButton

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        val main = activity as MainActivity
        v.findViewById<View>(R.id.btnAdd).setOnClickListener { main.openForm(null) }
        v.findViewById<android.widget.EditText>(R.id.etSearch).doAfterTextChanged {
            query = it?.toString()?.trim() ?: ""
            render()
        }

        // Configuração do Gerador de Relatórios
        spEstufa = v.findViewById(R.id.spEstufaRelatorio)
        tvResumoTemp = v.findViewById(R.id.tvResumoTemp)
        tvResumoPwm = v.findViewById(R.id.tvResumoPwm)
        tvResumoUsers = v.findViewById(R.id.tvResumoUsers)
        btnGerar = v.findViewById(R.id.btnGerarRelatorio)
        btnPartilhar = v.findViewById(R.id.btnPartilharRapido)

        val adapter = ArrayAdapter(requireContext(), R.layout.item_spinner, estufasRelatorio)
        adapter.setDropDownViewResource(R.layout.item_spinner)
        spEstufa.adapter = adapter

        spEstufa.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                atualizarResumoRelatorio(estufasRelatorio[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        btnGerar.setOnClickListener {
            val selecionada = estufasRelatorio[spEstufa.selectedItemPosition]
            gerarRelatorio(selecionada, partilharDireto = false)
        }

        btnPartilhar.setOnClickListener {
            val selecionada = estufasRelatorio[spEstufa.selectedItemPosition]
            gerarRelatorio(selecionada, partilharDireto = true)
        }

        load()
    }

    private fun load() {
        runAsync({ Backend.repo().utilizadores() }) { r ->
            if (!isAdded || view == null) return@runAsync
            r.onSuccess {
                users = it
                render()
                atualizarResumoRelatorio(estufasRelatorio[spEstufa.selectedItemPosition])
            }
            r.onFailure { Toast.makeText(requireContext(), it.message ?: "Erro", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun atualizarResumoRelatorio(estufa: String) {
        val estufaAlvo = if (estufa.startsWith("Todas")) "Estufa #1" else estufa
        runAsync({ Backend.repo().relatorio(estufaAlvo) }) { r ->
            if (!isAdded || view == null) return@runAsync
            r.onSuccess { rel ->
                if (estufa.startsWith("Todas")) {
                    tvResumoTemp.text = "Multi-estufa"
                    tvResumoPwm.text = "3 Zonas"
                    tvResumoUsers.text = users.size.toString()
                } else {
                    tvResumoTemp.text = fmt(rel.temperatura) + " °C"
                    tvResumoPwm.text = fmt(rel.ventoinhaDuty, 0) + " %"
                    tvResumoUsers.text = rel.utilizadores.size.toString()
                }
            }
        }
    }

    private fun gerarRelatorio(estufa: String, partilharDireto: Boolean) {
        val dialogCarregando = Toast.makeText(requireContext(), "A compilar dados da $estufa...", Toast.LENGTH_SHORT)
        dialogCarregando.show()

        val work: () -> String = {
            if (estufa.startsWith("Todas")) {
                val r1 = Backend.repo().relatorio("Estufa #1")
                val r2 = Backend.repo().relatorio("Estufa #2")
                val r3 = Backend.repo().relatorio("Estufa #3")
                val sb = StringBuilder()
                sb.appendLine("==================================================")
                sb.appendLine("     AGRIOT — RELATÓRIO GERAL CONSOLIDADO         ")
                sb.appendLine("     ESTUFA #1  |  ESTUFA #2  |  ESTUFA #3        ")
                sb.appendLine("==================================================")
                sb.appendLine("Data de Emissão: ${r1.dataHora}")
                sb.appendLine("Administrador Responsável: ${Session.nome} (${Session.id})")
                sb.appendLine("Total de Utilizadores Registados no Sistema: ${users.size}")
                sb.appendLine()
                sb.appendLine(">>> 1. RESUMO COMPARATIVO GLOBAL:")
                sb.appendLine("• Estufa #1: ${fmt(r1.temperatura)} °C | Humidade: ${fmt(r1.humidadeAr, 0)}% | Solo: ${fmt(r1.humidadeSolo, 0)}% | Ventoinha: ${fmt(r1.ventoinhaDuty, 0)}% (${r1.ventoinhaRpm} RPM) | Aquecedor: ${if (r1.aquecedor) "LIGADO" else "OFF"}")
                sb.appendLine("• Estufa #2: ${fmt(r2.temperatura)} °C | Humidade: ${fmt(r2.humidadeAr, 0)}% | Solo: ${fmt(r2.humidadeSolo, 0)}% | Ventoinha: ${fmt(r2.ventoinhaDuty, 0)}% (${r2.ventoinhaRpm} RPM) | Aquecedor: ${if (r2.aquecedor) "LIGADO" else "OFF"}")
                sb.appendLine("• Estufa #3: ${fmt(r3.temperatura)} °C | Humidade: ${fmt(r3.humidadeAr, 0)}% | Solo: ${fmt(r3.humidadeSolo, 0)}% | Ventoinha: ${fmt(r3.ventoinhaDuty, 0)}% (${r3.ventoinhaRpm} RPM) | Aquecedor: ${if (r3.aquecedor) "LIGADO" else "OFF"}")
                sb.appendLine()
                sb.appendLine(r1.formatarRelatorio())
                sb.appendLine()
                sb.appendLine(r2.formatarRelatorio())
                sb.appendLine()
                sb.appendLine(r3.formatarRelatorio())
                sb.toString()
            } else {
                Backend.repo().relatorio(estufa).formatarRelatorio()
            }
        }

        runAsync(work) { r ->
            if (!isAdded || view == null) return@runAsync
            r.onSuccess { textoRelatorio ->
                if (partilharDireto) {
                    partilharTexto(estufa, textoRelatorio)
                } else {
                    mostrarDialogRelatorio(estufa, textoRelatorio)
                }
            }
            r.onFailure { Toast.makeText(requireContext(), it.message ?: "Erro ao gerar relatório", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun mostrarDialogRelatorio(estufa: String, texto: String) {
        val d = Dialog(requireContext())
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        d.setContentView(R.layout.dialog_relatorio)
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)
        d.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.94).toInt(),
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        d.findViewById<TextView>(R.id.tvRelatorioTitulo).text = "Relatório AgrIoT"
        d.findViewById<TextView>(R.id.tvRelatorioSubtitulo).text = "$estufa · Área de Administração"
        d.findViewById<TextView>(R.id.tvRelatorioCorpo).text = texto

        d.findViewById<View>(R.id.btnCopiarRelatorio).setOnClickListener {
            val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Relatório AgrIoT", texto))
            Toast.makeText(requireContext(), "Relatório copiado para a área de transferência!", Toast.LENGTH_SHORT).show()
        }

        d.findViewById<View>(R.id.btnPartilharRelatorio).setOnClickListener {
            d.dismiss()
            partilharTexto(estufa, texto)
        }

        d.findViewById<View>(R.id.btnFecharRelatorio).setOnClickListener { d.dismiss() }
        d.show()
    }

    private fun partilharTexto(estufa: String, texto: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Relatório AgrIoT - $estufa")
            putExtra(Intent.EXTRA_TEXT, texto)
        }
        startActivity(Intent.createChooser(intent, "Partilhar Relatório"))
    }

    private fun render() {
        val v = view ?: return
        v.findViewById<TextView>(R.id.statTotal).text = users.size.toString()
        v.findViewById<TextView>(R.id.statAtivos).text = users.count { it.ativo }.toString()
        v.findViewById<TextView>(R.id.statAdmins).text = users.count { it.perfil == "admin" }.toString()
        v.findViewById<TextView>(R.id.chipContas).text = "${users.size} contas"

        val list = v.findViewById<LinearLayout>(R.id.userList)
        list.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        val main = activity as MainActivity
        for (u in users.filter { it.id.contains(query, ignoreCase = true) }) {
            val item = inflater.inflate(R.layout.item_user, list, false)
            val isAdmin = u.perfil == "admin"
            item.findViewById<TextView>(R.id.tvUserId).text = u.id
            val chip = item.findViewById<TextView>(R.id.chipPerfil)
            chip.text = if (isAdmin) "Administrador" else "Utilizador"
            chip.setBackgroundResource(if (isAdmin) R.drawable.bg_chip_green else R.drawable.bg_chip_gray)
            chip.setTextColor(
                ContextCompat.getColor(requireContext(), if (isAdmin) R.color.chip_green_text else R.color.text_secondary)
            )
            item.findViewById<TextView>(R.id.tvUserInfo).text =
                "${u.estufa} · ${if (u.ativo) "Ativo" else "Inativo"}"
            if (isAdmin) {
                item.findViewById<View>(R.id.avatar).setBackgroundResource(R.drawable.bg_circle_dark)
                item.findViewById<android.widget.ImageView>(R.id.avatarIcon).setImageResource(R.drawable.ic_person_white)
            }
            item.findViewById<ImageButton>(R.id.btnEdit).setOnClickListener { main.openForm(u.id) }
            val del = item.findViewById<ImageButton>(R.id.btnDelete)
            if (u.id == Session.id) {
                // a conta do administrador em sessão não pode ser eliminada
                del.isEnabled = false
                del.alpha = 0.3f
            } else {
                del.setOnClickListener {
                    DeleteDialog.show(requireContext(), u.id) {
                        runAsync({ Backend.repo().eliminarUtilizador(u.id) }) { r ->
                            if (!isAdded || view == null) return@runAsync
                            r.onSuccess { load() }
                            r.onFailure {
                                Toast.makeText(requireContext(), it.message ?: "Erro", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            list.addView(item)
        }
    }
}
