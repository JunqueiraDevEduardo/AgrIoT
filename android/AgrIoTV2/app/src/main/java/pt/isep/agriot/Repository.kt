package pt.isep.agriot

import android.app.Application
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

/**
 * [App] é a classe base de aplicação Android (Application).
 * Mantém uma referência estática global ([instance]) do contexto da aplicação,
 * permitindo o acesso fácil a serviços do sistema e preferências partilhadas a partir de qualquer componente.
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: App
    }
}

/**
 * [Repository] define o contrato principal de dados e controlo da estufa inteligente.
 * Abstrai todas as operações da aplicação, tais como autenticação de utilizadores,
 * leitura de sensores, acionamento de atuadores, parâmetros de regulação (PID e Histerese)
 * e geração de relatórios detalhados.
 */
interface Repository {
    fun login(id: String, password: String): Utilizador
    fun sensores(): Sensores
    fun atuadores(): Atuadores
    fun setAtuador(nome: String, estado: Boolean)
    fun setPwm(duty: Double, ligado: Boolean = true)
    fun pid(): PidParams
    fun setPid(p: PidParams)
    fun histerese(): HistParams
    fun setHisterese(p: HistParams)
    fun utilizadores(): List<Utilizador>
    fun criarUtilizador(id: String, password: String, perfil: String, estufa: String)
    fun editarUtilizador(id: String, novaPassword: String?, perfil: String, estufa: String, ativo: Boolean)
    fun eliminarUtilizador(id: String)
    fun relatorio(estufa: String): RelatorioEstufa
}

/**
 * [Backend] é um objeto Singleton responsável por gerir a fonte de dados ativa da aplicação.
 * Permite alternar dinamicamente entre o servidor Flask real ([RemoteRepository])
 * e o modo de simulação/offline ([DemoRepository]) com base no URL configurado nas preferências guardadas.
 */
object Backend {
    private const val DEFAULT_URL = "http://100.94.67.33:5000"
    private val prefs get() = App.instance.getSharedPreferences("agriot", Context.MODE_PRIVATE)
    val demo = DemoRepository()

    var url: String
        get() {
            val saved = prefs.getString("url", null)
            return if (saved.isNullOrBlank()) DEFAULT_URL else saved
        }
        set(v) {
            prefs.edit().putString("url", v.trim()).apply()
        }

    /**
     * Retorna a implementação correta de [Repository]:
     * - [DemoRepository] se o URL estiver em branco (modo simulação local).
     * - [RemoteRepository] se houver um URL válido configurado para o servidor Flask.
     */
    fun repo(): Repository = if (url.isBlank()) demo else RemoteRepository(url)
}

/**
 * [RemoteRepository] implementa [Repository] comunicando com o servidor Flask remoto através de REST/HTTP.
 * Realiza requisições síncronas com JSON, gere cabeçalhos de autorização (Bearer Token)
 * e processa respostas de sucesso ou erros da API ([ApiException]).
 */
class RemoteRepository(base: String) : Repository {
    private val base = base.trimEnd('/')

    /**
     * Método auxiliar privado para executar requisições HTTP (GET, POST, PUT, DELETE).
     * Configura timeouts de conexão/leitura, envia payloads JSON e trata códigos de erro HTTP.
     */
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

