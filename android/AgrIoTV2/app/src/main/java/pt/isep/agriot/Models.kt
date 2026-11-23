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

data class Atuadores(
    val luz: Boolean,
    val ventoinha: Boolean,
    val aquecedor: Boolean,
    val pid: Boolean,
    val histerese: Boolean = false,
    val duty: Double = 0.0,   // duty cycle PWM da ventoinha (%)
    val rpm: Int = 0,         // velocidade medida pelo tacómetro
    val pwm: Boolean = false  // modo PWM manual ativo
)

data class PidParams(val setpoint: Double, val kp: Double, val ki: Double, val kd: Double)

data class HistParams(val setpoint: Double, val h: Double)

data class Utilizador(
    val id: String,
    val nome: String,
    val perfil: String, // "admin" ou "utilizador"
    val estufa: String,
    val ativo: Boolean
)

data class RelatorioEstufa(
    val estufa: String,
    val dataHora: String,
    val online: Boolean,
    val temperatura: Double,
    val tempMin: Double,
    val tempMax: Double,
    val tempMedia: Double,
    val humidadeAr: Double,
    val humidadeSolo: Double,
    val pressao: Double,
    val luz: Boolean,
    val ventoinha: Boolean,
    val ventoinhaDuty: Double,
    val ventoinhaRpm: Int,
    val aquecedor: Boolean,
    val pidAtivo: Boolean,
    val histereseAtiva: Boolean,
    val pidParams: PidParams,
    val histParams: HistParams,
    val utilizadores: List<Utilizador>,
    val alertas: List<String>,
    val diagnosticoHardware: Map<String, String>
) {
    fun formatarRelatorio(isEn: Boolean = false): String {
        val sb = StringBuilder()
        if (isEn) {
            sb.appendLine("==================================================")
            sb.appendLine("        AGRIOT — OFFICIAL GREENHOUSE REPORT       ")
            sb.appendLine("==================================================")
            sb.appendLine("Greenhouse: $estufa")
            sb.appendLine("Issue Date and Time: $dataHora")
            sb.appendLine("Connection Status: ${if (online) "ONLINE (Operational)" else "OFFLINE"}")
            sb.appendLine("Issued by: ${Session.nome} (${Session.id})")
            sb.appendLine("Access Role: Administrator")
            sb.appendLine()
            sb.appendLine("--------------------------------------------------")
            sb.appendLine("1. ENVIRONMENTAL CONDITIONS & SENSORS (BME280 / ADS1115)")
            sb.appendLine("--------------------------------------------------")
            sb.appendLine("• Current Temperature: ${fmt(temperatura)} °C")
            sb.appendLine("  - 24h Average: ${fmt(tempMedia)} °C")
            sb.appendLine("  - Minimum: ${fmt(tempMin)} °C  |  Maximum: ${fmt(tempMax)} °C")
            sb.appendLine("• Air Humidity: ${fmt(humidadeAr, 0)} %")
            sb.appendLine("• Soil Moisture: ${fmt(humidadeSolo, 0)} % (Capacitive Sensor)")
            sb.appendLine("• Atmospheric Pressure: ${fmt(pressao, 0)} hPa")
            sb.appendLine()
            sb.appendLine("--------------------------------------------------")
            sb.appendLine("2. ACTUATORS & PWM CONTROL")
            sb.appendLine("--------------------------------------------------")
            sb.appendLine("• Lighting (LED GPIO16): ${if (luz) "ON" else "OFF"}")
            sb.appendLine("• Cooling Fan (GPIO12 PWM / GPIO13 RPM): ${if (ventoinha) "ON" else "OFF"}")
            sb.appendLine("  - PWM Duty Cycle: ${fmt(ventoinhaDuty, 0)} %")
            sb.appendLine("  - Rotation Speed (Tachometer): $ventoinhaRpm RPM")
            sb.appendLine("  - Operating Mode: ${if (pidAtivo) "Automatic (PID Loop)" else if (ventoinha) "Manual (PWM Regulation)" else "OFF"}")
            sb.appendLine("• Heater (Relay GPIO17): ${if (aquecedor) "ON" else "OFF"}")
            sb.appendLine("  - Operating Mode: ${if (histereseAtiva) "Automatic (Hysteresis)" else if (aquecedor) "Manual" else "OFF"}")
            sb.appendLine()
            sb.appendLine("--------------------------------------------------")
            sb.appendLine("3. AUTOMATIC CONTROL LOOPS")
            sb.appendLine("--------------------------------------------------")
            sb.appendLine("• PID Control (Fan): ${if (pidAtivo) "ACTIVE" else "INACTIVE"}")
            sb.appendLine("  - Setpoint: ${fmt(pidParams.setpoint)} °C")
            sb.appendLine("  - Gains: Kp=${fmt(pidParams.kp)}, Ki=${fmt(pidParams.ki, 2)}, Kd=${fmt(pidParams.kd)}")
            sb.appendLine("  - Control Logic: T > Setpoint (progressive cooling)")
            sb.appendLine("• Hysteresis Control (Heater): ${if (histereseAtiva) "ACTIVE" else "INACTIVE"}")
            sb.appendLine("  - Setpoint: ${fmt(histParams.setpoint)} °C | Deadband h: ${fmt(histParams.h)} °C")
            sb.appendLine("  - Switching Range: ON if T < ${fmt(histParams.setpoint - histParams.h)} °C, OFF if T >= ${fmt(histParams.setpoint)} °C")
            sb.appendLine("• Hardware Protection: Mutual Exclusion Active (Relay and Fan never run simultaneously)")
            sb.appendLine()
            sb.appendLine("--------------------------------------------------")
            sb.appendLine("4. AUTHORIZED USERS WITH GREENHOUSE ACCESS")
            sb.appendLine("--------------------------------------------------")
            if (utilizadores.isEmpty()) {
                sb.appendLine("• No specific users assigned to this greenhouse.")
            } else {
                for (u in utilizadores) {
                    sb.appendLine("• [${u.id}] ${u.nome} — Role: ${u.perfil.uppercase()} — Status: ${if (u.ativo) "Active" else "Inactive"}")
                }
            }
            sb.appendLine()
            sb.appendLine("--------------------------------------------------")
            sb.appendLine("5. HARDWARE & COMMUNICATION DIAGNOSTICS")
            sb.appendLine("--------------------------------------------------")
            for ((comp, diag) in diagnosticoHardware) {
                sb.appendLine("• $comp: $diag")
            }
            sb.appendLine()
            sb.appendLine("--------------------------------------------------")
            sb.appendLine("6. SYSTEM ALERTS & RECOMMENDATIONS")
            sb.appendLine("--------------------------------------------------")
            if (alertas.isEmpty()) {
                sb.appendLine("• Normal: All environmental parameters are within optimal ranges.")
            } else {
                for (a in alertas) {
                    sb.appendLine("• [ALERT] $a")
                }
            }
            sb.appendLine("==================================================")
        } else {
            sb.appendLine("==================================================")
            sb.appendLine("        AGRIOT — RELATÓRIO OFICIAL DA ESTUFA      ")
            sb.appendLine("==================================================")
            sb.appendLine("Estufa: $estufa")
            sb.appendLine("Data e Hora da Emissão: $dataHora")
            sb.appendLine("Estado da Conexão: ${if (online) "ONLINE (Operacional)" else "OFFLINE"}")
            sb.appendLine("Emitido por: ${Session.nome} (${Session.id})")
            sb.appendLine("Perfil de Acesso: Administrador")
            sb.appendLine()
            sb.appendLine("--------------------------------------------------")
            sb.appendLine("1. CONDIÇÕES AMBIENTAIS E SENSORES (BME280 / ADS1115)")
            sb.appendLine("--------------------------------------------------")
            sb.appendLine("• Temperatura Atual: ${fmt(temperatura)} °C")
            sb.appendLine("  - Média (24h): ${fmt(tempMedia)} °C")
            sb.appendLine("  - Mínima: ${fmt(tempMin)} °C  |  Máxima: ${fmt(tempMax)} °C")
            sb.appendLine("• Humidade do Ar: ${fmt(humidadeAr, 0)} %")
            sb.appendLine("• Humidade do Solo: ${fmt(humidadeSolo, 0)} % (Sensor Capacitivo)")
            sb.appendLine("• Pressão Atmosférica: ${fmt(pressao, 0)} hPa")
            sb.appendLine()
            sb.appendLine("--------------------------------------------------")
            sb.appendLine("2. ESTADO DOS ATUADORES & CONTROLO PWM")
            sb.appendLine("--------------------------------------------------")
            sb.appendLine("• Iluminação (LED GPIO16): ${if (luz) "LIGADA" else "DESLIGADA"}")
            sb.appendLine("• Ventoinha (GPIO12 PWM / GPIO13 RPM): ${if (ventoinha) "LIGADA" else "DESLIGADA"}")
            sb.appendLine("  - Duty Cycle PWM: ${fmt(ventoinhaDuty, 0)} %")
            sb.appendLine("  - Rotações (Tacómetro): $ventoinhaRpm RPM")
            sb.appendLine("  - Modo Operacional: ${if (pidAtivo) "Automático (Malha PID)" else if (ventoinha) "Manual (Regulação PWM)" else "Desligada"}")
            sb.appendLine("• Aquecedor (Relé GPIO17): ${if (aquecedor) "LIGADO" else "DESLIGADO"}")
            sb.appendLine("  - Modo Operacional: ${if (histereseAtiva) "Automático (Histerese)" else if (aquecedor) "Manual" else "Desligado"}")
            sb.appendLine()
            sb.appendLine("--------------------------------------------------")
            sb.appendLine("3. MALHAS DE CONTROLO AUTOMÁTICO")
            sb.appendLine("--------------------------------------------------")
            sb.appendLine("• Controlo PID (Ventoinha): ${if (pidAtivo) "ATIVO" else "INATIVO"}")
            sb.appendLine("  - Setpoint: ${fmt(pidParams.setpoint)} °C")
            sb.appendLine("  - Ganhos: Kp=${fmt(pidParams.kp)}, Ki=${fmt(pidParams.ki, 2)}, Kd=${fmt(pidParams.kd)}")
            sb.appendLine("  - Condição de Atuação: T > Setpoint (arrefecimento progressivo)")
            sb.appendLine("• Controlo por Histerese (Aquecedor): ${if (histereseAtiva) "ATIVO" else "INATIVO"}")
            sb.appendLine("  - Setpoint: ${fmt(histParams.setpoint)} °C | Banda h: ${fmt(histParams.h)} °C")
            sb.appendLine("  - Intervalo de Comutação: Liga se T < ${fmt(histParams.setpoint - histParams.h)} °C, Desliga se T >= ${fmt(histParams.setpoint)} °C")
            sb.appendLine("• Proteção Hardware: Relé e Ventoinha nunca atuam em simultâneo (Mutual Exclusion: OK)")
            sb.appendLine()
            sb.appendLine("--------------------------------------------------")
            sb.appendLine("4. UTILIZADORES COM ACESSO ATRIBUÍDO")
            sb.appendLine("--------------------------------------------------")
            if (utilizadores.isEmpty()) {
                sb.appendLine("• Nenhum utilizador específico atribuído a esta estufa.")
            } else {
                for (u in utilizadores) {
                    sb.appendLine("• [${u.id}] ${u.nome} — Perfil: ${u.perfil.uppercase()} — Estado: ${if (u.ativo) "Ativo" else "Inativo"}")
                }
            }
            sb.appendLine()
            sb.appendLine("--------------------------------------------------")
            sb.appendLine("5. DIAGNÓSTICO DE HARDWARE & COMUNICAÇÃO")
            sb.appendLine("--------------------------------------------------")
            for ((comp, diag) in diagnosticoHardware) {
                sb.appendLine("• $comp: $diag")
            }
            sb.appendLine()
            sb.appendLine("--------------------------------------------------")
            sb.appendLine("6. ALERTAS DO SISTEMA & RECOMENDAÇÕES")
            sb.appendLine("--------------------------------------------------")
            if (alertas.isEmpty()) {
                sb.appendLine("• Normalidade: Todos os parâmetros ambientais estão dentro dos limites recomendados.")
            } else {
                for (a in alertas) {
                    sb.appendLine("• [ALERTA] $a")
                }
            }
            sb.appendLine("==================================================")
        }
        return sb.toString()
    }
}

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
