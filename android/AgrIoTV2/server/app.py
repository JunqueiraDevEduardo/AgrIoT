"""
Servidor AgrIoT (Flask + SQLite) - Raspberry Pi 3 Model B+.

Camada 2 da arquitetura: API REST (JSON) -> autenticação/perfil -> módulo de controlo -> base de dados.
Controlo (secção 3.1 do relatório):
  - Aquecimento por HISTERESE: relé liga se T < Tset - h; desliga se T >= Tset.
  - Ventoinha com PID:  e = T - Tset;  u = Kp*e + Ki*Ts*soma(e) + Kd*(e - e_ant)/Ts,  limitado a 0-100 %.
    A ventoinha só atua quando T > Tset (só consegue arrefecer). Relé e ventoinha nunca atuam em simultâneo.

Executar:  pip install -r requirements.txt && python app.py      (escuta em 0.0.0.0:5000, usar o IP Tailscale)
Credenciais iniciais: ADM-001 / admin123  |  CLI-001, CLI-002, CLI-003 / 1234
"""
import math
import random
import secrets
import sqlite3
import threading
import time
from functools import wraps

from flask import Flask, g, jsonify, request
from werkzeug.security import check_password_hash, generate_password_hash

DB = "agriot.db"
TS = 5.0  # período do ciclo de controlo (s). No sistema real o relatório sugere, por exemplo, 60 s.
app = Flask(__name__)
tokens = {}
lock = threading.Lock()

# ------------------------------------------------------------ estado partilhado
estado = {"luz": False, "ventoinha": False, "aquecedor": False, "pid": False, "histerese": False}
params = {"setpoint": 28.0, "h": 2.0, "kp": 12.0, "ki": 0.4, "kd": 2.0}
ultima = {"temperatura": 24.0, "humidade_ar": 60.0, "humidade_solo": 40.0, "pressao": 1013.0}
vent = {"duty": 0.0, "rpm": 0}


# ------------------------------------------------------------ hardware
class Hardware:
    """
    Hardware do relatório (os pinos do esquemático podem não ser os finais):
      BME280  I2C 0x76/0x77  -> temperatura, humidade do ar, pressão (300-1100 hPa)
      ADS1115 I2C 0x48       -> sensor capacitivo de humidade do solo (a tensão desce com a humidade)
      OLED SSD1306 I2C 0x3C  -> valores locais; botão (GPIO11) alterna a página
      Ventoinha 4 pinos      -> PWM GPIO12 (azul), tacómetro GPIO13 (amarelo, pull-up 4,7 kOhm)
      Relé (resistência 3,3 ohm / 30 W) -> GPIO17 (sinal S), contactos COM-NO
      LED                    -> GPIO16
    Por agora é simulado, para a app poder ser testada sem o Raspberry Pi.
    """
    V_SECO = 2.55      # tensão ao ar (calibrar: média de 50 amostras)
    V_MOLHADO = 1.15   # tensão dentro de água, até à linha indicada

    def __init__(self):
        self.t = 24.0
        self.last = time.time()
        self.heater = False
        self.duty = 0.0

    def ler(self):
        now = time.time()
        dt = min(30.0, now - self.last)
        self.last = now
        # estufa: ambiente quente (35 C sem ventilação), o relé aquece, a ventoinha arrefece
        t_eq = 24.0 + 6.0 * math.sin(now / 900.0) + (14.0 if self.heater else 0.0)
        self.t += ((t_eq - self.t) - 0.15 * self.duty) * dt / 120.0
        v_solo = 1.9 + 0.15 * math.sin(now / 1500.0) + random.uniform(-0.01, 0.01)
        solo = (self.V_SECO - v_solo) / (self.V_SECO - self.V_MOLHADO) * 100.0
        return {
            "temperatura": self.t + random.uniform(-0.05, 0.05),
            "humidade_ar": 62 + 4 * math.sin(now / 900) + random.uniform(-0.3, 0.3),
            "humidade_solo": max(0.0, min(100.0, solo)),
            "pressao": 1013 + random.uniform(-0.5, 0.5),
        }

    def atuar_ventoinha(self, duty):
        self.duty = duty  # TODO: PWM por hardware no GPIO12 (p. ex. pigpio hardware_PWM, 25 kHz)

    def atuar_rele(self, ligado):
        self.heater = ligado  # TODO: GPIO do relé

    def rpm(self):
        return int(600 + self.duty * 38) if self.duty > 0 else 0  # TODO: contar pulsos do tacómetro


