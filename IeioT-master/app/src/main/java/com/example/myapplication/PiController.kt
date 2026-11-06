package com.example.myapplication

/**
 * Controlador PI discreto para o aquecimento da estufa.
 *
 * saída (%) = Kp * erro + Ki * ∫erro dt, limitada a [saidaMin, saidaMax].
 * Anti-windup: o integral só acumula quando a saída não está saturada
 * no sentido em que o erro a empurra.
 */
class PiController(
    var kp: Double,
    var ki: Double,
    private val saidaMin: Double = 0.0,
    private val saidaMax: Double = 100.0
) {
    private var integral = 0.0

    /** Calcula a saída (%) para o instante atual. [dt] em segundos. */
    fun atualizar(setpoint: Double, medida: Double, dt: Double): Double {
        require(dt > 0.0) { "dt tem de ser positivo" }
        val erro = setpoint - medida
        val bruta = kp * erro + ki * integral
        val saida = bruta.coerceIn(saidaMin, saidaMax)

        val saturadoParaCima = bruta > saidaMax && erro > 0
        val saturadoParaBaixo = bruta < saidaMin && erro < 0
        if (!saturadoParaCima && !saturadoParaBaixo) {
            integral += erro * dt
        }
        return saida
    }

    /** Limpa o integral (ao desligar o controlo ou mudar o setpoint de forma brusca). */
    fun reset() {
        integral = 0.0
    }
}