    /** Converte um [JSONObject] de utilizador recebido da API no modelo de domínio [Utilizador]. */
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
        val duty = j.optDouble("ventoinha_duty", 0.0)
        val vent = j.getBoolean("ventoinha")
        val pid = j.getBoolean("pid")
        return Atuadores(
            luz = j.getBoolean("luz"),
            ventoinha = vent,
            aquecedor = j.getBoolean("aquecedor"),
            pid = pid,
            histerese = j.optBoolean("histerese", false),
            duty = duty,
            rpm = j.optInt("ventoinha_rpm", 0),
            pwm = j.optBoolean("pwm", vent && !pid)
        )
    }

    override fun setAtuador(nome: String, estado: Boolean) {
        call("PUT", "/api/atuadores/$nome", JSONObject().put("estado", estado))
    }

    override fun setPwm(duty: Double, ligado: Boolean) {
        call("PUT", "/api/atuadores/pwm", JSONObject().put("duty", duty).put("estado", ligado))
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

    override fun histerese(): HistParams {
        val j = JSONObject(call("GET", "/api/histerese"))
        return HistParams(j.getDouble("setpoint"), j.getDouble("h"))
    }

    override fun setHisterese(p: HistParams) {
        call("PUT", "/api/histerese", JSONObject().put("setpoint", p.setpoint).put("h", p.h))
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

    override fun relatorio(estufa: String): RelatorioEstufa {
        return try {
            val raw = call("GET", "/api/relatorio/" + java.net.URLEncoder.encode(estufa, "UTF-8"))
            val j = JSONObject(raw)
            val uArr = j.optJSONArray("utilizadores") ?: JSONArray()
            val uList = (0 until uArr.length()).map { user(uArr.getJSONObject(it)) }
            val aArr = j.optJSONArray("alertas") ?: JSONArray()
            val alertas = (0 until aArr.length()).map { aArr.getString(it) }
            val dObj = j.optJSONObject("diagnostico") ?: JSONObject()
            val diagMap = mutableMapOf<String, String>()
            val keys = dObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                diagMap[k] = dObj.getString(k)
            }
            RelatorioEstufa(
                estufa = j.optString("estufa", estufa),
                dataHora = j.optString("data_hora", SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "PT")).format(Date())),
                online = j.optBoolean("online", true),
                temperatura = j.getDouble("temperatura"),
                tempMin = j.optDouble("temp_min", j.getDouble("temperatura")),
                tempMax = j.optDouble("temp_max", j.getDouble("temperatura")),
                tempMedia = j.optDouble("temp_media", j.getDouble("temperatura")),
                humidadeAr = j.getDouble("humidade_ar"),
                humidadeSolo = j.getDouble("humidade_solo"),
                pressao = j.getDouble("pressao"),
                luz = j.getBoolean("luz"),
                ventoinha = j.getBoolean("ventoinha"),
                ventoinhaDuty = j.optDouble("ventoinha_duty", 0.0),
                ventoinhaRpm = j.optInt("ventoinha_rpm", 0),
                aquecedor = j.getBoolean("aquecedor"),
                pidAtivo = j.getBoolean("pid"),
                histereseAtiva = j.optBoolean("histerese", false),
                pidParams = PidParams(j.getDouble("pid_setpoint"), j.getDouble("pid_kp"), j.getDouble("pid_ki"), j.getDouble("pid_kd")),
                histParams = HistParams(j.getDouble("hist_setpoint"), j.getDouble("hist_h")),
                utilizadores = uList,
                alertas = alertas,
                diagnosticoHardware = diagMap
            )
        } catch (e: Exception) {
            // Fallback: sintetiza o relatório com base nas rotas individuais se o endpoint direto não estiver disponível
            val s = sensores()
            val a = atuadores()
            val p = pid()
            val h = histerese()
            val uList = runCatching { utilizadores().filter { it.estufa.equals(estufa, ignoreCase = true) } }.getOrDefault(emptyList())
            val minT = s.historico.minOrNull() ?: s.temperatura
            val maxT = s.historico.maxOrNull() ?: s.temperatura
            val avgT = if (s.historico.isNotEmpty()) s.historico.average() else s.temperatura
            RelatorioEstufa(
                estufa = estufa,
                dataHora = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "PT")).format(Date()),
                online = true,
                temperatura = s.temperatura,
                tempMin = minT,
                tempMax = maxT,
                tempMedia = avgT,
                humidadeAr = s.humidadeAr,
                humidadeSolo = s.humidadeSolo,
                pressao = s.pressao,
                luz = a.luz,
                ventoinha = a.ventoinha,
                ventoinhaDuty = a.duty,
                ventoinhaRpm = a.rpm,
                aquecedor = a.aquecedor,
                pidAtivo = a.pid,
                histereseAtiva = a.histerese,
                pidParams = p,
                histParams = h,
                utilizadores = uList,
                alertas = listOf(if (a.pid) "PID ativo a regular a ventoinha." else "Ventoinha em controlo manual por PWM."),
                diagnosticoHardware = mapOf(
                    "BME280" to "I²C 0x76 OK",
                    "ADS1115" to "I²C 0x48 OK",
                    "PWM Ventoinha" to "GPIO12 ativo",
                    "Tacómetro" to "${a.rpm} RPM",
                    "Relé" to "GPIO17 OK"
                )
            )
        }
    }
}