hw = Hardware()


# ------------------------------------------------------------ base de dados
def db():
    if "db" not in g:
        g.db = sqlite3.connect(DB)
        g.db.row_factory = sqlite3.Row
    return g.db


@app.teardown_appcontext
def close_db(_):
    d = g.pop("db", None)
    if d:
        d.close()


def init_db():
    c = sqlite3.connect(DB)
    c.execute("CREATE TABLE IF NOT EXISTS utilizadores (id TEXT PRIMARY KEY, nome TEXT, perfil TEXT,"
              " estufa TEXT, ativo INTEGER, hash TEXT)")
    c.execute("CREATE TABLE IF NOT EXISTS leituras (ts INTEGER, temperatura REAL, humidade_ar REAL,"
              " humidade_solo REAL, pressao REAL)")
    c.execute("CREATE TABLE IF NOT EXISTS atuadores (nome TEXT PRIMARY KEY, estado INTEGER)")
    c.execute("CREATE TABLE IF NOT EXISTS parametros (nome TEXT PRIMARY KEY, valor REAL)")
    if c.execute("SELECT COUNT(*) FROM utilizadores").fetchone()[0] == 0:
        for i, n, p, e, a, pw in [
            ("ADM-001", "Eduardo", "admin", "Estufa #1", 1, "admin123"),
            ("CLI-001", "Cliente 001", "utilizador", "Estufa #1", 1, "1234"),
            ("CLI-002", "Cliente 002", "utilizador", "Estufa #2", 1, "1234"),
            ("CLI-003", "Cliente 003", "utilizador", "Estufa #2", 0, "1234"),
        ]:
            c.execute("INSERT INTO utilizadores VALUES (?,?,?,?,?,?)", (i, n, p, e, a, generate_password_hash(pw)))
    # recupera estado e parâmetros guardados (o sistema retoma após falha de alimentação)
    for nome, v in c.execute("SELECT nome, estado FROM atuadores"):
        if nome in estado:
            estado[nome] = bool(v)
    estado["aquecedor"] = False  # o relé arranca sempre desligado (estado seguro)
    estado["ventoinha"] = False
    for nome, v in c.execute("SELECT nome, valor FROM parametros"):
        if nome in params:
            params[nome] = v
    c.commit()
    c.close()


def guardar_parametros():
    c = sqlite3.connect(DB)
    for k, v in params.items():
        c.execute("INSERT OR REPLACE INTO parametros VALUES (?,?)", (k, v))
    c.commit()
    c.close()


# ------------------------------------------------------------ autenticação
def requer(admin=False):
    def deco(fn):
        @wraps(fn)
        def wrap(*a, **kw):
            tok = request.headers.get("Authorization", "").replace("Bearer ", "")
            uid = tokens.get(tok)
            if not uid:
                return jsonify(erro="Sessão inválida"), 401
            u = db().execute("SELECT * FROM utilizadores WHERE id=?", (uid,)).fetchone()
            if not u or not u["ativo"]:
                return jsonify(erro="Conta desativada"), 403
            if admin and u["perfil"] != "admin":
                return jsonify(erro="Operação reservada a administradores"), 403
            g.user = u
            return fn(*a, **kw)

        return wrap

    return deco


def user_json(r):
    return {"id": r["id"], "nome": r["nome"], "perfil": r["perfil"], "estufa": r["estufa"], "ativo": bool(r["ativo"])}


