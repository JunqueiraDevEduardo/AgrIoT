"""
Servidor AgrIoT (Flask + SQLite).

Camada 2 da arquitetura: API REST (JSON) -> autenticação/perfil -> módulo de controlo
(PID na ventoinha, histerese no aquecedor) -> acesso à base de dados.

Executar:   pip install -r requirements.txt && python app.py
O servidor escuta em 0.0.0.0:5000 (usar o IP Tailscale 100.x.y.z na app).

Credenciais iniciais: ADM-001 / admin123   |   CLI-001, CLI-002, CLI-003 / 1234
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
app = Flask(__name__)
tokens = {}  # token -> id do utilizador
lock = threading.Lock()

estado = {"luz": False, "ventoinha": False, "aquecedor": False, "pid": False}
pid_params = {"setpoint": 28.0, "kp": 12.0, "ki": 0.4, "kd": 2.0}
ultima = {"temperatura": 24.0, "humidade_ar": 60.0, "humidade_solo": 40.0, "pressao": 1013.0}


# ------------------------------------------------------------------ hardware
class Hardware:
    """TODO: substituir por leituras/comandos reais (GPIO, I2C, ...). Por agora é simulado."""

    def ler(self):
        t = time.time()
        return {
            "temperatura": 24.5 + 2.0 * math.sin(t / 600) + random.uniform(-0.1, 0.1),
            "humidade_ar": 62 + 4 * math.sin(t / 900) + random.uniform(-0.3, 0.3),
            "humidade_solo": 41 + 2 * math.sin(t / 1200),
            "pressao": 1013 + random.uniform(-0.5, 0.5),
        }

    def atuar(self, nome, valor):
        pass  # TODO: ligar/desligar o atuador (valor: bool ou duty-cycle 0-100)


hw = Hardware()


# ------------------------------------------------------------------ base de dados
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
    c.execute(
        "CREATE TABLE IF NOT EXISTS utilizadores (id TEXT PRIMARY KEY, nome TEXT, perfil TEXT,"
        " estufa TEXT, ativo INTEGER, hash TEXT)"
    )
    c.execute(
        "CREATE TABLE IF NOT EXISTS leituras (ts INTEGER, temperatura REAL, humidade_ar REAL,"
        " humidade_solo REAL, pressao REAL)"
    )
    c.execute("CREATE TABLE IF NOT EXISTS atuadores (nome TEXT PRIMARY KEY, estado INTEGER)")
    if c.execute("SELECT COUNT(*) FROM utilizadores").fetchone()[0] == 0:
        seed = [
            ("ADM-001", "Eduardo", "admin", "Estufa #1", 1, "admin123"),
            ("CLI-001", "Cliente 001", "utilizador", "Estufa #1", 1, "1234"),
            ("CLI-002", "Cliente 002", "utilizador", "Estufa #2", 1, "1234"),
            ("CLI-003", "Cliente 003", "utilizador", "Estufa #2", 0, "1234"),
        ]
        for i, n, p, e, a, pw in seed:
            c.execute("INSERT INTO utilizadores VALUES (?,?,?,?,?,?)", (i, n, p, e, a, generate_password_hash(pw)))
    c.commit()
    c.close()


# ------------------------------------------------------------------ autenticação
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


# ------------------------------------------------------------------ sensores / atuadores / pid
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
        return jsonify(**estado)


@app.put("/api/atuadores/<nome>")
@requer()
def set_atuador(nome):
    if nome not in estado:
        return jsonify(erro="Atuador desconhecido"), 404
    val = bool(request.get_json(force=True).get("estado"))
    with lock:
        estado[nome] = val
    if nome != "pid":
        hw.atuar(nome, val)
    db().execute("INSERT OR REPLACE INTO atuadores VALUES (?,?)", (nome, int(val)))
    db().commit()
    return jsonify(nome=nome, estado=val)


@app.get("/api/pid")
@requer()
def get_pid():
    with lock:
        return jsonify(**pid_params)


@app.put("/api/pid")
@requer()
def set_pid():
    d = request.get_json(force=True)
    with lock:
        for k in pid_params:
            if k in d:
                pid_params[k] = float(d[k])
        return jsonify(**pid_params)


# ------------------------------------------------------------------ utilizadores (admin)
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
    db().execute(
        "INSERT INTO utilizadores VALUES (?,?,?,?,1,?)",
        (uid, uid, d.get("perfil", "utilizador"), d.get("estufa", "Estufa #1"), generate_password_hash(d["password"])),
    )
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
    db().execute(
        "UPDATE utilizadores SET perfil=?, estufa=?, ativo=? WHERE id=?",
        (d.get("perfil", "utilizador"), d.get("estufa", "Estufa #1"), int(bool(d.get("ativo", True))), uid),
    )
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


# ------------------------------------------------------------------ módulo de controlo
def controlo():
    """Lê sensores a cada 5 s, regista a cada 60 s, aplica PID (ventoinha) e histerese (aquecedor)."""
    c = sqlite3.connect(DB)
    integral, prev, ultimo_log = 0.0, 0.0, 0
    while True:
        r = hw.ler()
        with lock:
            ultima.update(r)
            p = dict(pid_params)
            ativo = estado["pid"]
        if time.time() - ultimo_log >= 60:
            c.execute("INSERT INTO leituras VALUES (?,?,?,?,?)",
                      (int(time.time()), r["temperatura"], r["humidade_ar"], r["humidade_solo"], r["pressao"]))
            c.commit()
            ultimo_log = time.time()
        if ativo:
            dt = 5.0
            e = r["temperatura"] - p["setpoint"]  # ventoinha arrefece: erro positivo = demasiado quente
            out = max(0.0, min(100.0, p["kp"] * e + p["ki"] * integral + p["kd"] * (e - prev) / dt))
            if 0.0 < out < 100.0:
                integral += e * dt
            prev = e
            with lock:
                estado["ventoinha"] = out > 0
            hw.atuar("ventoinha", out)  # duty-cycle %
            # histerese do aquecedor
            with lock:
                if r["temperatura"] < p["setpoint"] - 3:
                    estado["aquecedor"] = True
                elif r["temperatura"] > p["setpoint"] - 2:
                    estado["aquecedor"] = False
                hw.atuar("aquecedor", estado["aquecedor"])
        else:
            integral, prev = 0.0, 0.0
        time.sleep(5)


if __name__ == "__main__":
    init_db()
    threading.Thread(target=controlo, daemon=True).start()
    app.run(host="0.0.0.0", port=5000)
