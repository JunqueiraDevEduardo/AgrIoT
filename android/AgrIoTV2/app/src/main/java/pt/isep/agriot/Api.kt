package com.example.myapplication

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object Api {
    const val BASE = "http://100.94.67.33:5000"
    private val executor = Executors.newSingleThreadExecutor()

    private fun pedido(caminho: String, metodo: String, corpo: JSONObject? = null): JSONObject {
        val c = URL(BASE + caminho).openConnection() as HttpURLConnection
        c.requestMethod = metodo
        c.connectTimeout = 3000
        c.readTimeout = 3000
        if (corpo != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(corpo.toString().toByteArray()) }
        }
        return try {
            JSONObject(c.inputStream.bufferedReader().readText())
        } finally {
            c.disconnect()
        }
    }

    /** Corre em thread de fundo; o callback vem na thread principal. */
    fun <T> async(
        activity: android.app.Activity,
        trabalho: () -> T,
        sucesso: (T) -> Unit,
        erro: (Exception) -> Unit = {}
    ) {
        executor.execute {
            try {
                val r = trabalho()
                activity.runOnUiThread { sucesso(r) }
            } catch (e: Exception) {
                activity.runOnUiThread { erro(e) }
            }
        }
    }

    fun sensores() = pedido("/api/sensores", "GET")
    fun atuadores() = pedido("/api/atuadores", "GET")
    fun setAtuador(nome: String, ligado: Boolean) =
        pedido("/api/atuadores/$nome", "POST", JSONObject().put("ligado", ligado))
    fun setPi(setpoint: Double, kp: Double, ki: Double) =
        pedido("/api/pi", "POST",
            JSONObject().put("setpoint", setpoint).put("kp", kp).put("ki", ki))
}