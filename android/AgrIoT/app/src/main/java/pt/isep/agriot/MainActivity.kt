package pt.isep.agriot

import android.content.Intent
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager

class MainActivity : AppCompatActivity() {

    private val navIds = intArrayOf(R.id.navHome, R.id.navSensores, R.id.navAtuadores, R.id.navPid, R.id.navAdmin)
    private var current = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Session.id.isEmpty()) { // sessão perdida (ex.: processo terminado)
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_main)
        navIds.forEachIndexed { i, id -> findViewById<View>(id).setOnClickListener { selectTab(i) } }
        if (savedInstanceState == null) selectTab(0) else highlight(savedInstanceState.getInt("tab", 0))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", current)
    }

    fun selectTab(i: Int) {
        if (i == 4 && !Session.admin) {
            Toast.makeText(this, "Apenas administradores têm acesso a esta área.", Toast.LENGTH_SHORT).show()
            return
        }
        val f: Fragment = when (i) {
            1 -> SensoresFragment()
            2 -> AtuadoresFragment()
            3 -> PidFragment()
            4 -> AdminFragment()
            else -> HomeFragment()
        }
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        supportFragmentManager.beginTransaction().replace(R.id.container, f).commit()
        highlight(i)
    }

    fun openForm(editId: String?) {
        val f = UserFormFragment()
        f.arguments = Bundle().apply { putString("id", editId) }
        supportFragmentManager.beginTransaction().replace(R.id.container, f).addToBackStack(null).commit()
    }

    fun logout() {
        Session.clear()
        val i = Intent(this, LoginActivity::class.java)
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(i)
        finish()
    }

    private fun highlight(selected: Int) {
        current = selected
        navIds.forEachIndexed { i, id ->
            val item = findViewById<LinearLayout>(id)
            val pill = item.getChildAt(0) as FrameLayout
            val icon = pill.getChildAt(0) as ImageView
            val label = item.getChildAt(1) as TextView
            val on = i == selected
            val color = ContextCompat.getColor(this, if (on) R.color.green_primary else R.color.nav_inactive)
            pill.setBackgroundResource(if (on) R.drawable.bg_nav_pill else 0)
            icon.setColorFilter(color, PorterDuff.Mode.SRC_ATOP)
            label.setTextColor(color)
            label.setTypeface(null, if (on) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }
    }
}

object DeleteDialog {
    fun show(ctx: android.content.Context, id: String, onConfirm: () -> Unit) {
        val d = android.app.Dialog(ctx)
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        d.setContentView(R.layout.dialog_delete)
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)
        d.window?.setLayout(
            (ctx.resources.displayMetrics.widthPixels * 0.88).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        d.findViewById<TextView>(R.id.dlgMsg).text =
            "O utilizador $id será removido permanentemente e perderá o acesso à estufa. Esta ação não pode ser revertida."
        d.findViewById<View>(R.id.btnCancelar).setOnClickListener { d.dismiss() }
        d.findViewById<View>(R.id.btnEliminar).setOnClickListener {
            d.dismiss()
            onConfirm()
        }
        d.show()
    }
}
