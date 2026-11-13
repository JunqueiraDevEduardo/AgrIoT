package pt.isep.agriot

import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.widget.EditText
import android.widget.ImageButton
import java.util.Locale

data class Sensores(
    val temperatura: Double,
    val humidadeAr: Double,
    val humidadeSolo: Double,
    val pressao: Double,
    val historico: List<Double>
)

data class Atuadores(val luz: Boolean, val ventoinha: Boolean, val aquecedor: Boolean, val pid: Boolean)

data class PidParams(val setpoint: Double, val kp: Double, val ki: Double, val kd: Double)

data class Utilizador(
    val id: String,
    val nome: String,
    val perfil: String, // "admin" ou "utilizador"
    val estufa: String,
    val ativo: Boolean
)

class ApiException(val code: Int, message: String) : Exception(message)

object Session {
    var token = ""
    var id = ""
    var nome = ""
    var estufa = ""
    var admin = false

    fun clear() {
        token = ""; id = ""; nome = ""; estufa = ""; admin = false
    }
}

/** Executa [work] numa thread de fundo e entrega o resultado na thread principal. */
fun <T> runAsync(work: () -> T, done: (Result<T>) -> Unit) {
    Thread {
        val r = runCatching(work)
        Handler(Looper.getMainLooper()).post { done(r) }
    }.start()
}

fun fmt(value: Double, decimals: Int = 1): String =
    String.format(Locale.US, "%.${decimals}f", value)

fun fmtPt(value: Double, decimals: Int = 1): String =
    String.format(Locale("pt", "PT"), "%.${decimals}f", value)

/** Alterna entre mostrar/esconder o texto de um campo de palavra-passe. */
fun wireEye(et: EditText, btn: ImageButton) {
    var visible = false
    btn.setOnClickListener {
        visible = !visible
        et.inputType = InputType.TYPE_CLASS_TEXT or
            (if (visible) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            else InputType.TYPE_TEXT_VARIATION_PASSWORD)
        btn.setImageResource(if (visible) R.drawable.ic_eye_off else R.drawable.ic_eye)
        et.setSelection(et.text.length)
    }
}
