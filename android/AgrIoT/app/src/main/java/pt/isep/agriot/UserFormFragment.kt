package pt.isep.agriot

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

/** Ecrã partilhado: "Novo Utilizador" (POST) e "Editar Utilizador" (PUT / DELETE). */
class UserFormFragment : Fragment(R.layout.fragment_user_form) {

    private val estufas = listOf("Estufa #1", "Estufa #2", "Estufa #3")
    private var editId: String? = null
    private var admin = false

    private lateinit var tvUser: TextView
    private lateinit var tvAdmin: TextView

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        editId = arguments?.getString("id")
        val edit = editId != null

        val etId = v.findViewById<EditText>(R.id.etUserId)
        val etPass = v.findViewById<EditText>(R.id.etPass)
        val etConfirm = v.findViewById<EditText>(R.id.etConfirm)
        val spEstufa = v.findViewById<Spinner>(R.id.spEstufa)
        val swAtiva = v.findViewById<SwitchCompat>(R.id.swAtiva)
        val btnPrimary = v.findViewById<Button>(R.id.btnPrimary)
        val btnCancel = v.findViewById<Button>(R.id.btnCancel)
        val btnDelete = v.findViewById<Button>(R.id.btnDeleteUser)
        tvUser = v.findViewById(R.id.tvPerfilUser)
        tvAdmin = v.findViewById(R.id.tvPerfilAdmin)

        wireEye(etPass, v.findViewById<ImageButton>(R.id.eyePass))
        wireEye(etConfirm, v.findViewById<ImageButton>(R.id.eyeConfirm))

        val adapter = ArrayAdapter(requireContext(), R.layout.item_spinner, estufas)
        adapter.setDropDownViewResource(R.layout.item_spinner)
        spEstufa.adapter = adapter

        // Modo novo / edição
        v.findViewById<TextView>(R.id.formTitle).text = if (edit) "Editar Utilizador" else "Novo Utilizador"
        v.findViewById<TextView>(R.id.chipModo).text = if (edit) "Edição ativa" else "Novo registo"
        v.findViewById<View>(R.id.layoutConfirm).visibility = if (edit) View.GONE else View.VISIBLE
        v.findViewById<View>(R.id.rowAtiva).visibility = if (edit) View.VISIBLE else View.GONE
        btnCancel.visibility = if (edit) View.GONE else View.VISIBLE
        btnDelete.visibility = if (edit) View.VISIBLE else View.GONE
        btnPrimary.text = if (edit) "GUARDAR ALTERAÇÕES" else "+ CRIAR UTILIZADOR"
        v.findViewById<TextView>(R.id.tvPassLabel).text = if (edit) "Nova palavra-passe (opcional)" else "Palavra-passe"
        if (edit) {
            etId.setText(editId)
            etId.isEnabled = false // o ID identifica o utilizador
            etId.alpha = 0.6f
            etPass.hint = "Deixar vazio para manter"
        }

        tvUser.setOnClickListener { setPerfil(false) }
        tvAdmin.setOnClickListener { setPerfil(true) }
        setPerfil(false)

        val back = { parentFragmentManager.popBackStack() }
        v.findViewById<View>(R.id.tvBack).setOnClickListener { back() }
        btnCancel.setOnClickListener { back() }

        if (edit) {
            runAsync({ Backend.repo().utilizadores() }) { r ->
                if (!isAdded || view == null) return@runAsync
                r.onSuccess { list ->
                    val u = list.find { it.id == editId } ?: return@onSuccess
                    setPerfil(u.perfil == "admin")
                    spEstufa.setSelection(maxOf(0, estufas.indexOf(u.estufa)))
                    swAtiva.isChecked = u.ativo
                }
            }
        }

        btnPrimary.setOnClickListener {
            val estufa = estufas[spEstufa.selectedItemPosition]
            val perfil = if (admin) "admin" else "utilizador"
            if (edit) {
                if (editId == Session.id && (!admin || !swAtiva.isChecked)) {
                    toast("Não pode remover o seu próprio acesso de administrador.")
                    return@setOnClickListener
                }
                val nova = etPass.text.toString()
                send({ Backend.repo().editarUtilizador(editId!!, nova, perfil, estufa, swAtiva.isChecked) }, "Alterações guardadas")
            } else {
                val id = etId.text.toString().trim().uppercase()
                val pass = etPass.text.toString()
                if (id.isEmpty() || pass.isEmpty()) {
                    toast("Preencha o ID e a palavra-passe")
                    return@setOnClickListener
                }
                if (pass != etConfirm.text.toString()) {
                    toast("As palavras-passe não coincidem")
                    return@setOnClickListener
                }
                send({ Backend.repo().criarUtilizador(id, pass, perfil, estufa) }, "Utilizador criado")
            }
        }

        btnDelete.setOnClickListener {
            val id = editId ?: return@setOnClickListener
            if (id == Session.id) {
                toast("A conta em sessão não pode ser eliminada.")
                return@setOnClickListener
            }
            DeleteDialog.show(requireContext(), id) {
                send({ Backend.repo().eliminarUtilizador(id) }, "Utilizador eliminado")
            }
        }
    }

    private fun send(work: () -> Unit, okMsg: String) {
        runAsync(work) { r ->
            if (!isAdded || view == null) return@runAsync
            r.onSuccess {
                toast(okMsg)
                parentFragmentManager.popBackStack()
            }
            r.onFailure { toast(it.message ?: "Erro") }
        }
    }

    private fun setPerfil(isAdmin: Boolean) {
        admin = isAdmin
        style(tvUser, !isAdmin)
        style(tvAdmin, isAdmin)
    }

    private fun style(tv: TextView, selected: Boolean) {
        tv.setBackgroundResource(if (selected) R.drawable.bg_button_green else R.drawable.bg_input)
        tv.setTextColor(ContextCompat.getColor(requireContext(), if (selected) R.color.white else R.color.text_secondary))
    }

    private fun toast(msg: String) {
        context?.let { Toast.makeText(it, msg, Toast.LENGTH_SHORT).show() }
    }
}
