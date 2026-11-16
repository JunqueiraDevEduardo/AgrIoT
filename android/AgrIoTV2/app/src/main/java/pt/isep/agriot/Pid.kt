package pt.isep.agriot

import kotlin.math.abs
import kotlin.math.max

/** Parâmetros partilhados pelo PID e pela histerese (um único setpoint Tset, como no relatório). */
object Params {
    var setpoint = 28.0
    var h = 2.0
    var kp = 12.0
    var ki = 0.4
    var kd = 2.0
}

/**
 * Controlador PID da ventoinha (secção 3.1.1 do relatório).
 * e_k = T_k - Tset;  u_k = Kp*e + Ki*Ts*soma(e) + Kd*(e_k - e_k-1)/Ts, limitado a 0-100 %.
 * A ventoinha só atua quando T > Tset (só consegue arrefecer): com e <= 0 a saída é 0.
 */
class PidController(var kp: Double = 0.0, var ki: Double = 0.0, var kd: Double = 0.0) {
    private var soma = 0.0
    private var anterior = 0.0
    private var primeiro = true

    fun update(setpoint: Double, temperatura: Double, ts: Double): Double {
        val e = temperatura - setpoint
        if (e <= 0.0) {
            soma = 0.0; anterior = e; primeiro = false
            return 0.0
        }
        val d = if (primeiro) 0.0 else (e - anterior) / ts
        val candidata = soma + e
        val bruta = kp * e + ki * ts * candidata + kd * d
        if (bruta <= 100.0) soma = candidata // anti-windup: não acumula com a saída saturada
        anterior = e; primeiro = false
        return bruta.coerceIn(0.0, 100.0)
    }

    fun reset() {
        soma = 0.0; anterior = 0.0; primeiro = true
    }
}

/** Simulação da ventoinha com PID: estufa quente (35 °C sem ventilação) que a ventoinha arrefece. */
object PidSim {
    const val MAX_POINTS = 300
    private const val T_EQ = 35.0      // temperatura de equilíbrio sem ventoinha
    private const val K_VENT = 0.15    // efeito de arrefecimento por % de duty
    private const val TAU = 40.0
    private const val T_INICIAL = 33.0

    var running = true
    var temp = T_INICIAL

    private val ctrl = PidController()
    val temps = ArrayList<Double>()
    val setpoints = ArrayList<Double>()
    val saidas = ArrayList<Double>()

    init {
        reset()
    }

    fun reset() {
        temp = T_INICIAL
        ctrl.reset()
        temps.clear(); setpoints.clear(); saidas.clear()
        temps.add(temp); setpoints.add(Params.setpoint); saidas.add(0.0)
        running = true
    }

    /** Avança Ts = 1 s de tempo simulado. */
    fun step() {
        if (temps.size >= MAX_POINTS) { running = false; return }
        ctrl.kp = Params.kp; ctrl.ki = Params.ki; ctrl.kd = Params.kd
        val u = ctrl.update(Params.setpoint, temp, 1.0)
        temp += ((T_EQ - temp) - K_VENT * u) / TAU
        temps.add(temp); setpoints.add(Params.setpoint); saidas.add(u)
    }

    /** Sobreelevação: quanto a temperatura passa abaixo do setpoint, em % do degrau inicial. */
    fun overshootPercent(): Double {
        val degrau = temps.first() - Params.setpoint
        if (degrau <= 0.0) return 0.0
        return ((Params.setpoint - temps.min()) / degrau * 100.0).coerceAtLeast(0.0)
    }

    /** Segundos até ficar dentro de ±0,5 °C do setpoint; -1 se ainda não estabilizou. */
    fun tempoResposta(): Int {
        val ultimoFora = temps.indexOfLast { abs(it - Params.setpoint) > 0.5 }
        return if (ultimoFora == temps.size - 1) -1 else ultimoFora + 1
    }

    fun erroFinal(): Double = temps.last() - Params.setpoint
}

/**
 * Simulação do aquecimento por histerese (secção 3.1.1): o relé liga quando T < Tset - h
 * e desliga quando T >= Tset; entre os dois valores mantém o estado.
 */
object HistSim {
    const val MAX_POINTS = 300
    private const val T_AMB = 18.0
    private const val GANHO = 14.0   // com o relé ligado a temperatura tende para T_AMB + GANHO
    private const val TAU = 40.0
    private const val T_INICIAL = 22.0

    var running = true
    var temp = T_INICIAL
    var rele = false
    var comutacoes = 0
    private var ligado = 0

    val temps = ArrayList<Double>()
    val setpoints = ArrayList<Double>()
    val limites = ArrayList<Double>()
    val reles = ArrayList<Boolean>()

    init {
        reset()
    }

    fun reset() {
        temp = T_INICIAL; rele = false; comutacoes = 0; ligado = 0
        temps.clear(); setpoints.clear(); limites.clear(); reles.clear()
        temps.add(temp); setpoints.add(Params.setpoint); limites.add(Params.setpoint - Params.h); reles.add(false)
        running = true
    }

    fun step() {
        if (temps.size >= MAX_POINTS) { running = false; return }
        val baixo = Params.setpoint - Params.h
        if (!rele && temp < baixo) { rele = true; comutacoes++ }
        else if (rele && temp >= Params.setpoint) { rele = false; comutacoes++ }
        temp += ((T_AMB - temp) + (if (rele) GANHO else 0.0)) / TAU
        if (rele) ligado++
        temps.add(temp); setpoints.add(Params.setpoint); limites.add(baixo); reles.add(rele)
    }

    fun tempoLigadoPercent(): Double = ligado * 100.0 / max(1, temps.size - 1)

    /** Amplitude da oscilação nas últimas 120 amostras. */
    fun amplitude(): Double {
        val n = temps.size
        val w = temps.subList(max(0, n - 120), n)
        return w.max() - w.min()
    }
}
