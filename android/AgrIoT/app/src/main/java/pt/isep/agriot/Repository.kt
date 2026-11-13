package pt.isep.agriot

import android.app.Application
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.sin

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: App
    }
}

interface Repository {
    fun login(id: String, password: String): Utilizador
    fun sensores(): Sensores
    fun atuadores(): Atuadores
    fun setAtuador(nome: String, estado: Boolean)
    fun pid(): PidParams
    fun setPid(p: PidParams)
    fun utilizadores(): List<Utilizador>
    fun criarUtilizador(id: String, password: String, perfil: String, estufa: String)
    fun editarUtilizador(id: String, novaPassword: String?, perfil: String, estufa: String, ativo: Boolean)
    fun eliminarUtilizador(id: String)
}

/** Escolhe entre o servidor Flask (URL definido) e o modo demonstração (URL vazio). */
object Backend {
    private val prefs get() = App.instance.getSharedPreferences("agriot", Context.MODE_PRIVATE)
    val demo = DemoRepository()

    var url: String
        get() = prefs.getString("url", "") ?: ""
        set(v) {
            prefs.edit().putString("url", v.trim()).apply()
        }

    fun repo(): Repository = if (url.isBlank()) demo else RemoteRepository(url)
}

/** Cliente HTTP/JSON (REST) para o servidor Flask. */
class RemoteRepository(base: String) : Repository {
    private val base = base.trimEnd('/')

    private fun call(method: String, path: String, body: JSONObject? = null): String {
        try {
            val conn = URL(base + path).openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.connectTimeout = 4000
            conn.readTimeout = 6000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            if (Session.token.isNotEmpty()) conn.setRequestProperty("Authorization", "Bearer " + Session.token)
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            conn.disconnect()
            if (code !in 200..299) {
                val msg = try {
                    JSONObject(text).optString("erro")
                } catch (e: Exception) {
                    ""
                }
                throw ApiException(code, msg.ifEmpty { "Erro $code" })
            }
            return text
        } catch (e: IOException) {
            throw ApiException(0, "Sem ligação ao servidor")
        }
    }

    private fun user(j: JSONObject) = Utilizador(
        j.getString("id"), j.optString("nome", j.getString("id")), j.getString("perfil"),
        j.optString("estufa", "Estufa #1"), j.optBoolean("ativo", true)
    )

    override fun login(id: String, password: String): Utilizador {
        val j = JSONObject(call("POST", "/api/login", JSONObject().put("id", id).put("password", password)))
        Session.token = j.getString("token")
        return user(j)
    }

    override fun sensores(): Sensores {
        val j = JSONObject(call("GET", "/api/sensores"))
        val arr = j.optJSONArray("historico")
        val h = ArrayList<Double>()
        if (arr != null) for (i in 0 until arr.length()) h.add(arr.getDouble(i))
        return Sensores(
            j.getDouble("temperatura"), j.getDouble("humidade_ar"),
            j.getDouble("humidade_solo"), j.getDouble("pressao"), h
        )
    }

    override fun atuadores(): Atuadores {
        val j = JSONObject(call("GET", "/api/atuadores"))
        return Atuadores(j.getBoolean("luz"), j.getBoolean("ventoinha"), j.getBoolean("aquecedor"), j.getBoolean("pid"))
    }

    override fun setAtuador(nome: String, estado: Boolean) {
        call("PUT", "/api/atuadores/$nome", JSONObject().put("estado", estado))
    }

    override fun pid(): PidParams {
        val j = JSONObject(call("GET", "/api/pid"))
        return PidParams(j.getDouble("setpoint"), j.getDouble("kp"), j.getDouble("ki"), j.getDouble("kd"))
    }

    override fun setPid(p: PidParams) {
        call(
            "PUT", "/api/pid",
            JSONObject().put("setpoint", p.setpoint).put("kp", p.kp).put("ki", p.ki).put("kd", p.kd)
        )
    }

    override fun utilizadores(): List<Utilizador> {
        val arr = JSONArray(call("GET", "/api/utilizadores"))
        return (0 until arr.length()).map { user(arr.getJSONObject(it)) }
    }