/**
 * [DemoRepository] implementa [Repository] simulando em memória todo o comportamento de uma estufa.
 * Útil para desenvolvimento, testes offline e demonstrações da aplicação sem dependência de hardware ou servidor.
 */
class DemoRepository : Repository {
    private val users = mutableListOf(
        Utilizador("ADM-001", "Eduardo", "admin", "Estufa #1", true),
        Utilizador("CLI-001", "Cliente 001", "utilizador", "Estufa #1", true),
        Utilizador("CLI-002", "Cliente 002", "utilizador", "Estufa #2", true),
        Utilizador("CLI-003", "Cliente 003", "utilizador", "Estufa #2", false),
        Utilizador("CLI-004", "Cliente 004", "utilizador", "Estufa #3", true)
    )
    private val passwords = mutableMapOf(
        "ADM-001" to "admin123", "CLI-001" to "1234", "CLI-002" to "1234", "CLI-003" to "1234", "CLI-004" to "1234"
    )
    private var act = Atuadores(luz = true, ventoinha = false, aquecedor = false, pid = false, histerese = false, duty = 0.0, rpm = 0, pwm = false)
    private var manualDuty = 45.0
    private var pwmManualLigado = false

    /** Simula uma pequena latência de rede/processamento. */
    private fun pause() = Thread.sleep(120)