@app.post("/api/login")
def login():
    d = request.get_json(force=True)
    uid = str(d.get("id", "")).strip().upper()
    r = db().execute("SELECT * FROM utilizadores WHERE id=?", (uid,)).fetchone()
    if not r or not check_password_hash(r["hash"], d.get("password", "")):
        return jsonify(erro="ID ou palavra-passe incorretos"), 401
    if not r["ativo"]:
        return jsonify(erro="Conta desativada"), 403
    tok = secrets.token_hex(16)
    tokens[tok] = r["id"]
    return jsonify(token=tok, **user_json(r))


# ------------------------------------------------------------ sensores / atuadores / controlo
@app.get("/api/sensores")
@requer()
def sensores():
    rows = db().execute(
        "SELECT ts/1800 AS b, AVG(temperatura) AS t FROM leituras WHERE ts > ? GROUP BY b ORDER BY b",
        (int(time.time()) - 86400,),
    ).fetchall()
    with lock:
        cur = dict(ultima)
    return jsonify(**cur, historico=[round(r["t"], 2) for r in rows] or [cur["temperatura"]] * 2)


@app.get("/api/atuadores")
@requer()
def get_atuadores():
    with lock:
        return jsonify(**estado, ventoinha_duty=round(vent["duty"], 1), ventoinha_rpm=vent["rpm"])


@app.put("/api/atuadores/<nome>")
@requer()
def set_atuador(nome):
    if nome not in estado:
        return jsonify(erro="Atuador desconhecido"), 404
    val = bool(request.get_json(force=True).get("estado"))
    with lock:
        if nome == "ventoinha" and estado["pid"]:
            return jsonify(erro="A ventoinha está sob controlo PID"), 409
        if nome == "aquecedor" and estado["histerese"]:
            return jsonify(erro="O aquecedor está sob controlo por histerese"), 409
        estado[nome] = val
        if nome == "ventoinha":
            vent["duty"] = 100.0 if val else 0.0
            hw.atuar_ventoinha(vent["duty"]); vent["rpm"] = hw.rpm()
        if nome == "aquecedor":
            hw.atuar_rele(val)
        if nome in ("pid", "histerese") and not val:
            # ao desligar o controlo automático o atuador volta ao estado seguro (desligado)
            if nome == "pid":
                estado["ventoinha"] = False; vent["duty"] = 0.0; hw.atuar_ventoinha(0.0); vent["rpm"] = 0
            else:
                estado["aquecedor"] = False; hw.atuar_rele(False)
    db().execute("INSERT OR REPLACE INTO atuadores VALUES (?,?)", (nome, int(val)))
    db().commit()
    return jsonify(nome=nome, estado=val)


@app.get("/api/pid")
@requer()
def get_pid():
    with lock:
        return jsonify(**{k: params[k] for k in ("setpoint", "kp", "ki", "kd")})


@app.put("/api/pid")
@requer()
def set_pid():
    d = request.get_json(force=True)
    with lock:
        for k in ("setpoint", "kp", "ki", "kd"):
            if k in d:
                params[k] = float(d[k])
    guardar_parametros()
    return get_pid()


@app.get("/api/histerese")
@requer()
def get_hist():
    with lock:
        return jsonify(setpoint=params["setpoint"], h=params["h"])


@app.put("/api/histerese")
@requer()
def set_hist():
    d = request.get_json(force=True)
    with lock:
        if "setpoint" in d:
            params["setpoint"] = float(d["setpoint"])
        if "h" in d:
            params["h"] = max(0.1, float(d["h"]))
    guardar_parametros()
    return get_hist()


# ------------------------------------------------------------ utilizadores (admin)
@app.get("/api/utilizadores")
@requer(admin=True)
def listar():
    return jsonify([user_json(r) for r in db().execute("SELECT * FROM utilizadores ORDER BY id")])


@app.post("/api/utilizadores")
@requer(admin=True)
def criar():
    d = request.get_json(force=True)
    uid = str(d.get("id", "")).strip().upper()
    if not uid or not d.get("password"):
        return jsonify(erro="ID e palavra-passe obrigatórios"), 400
    if db().execute("SELECT 1 FROM utilizadores WHERE id=?", (uid,)).fetchone():
        return jsonify(erro="Já existe um utilizador com esse ID"), 409
    db().execute("INSERT INTO utilizadores VALUES (?,?,?,?,1,?)",
                 (uid, uid, d.get("perfil", "utilizador"), d.get("estufa", "Estufa #1"), generate_password_hash(d["password"])))
    db().commit()
    return jsonify(id=uid), 201


