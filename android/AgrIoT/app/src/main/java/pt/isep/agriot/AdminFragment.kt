package pt.isep.agriot

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment

class AdminFragment : Fragment(R.layout.fragment_admin) {

    private var users: List<Utilizador> = emptyList()
    private var query = ""

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        val main = activity as MainActivity
        v.findViewById<View>(R.id.btnAdd).setOnClickListener { main.openForm(null) }
        v.findViewById<android.widget.EditText>(R.id.etSearch).doAfterTextChanged {
            query = it?.toString()?.trim() ?: ""
            render()
        }
        load()
    }

    private fun load() {
        runAsync({ Backend.repo().utilizadores() }) { r ->
            if (!isAdded || view == null) return@runAsync
            r.onSuccess { users = it; render() }
            r.onFailure { Toast.makeText(requireContext(), it.message ?: "Erro", Toast.LENGTH_SHORT).show() }
        }
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