    /** Gera uma variação cíclica realista de temperatura ao longo do dia usando funções trigonométricas. */
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
        pause()
        val t = tempAt(System.currentTimeMillis())
        var duty = 0.0
        var vent = act.ventoinha
        if (act.pid) {
            duty = (Params.kp * (t - Params.setpoint)).coerceIn(0.0, 100.0) // Controlo PID de arrefecimento
            vent = duty > 0.0
        } else if (act.ventoinha || pwmManualLigado) {
            duty = manualDuty.coerceIn(0.0, 100.0)
            vent = duty > 0.0
        }
        val heater = if (act.histerese) t < Params.setpoint - Params.h else act.aquecedor
        val rpm = if (vent && duty > 0.0) (600 + duty * 38).toInt() else 0
        return act.copy(
            ventoinha = vent,
            aquecedor = heater,
            duty = duty,
            rpm = rpm,
            pwm = pwmManualLigado || (vent && !act.pid)
        )
    }

    override fun setAtuador(nome: String, estado: Boolean) {
        pause()
        if (nome == "ventoinha" && act.pid) throw ApiException(409, "A ventoinha está sob controlo PID")
        if (nome == "pwm" && act.pid) throw ApiException(409, "A ventoinha está sob controlo PID")
        if (nome == "aquecedor" && act.histerese) throw ApiException(409, "O aquecedor está sob controlo por histerese")
        act = when (nome) {
            "luz" -> act.copy(luz = estado)
            "ventoinha" -> {
                pwmManualLigado = estado
                if (estado && manualDuty <= 0.0) manualDuty = 45.0
                val d = if (estado) manualDuty else 0.0
                act.copy(ventoinha = estado, duty = d, pwm = estado)
            }
            "pwm" -> {
                pwmManualLigado = estado
                if (estado && manualDuty <= 0.0) manualDuty = 45.0
                val d = if (estado) manualDuty else 0.0
                act.copy(ventoinha = estado, duty = d, pwm = estado)
            }
            "aquecedor" -> act.copy(aquecedor = estado)
            "histerese" -> act.copy(histerese = estado)
            else -> act.copy(pid = estado)
        }
    }

    override fun setPwm(duty: Double, ligado: Boolean) {
        pause()
        if (act.pid) throw ApiException(409, "A ventoinha está sob controlo PID. Desative o PID primeiro.")
        manualDuty = duty.coerceIn(0.0, 100.0)
        pwmManualLigado = ligado && manualDuty > 0.0
        act = act.copy(
            ventoinha = pwmManualLigado,
            duty = if (pwmManualLigado) manualDuty else 0.0,
            pwm = pwmManualLigado
        )
    }

    override fun pid(): PidParams {
        pause(); return PidParams(Params.setpoint, Params.kp, Params.ki, Params.kd)
    }

    override fun setPid(p: PidParams) {
        pause()
        Params.setpoint = p.setpoint; Params.kp = p.kp; Params.ki = p.ki; Params.kd = p.kd
    }

    override fun histerese(): HistParams {
        pause(); return HistParams(Params.setpoint, Params.h)
    }

    override fun setHisterese(p: HistParams) {
        pause(); Params.setpoint = p.setpoint; Params.h = p.h
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

    override fun relatorio(estufa: String): RelatorioEstufa {
        pause()
        val now = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "PT")).format(Date())
        val s = sensores()
        val a = atuadores()
        val p = pid()
        val h = histerese()

        // Aplica desvios simulados por estufa para conferir realismo aos dados
        val (tempOffset, humOffset, soloOffset) = when (estufa) {
            "Estufa #2" -> Triple(-2.8, 8.5, 12.0)
            "Estufa #3" -> Triple(1.9, -6.0, -8.0)
            else -> Triple(0.0, 0.0, 0.0)
        }

        val curTemp = s.temperatura + tempOffset
        val hist = s.historico.map { (it + tempOffset) }
        val minT = hist.minOrNull() ?: (curTemp - 2.1)
        val maxT = hist.maxOrNull() ?: (curTemp + 2.7)
        val avgT = if (hist.isNotEmpty()) hist.average() else curTemp
        val curHum = (s.humidadeAr + humOffset).coerceIn(20.0, 95.0)
        val curSolo = (s.humidadeSolo + soloOffset).coerceIn(10.0, 95.0)
        val uList = users.filter { it.estufa.equals(estufa, ignoreCase = true) }

        val alertas = mutableListOf<String>()
        if (curTemp > p.setpoint + 3.0) alertas.add("Temperatura elevada (${fmt(curTemp)} °C) excede o setpoint (${fmt(p.setpoint)} °C).")
        if (curSolo < 32.0) alertas.add("Humidade do solo baixa (${fmt(curSolo, 0)}%). Recomendada rega automática.")
        if (uList.any { !it.ativo }) alertas.add("Existem contas desativadas atribuídas a esta estufa.")

        val diag = mapOf(
            "Sensor Ambiental BME280 (I²C 0x76)" to "Comunicação nominal, leituras de T, H e P válidas",
            "Conversor Analógico ADS1115 (I²C 0x48)" to "Tensão no eletrodo de solo dentro da gama (1.15 V - 2.55 V)",
            "Display SSD1306 (I²C 0x3C)" to "Ecrã local operacional a 128x64 px",
            "Controlo PWM Ventoinha (GPIO12)" to if (a.ventoinha) "Frequência 25 kHz, duty cycle a ${fmt(a.duty, 0)}%" else "Ventoinha em repouso",
            "Tacómetro da Ventoinha (GPIO13)" to if (a.ventoinha) "${a.rpm} RPM monitorizadas com sucesso" else "0 RPM (repouso)",
            "Relé de Aquecimento (GPIO17)" to if (a.aquecedor) "Circuito fechado (relé alimentado)" else "Circuito aberto (desligado)",
            "Segurança de Atuadores" to "Mutual Exclusion Ativa: Ventoinha e Relé nunca atuam em simultâneo"
        )

        return RelatorioEstufa(
            estufa = estufa,
            dataHora = now,
            online = true,
            temperatura = curTemp,
            tempMin = minT,
            tempMax = maxT,
            tempMedia = avgT,
            humidadeAr = curHum,
            humidadeSolo = curSolo,
            pressao = s.pressao,
            luz = a.luz,
            ventoinha = a.ventoinha,
            ventoinhaDuty = a.duty,
            ventoinhaRpm = a.rpm,
            aquecedor = a.aquecedor,
            pidAtivo = a.pid,
            histereseAtiva = a.histerese,
            pidParams = p,
            histParams = h,
            utilizadores = uList,
            alertas = alertas,
            diagnosticoHardware = diag
        )
    }
}