    override fun criarUtilizador(id: String, password: String, perfil: String, estufa: String) {
        call(
            "POST", "/api/utilizadores",
            JSONObject().put("id", id).put("password", password).put("perfil", perfil).put("estufa", estufa)
        )
    }

    override fun editarUtilizador(id: String, novaPassword: String?, perfil: String, estufa: String, ativo: Boolean) {
        val b = JSONObject().put("perfil", perfil).put("estufa", estufa).put("ativo", ativo)
        if (!novaPassword.isNullOrEmpty()) b.put("password", novaPassword)
        call("PUT", "/api/utilizadores/$id", b)
    }

    override fun eliminarUtilizador(id: String) {
        call("DELETE", "/api/utilizadores/$id")
    }
}

/** Dados simulados em memória, para testar a app sem servidor. */
class DemoRepository : Repository {
    private val users = mutableListOf(
        Utilizador("ADM-001", "Eduardo", "admin", "Estufa #1", true),
        Utilizador("CLI-001", "Cliente 001", "utilizador", "Estufa #1", true),
        Utilizador("CLI-002", "Cliente 002", "utilizador", "Estufa #2", true),
        Utilizador("CLI-003", "Cliente 003", "utilizador", "Estufa #2", false)
    )
    private val passwords = mutableMapOf(
        "ADM-001" to "admin123", "CLI-001" to "1234", "CLI-002" to "1234", "CLI-003" to "1234"
    )
    private var act = Atuadores(luz = true, ventoinha = false, aquecedor = false, pid = false)
    private var pidP = PidParams(28.0, 12.0, 0.4, 2.0)

    private fun pause() = Thread.sleep(120)

    private fun tempAt(ms: Long): Double {
        val hours = (ms % 86_400_000L) / 3_600_000.0
        return 24.0 + 3.0 * sin((hours - 9.0) / 24.0 * 2 * PI)
    }

    override fun login(id: String, password: String): Utilizador {
        pause()
        val u = users.find { it.id.equals(id, true) } ?: throw ApiException(401, "ID ou palavra-passe incorretos")
        if (passwords[u.id] != password) throw ApiException(401, "ID ou palavra-passe incorretos")
        if (!u.ativo) throw ApiException(403, "Conta desativada")
        Session.token = "demo"
        return u
    }

    override fun sensores(): Sensores {
        pause()
        val now = System.currentTimeMillis()
        val hist = (0 until 48).map { tempAt(now - (47 - it) * 30 * 60 * 1000L) }
        return Sensores(
            tempAt(now), 62.0 + 3.0 * sin(now / 60000.0), 41.0 + sin(now / 90000.0), 1013.0, hist
        )
    }

    override fun atuadores(): Atuadores {
        pause(); return act
    }

    override fun setAtuador(nome: String, estado: Boolean) {
        pause()
        act = when (nome) {
            "luz" -> act.copy(luz = estado)
            "ventoinha" -> act.copy(ventoinha = estado)
            "aquecedor" -> act.copy(aquecedor = estado)
            else -> act.copy(pid = estado)
        }
    }

    override fun pid(): PidParams {
        pause(); return pidP
    }

    override fun setPid(p: PidParams) {
        pause(); pidP = p
    }

    override fun utilizadores(): List<Utilizador> {
        pause(); return users.toList()
    }

    override fun criarUtilizador(id: String, password: String, perfil: String, estufa: String) {
        pause()
        if (users.any { it.id.equals(id, true) }) throw ApiException(409, "Já existe um utilizador com esse ID")
        users.add(Utilizador(id.uppercase(), id.uppercase(), perfil, estufa, true))
        passwords[id.uppercase()] = password
    }

    override fun editarUtilizador(id: String, novaPassword: String?, perfil: String, estufa: String, ativo: Boolean) {
        pause()
        val i = users.indexOfFirst { it.id == id }
        if (i < 0) throw ApiException(404, "Utilizador não encontrado")
        users[i] = users[i].copy(perfil = perfil, estufa = estufa, ativo = ativo)
        if (!novaPassword.isNullOrEmpty()) passwords[id] = novaPassword
    }

    override fun eliminarUtilizador(id: String) {
        pause()
        users.removeAll { it.id == id }
        passwords.remove(id)
    }
}