@app.put("/api/utilizadores/<uid>")
@requer(admin=True)
def editar(uid):
    d = request.get_json(force=True)
    if uid == g.user["id"] and (d.get("perfil") != "admin" or not d.get("ativo", True)):
        return jsonify(erro="Não pode remover o seu próprio acesso de administrador"), 400
    if not db().execute("SELECT 1 FROM utilizadores WHERE id=?", (uid,)).fetchone():
        return jsonify(erro="Utilizador não encontrado"), 404
    db().execute("UPDATE utilizadores SET perfil=?, estufa=?, ativo=? WHERE id=?",
                 (d.get("perfil", "utilizador"), d.get("estufa", "Estufa #1"), int(bool(d.get("ativo", True))), uid))
    if d.get("password"):
        db().execute("UPDATE utilizadores SET hash=? WHERE id=?", (generate_password_hash(d["password"]), uid))
    db().commit()
    return jsonify(id=uid)


@app.delete("/api/utilizadores/<uid>")
@requer(admin=True)
def eliminar(uid):
    if uid == g.user["id"]:
        return jsonify(erro="A conta em sessão não pode ser eliminada"), 400
    db().execute("DELETE FROM utilizadores WHERE id=?", (uid,))
    db().commit()
    return jsonify(id=uid)


# ------------------------------------------------------------ ciclo de controlo
def controlo():
    """Estado seguro no arranque; depois, em cada ciclo Ts: ler, controlar e (a cada 60 s) registar."""
    c = sqlite3.connect(DB)
    hw.atuar_rele(False)
    hw.atuar_ventoinha(0.0)
    soma, ant, primeiro, ultimo_log = 0.0, 0.0, True, 0.0
    while True:
        r = hw.ler()
        with lock:
            ultima.update(r)
            p = dict(params)
            pid_on, hist_on = estado["pid"], estado["histerese"]
            rele = estado["aquecedor"]
        T = r["temperatura"]
        if time.time() - ultimo_log >= 60:
            c.execute("INSERT INTO leituras VALUES (?,?,?,?,?)",
                      (int(time.time()), T, r["humidade_ar"], r["humidade_solo"], r["pressao"]))
            c.commit()
            ultimo_log = time.time()

        if hist_on:  # histerese: liga em T < Tset - h; desliga em T >= Tset; no meio mantém
            if not rele and T < p["setpoint"] - p["h"]:
                rele = True
            elif rele and T >= p["setpoint"]:
                rele = False
            hw.atuar_rele(rele)

        if pid_on:  # PID: e = T - Tset; só atua se T > Tset
            e = T - p["setpoint"]
            if e <= 0:
                u, soma, ant, primeiro = 0.0, 0.0, e, False
            else:
                d = 0.0 if primeiro else (e - ant) / TS
                cand = soma + e
                bruta = p["kp"] * e + p["ki"] * TS * cand + p["kd"] * d
                if bruta <= 100:
                    soma = cand
                u, ant, primeiro = max(0.0, min(100.0, bruta)), e, False
            hw.atuar_ventoinha(u)
            with lock:
                vent["duty"], vent["rpm"] = u, hw.rpm()
                estado["ventoinha"] = u > 0
        else:
            soma, ant, primeiro = 0.0, 0.0, True

        # relé e ventoinha nunca em simultâneo (garantido pela lógica; a proteção final fica aqui)
        with lock:
            estado["aquecedor"] = rele
            if rele and vent["duty"] > 0 and pid_on:
                rele = False
                estado["aquecedor"] = False
                hw.atuar_rele(False)
        time.sleep(TS)


if __name__ == "__main__":
    init_db()
    threading.Thread(target=controlo, daemon=True).start()
    app.run(host="0.0.0.0", port=5000)
