package pt.isep.agriot

/** Controlador PID discreto com anti-windup. Saída em % limitada a [min, max]. */
class PidController(
    var kp: Double = 0.0,
    var ki: Double = 0.0,
    var kd: Double = 0.0,
    private val min: Double = 0.0,
    private val max: Double = 100.0
) {
    private var integral = 0.0
    private var prevErr = 0.0
    private var first = true

    fun update(setpoint: Double, medida: Double, dt: Double): Double {
        val e = setpoint - medida
        val d = if (first) 0.0 else (e - prevErr) / dt
        first = false
        prevErr = e
        val bruta = kp * e + ki * integral + kd * d
        val saida = bruta.coerceIn(min, max)
        val satCima = bruta > max && e > 0
        val satBaixo = bruta < min && e < 0
        if (!satCima && !satBaixo) integral += e * dt
        return saida
    }

    fun reset() {
        integral = 0.0; prevErr = 0.0; first = true
    }
}

/** Simulação (planta de 1.ª ordem) usada no ecrã PID. Persiste enquanto a app está aberta. */
object PidSim {
    const val MAX_POINTS = 300
    private const val T_AMBIENTE = 20.0
    private const val T_INICIAL = 22.0

    var setpoint = 28.0
    var kp = 12.0
    var ki = 0.4
    var kd = 2.0
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
        temps.add(temp); setpoints.add(setpoint); saidas.add(0.0)
        running = true
    }

    /** Avança 1 s de tempo simulado. */
    fun step() {
        if (temps.size >= MAX_POINTS) {
            running = false
            return
        }
        ctrl.kp = kp; ctrl.ki = ki; ctrl.kd = kd
        val out = ctrl.update(setpoint, temp, 1.0)
        temp += ((T_AMBIENTE - temp) + 0.3 * out) / 40.0
        temps.add(temp); setpoints.add(setpoint); saidas.add(out)
    }

    fun overshootPercent(): Double {
        val degrau = setpoint - temps.first()
        if (degrau <= 0.0) return 0.0
        return ((temps.max() - setpoint) / degrau * 100.0).coerceAtLeast(0.0)
    }

    /** Segundos até a temperatura ficar dentro de ±0,5 °C do setpoint; -1 se ainda não estabilizou. */
    fun tempoResposta(): Int {
        val ultimoFora = temps.indexOfLast { kotlin.math.abs(it - setpoint) > 0.5 }
        return if (ultimoFora == temps.size - 1) -1 else ultimoFora + 1
    }

    fun erroFinal(): Double = setpoint - temps.last()
}
