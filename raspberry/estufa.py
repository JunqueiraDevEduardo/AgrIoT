"""
estufa.py - Backend Flask da AgrIoT (Estufa Inteligente)

Implementa exatamente o contrato esperado pelo RemoteRepository do app Kotlin
(ver app/src/main/java/pt/isep/agriot/Repository.kt):

    POST   /api/login                  -> {token, id, nome, perfil, estufa, ativo}
    GET    /api/sensores                -> {temperatura, humidade_ar, humidade_solo, pressao, historico:[...]}
    GET    /api/atuadores                -> {luz, ventoinha, aquecedor, pid, histerese, ventoinha_duty, ventoinha_rpm}
    PUT    /api/atuadores/<nome>         <- {estado: bool}
    GET    /api/pid                      -> {setpoint, kp, ki, kd}
    PUT    /api/pid                      <- {setpoint, kp, ki, kd}
    GET    /api/histerese                -> {setpoint, h}
    PUT    /api/histerese                <- {setpoint, h}
    GET    /api/utilizadores             -> [{id, nome, perfil, estufa, ativo}, ...]
    POST   /api/utilizadores             <- {id, password, perfil, estufa}
    PUT    /api/utilizadores/<id>        <- {password?, perfil, estufa, ativo}
    DELETE /api/utilizadores/<id>

Todos os pedidos (exceto login) devem trazer o cabeçalho:
    Authorization: Bearer <token>

Erros respondem com {"erro": "mensagem"} e o código HTTP adequado
(401 credenciais inválidas, 403 conta desativada, 404 não encontrado,
409 conflito, 400 pedido inválido).

Base de dados: SQLite em estufa.db, criada automaticamente na primeira
execução, com um utilizador admin de arranque (ADM-001 / admin123).
"""

from __future__ import annotations

import math
import secrets
import sqlite3
import threading
import time
from contextlib import closing
from pathlib import Path

from flask import Flask, g, jsonify, request
from werkzeug.security import check_password_hash, generate_password_hash

DB_PATH = Path(__file__).parent / "estufa.db"

app = Flask(__name__)

# token -> id do utilizador. Em memória: reinicia ao reiniciar o servidor.
# (se precisares que sobreviva a reinícios, dá para mover para uma tabela sqlite)
TOKENS: dict[str, str] = {}
TOKENS_LOCK = threading.Lock()


# --------------------------------------------------------------------------
# Base de dados
# --------------------------------------------------------------------------

def get_db() -> sqlite3.Connection:
    db = getattr(g, "_db", None)
    if db is None:
        db = g._db = sqlite3.connect(DB_PATH)
        db.row_factory = sqlite3.Row
        db.execute("PRAGMA foreign_keys = ON")
    return db


@app.teardown_appcontext
def close_db(exception=None):
    db = getattr(g, "_db", None)
    if db is not None:
        db.close()


def init_db():
    """Cria as tabelas se ainda não existirem e semeia dados iniciais."""
    with closing(sqlite3.connect(DB_PATH)) as db:
        db.executescript(
            """
            CREATE TABLE IF NOT EXISTS utilizadores (
                id        TEXT PRIMARY KEY,
                nome      TEXT NOT NULL,
                password_hash TEXT NOT NULL,
                perfil    TEXT NOT NULL CHECK (perfil IN ('admin', 'utilizador')),
                estufa    TEXT NOT NULL DEFAULT 'Estufa #1',
                ativo     INTEGER NOT NULL DEFAULT 1
            );

            CREATE TABLE IF NOT EXISTS atuadores (
                id              INTEGER PRIMARY KEY CHECK (id = 1),
                luz             INTEGER NOT NULL DEFAULT 0,
                ventoinha       INTEGER NOT NULL DEFAULT 0,
                aquecedor       INTEGER NOT NULL DEFAULT 0,
                pid             INTEGER NOT NULL DEFAULT 0,
                histerese       INTEGER NOT NULL DEFAULT 0,
                ventoinha_duty  REAL NOT NULL DEFAULT 0,
                ventoinha_rpm   INTEGER NOT NULL DEFAULT 0
            );

            CREATE TABLE IF NOT EXISTS pid_params (
                id        INTEGER PRIMARY KEY CHECK (id = 1),
                setpoint  REAL NOT NULL DEFAULT 24.0,
                kp        REAL NOT NULL DEFAULT 10.0,
                ki        REAL NOT NULL DEFAULT 0.1,
                kd        REAL NOT NULL DEFAULT 0.0
            );

            CREATE TABLE IF NOT EXISTS hist_params (
                id        INTEGER PRIMARY KEY CHECK (id = 1),
                setpoint  REAL NOT NULL DEFAULT 24.0,
                h         REAL NOT NULL DEFAULT 1.0
            );

            CREATE TABLE IF NOT EXISTS leituras (
                id              INTEGER PRIMARY KEY AUTOINCREMENT,
                ts              REAL NOT NULL,
                temperatura     REAL NOT NULL,
                humidade_ar     REAL NOT NULL,
                humidade_solo   REAL NOT NULL,
                pressao         REAL NOT NULL
            );
            """
        db.execute(
            "INSERT OR IGNORE INTO atuadores (id, luz, ventoinha, aquecedor, pid, histerese) "
            "VALUES (1, 1, 0, 0, 0, 0)"
        )
        db.execute("INSERT OR IGNORE INTO pid_params (id) VALUES (1)")
        db.execute("INSERT OR IGNORE INTO hist_params (id) VALUES (1)")

        # Utilizador admin de arranque, só se a tabela estiver vazia
        if db.execute("SELECT COUNT(*) FROM utilizadores").fetchone()[0] == 0:
            db.execute(
                "INSERT INTO utilizadores (id, nome, password_hash, perfil, estufa, ativo) VALUES (?, ?, ?, ?, ?, ?)",
                ("ADM-001", "Eduardo", generate_password_hash("admin123"), "admin", "Estufa #1", 1),
            )
        db.commit()


# --------------------------------------------------------------------------
# Simulação de sensores (substituir por leitura real quando o hardware estiver ligado)
# --------------------------------------------------------------------------

def temp_simulada(ts: float) -> float:
    horas = (ts % 86400) / 3600.0
    return 24.0 + 3.0 * math.sin((horas - 9.0) / 24.0 * 2 * math.pi)

def loop_sensores():
    """Corre em segundo plano: regista uma leitura a cada 30s e aplica o
    controlo automático (PID / histerese) ao aquecedor/ventoinha, tal como
    o DemoRepository do Kotlin fazia em memória."""
    while True:
        try:
            with closing(sqlite3.connect(DB_PATH)) as db:
                db.row_factory = sqlite3.Row  # <--- ADICIONE ESTA LINHA AQUI
                ts = time.time()
                t = temp_simulada(ts)
humidade_ar = 62.0 + 3.0 * math.sin(ts / 60.0)
                humidade_solo = 41.0 + math.sin(ts / 90.0)
                pressao = 1013.0

                db.execute(
                    "INSERT INTO leituras (ts, temperatura, humidade_ar, humidade_solo, pressao) "
                    "VALUES (?, ?, ?, ?, ?)",
                    (ts, t, humidade_ar, humidade_solo, pressao),
                )
                # mantém só as últimas 500 leituras
                db.execute(
                    "DELETE FROM leituras WHERE id NOT IN "
                    "(SELECT id FROM leituras ORDER BY id DESC LIMIT 500)"
                )

                act = db.execute("SELECT * FROM atuadores WHERE id = 1").fetchone()
                duty, rpm = 0.0, 0
                if act["pid"]:
                    pid = db.execute("SELECT * FROM pid_params WHERE id = 1").fetchone()
                    duty = max(0.0, min(100.0, pid["kp"] * (t - pid["setpoint"])))
                    rpm = int(600 + duty * 3.8) if duty > 0 else 0
                    db.execute("UPDATE atuadores SET ventoinha = ?, ventoinha_duty = ?, ventoinha_rpm = ? WHERE id = 1",
                               (1 if duty > 0 else 0, duty, rpm))
                elif act["ventoinha"]:
                    duty, rpm = 100.0, int(600 + 100 * 3.8)
                    db.execute("UPDATE atuadores SET ventoinha_duty = ?, ventoinha_rpm = ? WHERE id = 1", (duty, rpm))

                if act["histerese"]:
                    hp = db.execute("SELECT * FROM hist_params WHERE id = 1").fetchone()
                    aquecedor = 1 if t < hp["setpoint"] - hp["h"] else 0
                    db.execute("UPDATE atuadores SET aquecedor = ? WHERE id = 1", (aquecedor,))

                db.commit()
        except Exception as e:  # nunca deixar o loop morrer
            app.logger.exception("Erro no loop de sensores: %s", e)
        time.sleep(30)


# --------------------------------------------------------------------------
# Autenticação
# --------------------------------------------------------------------------

class ApiError(Exception):
    def __init__(self, code: int, message: str):
        super().__init__(message)
        self.code = code
        self.message = message


@app.errorhandler(ApiError)
def handle_api_error(e: ApiError):
    return jsonify({"erro": e.message}), e.code


def current_user_id() -> str:
    auth = request.headers.get("Authorization", "")
    if not auth.startswith("Bearer "):
        raise ApiError(401, "Sem autenticação")
    token = auth[len("Bearer "):]
    with TOKENS_LOCK:
        uid = TOKENS.get(token)
    if not uid:
        raise ApiError(401, "Sessão inválida, inicie sessão novamente")
    return uid


def current_user_row() -> sqlite3.Row:
    uid = current_user_id()
    row = get_db().execute("SELECT * FROM utilizadores WHERE id = ?", (uid,)).fetchone()
    if row is None or not row["ativo"]:
        raise ApiError(403, "Conta desativada")
    return row


def require_admin():
    if current_user_row()["perfil"] != "admin":
        raise ApiError(403, "Apenas administradores")


def user_json(row: sqlite3.Row) -> dict:
    return {
        "id": row["id"],
        "nome": row["nome"],
        "perfil": row["perfil"],
        "estufa": row["estufa"],
        "ativo": bool(row["ativo"]),
    }


# --------------------------------------------------------------------------
# Rotas: autenticação
# --------------------------------------------------------------------------

@app.post("/api/login")
def login():
    body = request.get_json(force=True, silent=True) or {}
    uid = (body.get("id") or "").strip()
    password = body.get("password") or ""
    if not uid or not password:
        raise ApiError(400, "ID e palavra-passe são obrigatórios")

    db = get_db()
    row = db.execute("SELECT * FROM utilizadores WHERE id = ?", (uid.upper(),)).fetchone()
    if row is None or not check_password_hash(row["password_hash"], password):
        raise ApiError(401, "ID ou palavra-passe incorretos")
    if not row["ativo"]:
        raise ApiError(403, "Conta desativada")

    token = secrets.token_hex(24)
    with TOKENS_LOCK:
        TOKENS[token] = row["id"]

    resp = user_json(row)
    resp["token"] = token
    return jsonify(resp)


# --------------------------------------------------------------------------
# Rotas: sensores
# --------------------------------------------------------------------------

@app.get("/api/sensores")
def sensores():
    current_user_row()
    db = get_db()
    ultima = db.execute("SELECT * FROM leituras ORDER BY id DESC LIMIT 1").fetchone()
    if ultima is None:
        ts = time.time()
        ultima = {
            "temperatura": temp_simulada(ts),
            "humidade_ar": 62.0,
            "humidade_solo": 41.0,
            "pressao": 1013.0,
        }
    historico_rows = db.execute(
        "SELECT temperatura FROM leituras ORDER BY id DESC LIMIT 48"
    ).fetchall()
    historico = [r["temperatura"] for r in reversed(historico_rows)]

    return jsonify({
        "temperatura": ultima["temperatura"],
        "humidade_ar": ultima["humidade_ar"],
        "humidade_solo": ultima["humidade_solo"],
        "pressao": ultima["pressao"],
        "historico": historico,
    })


# --------------------------------------------------------------------------
# Rotas: atuadores
# --------------------------------------------------------------------------

NOMES_ATUADOR = {"luz", "ventoinha", "aquecedor", "pid", "histerese"}


@app.get("/api/atuadores")
def atuadores():
    current_user_row()
    row = get_db().execute("SELECT * FROM atuadores WHERE id = 1").fetchone()
    return jsonify({
        "luz": bool(row["luz"]),
        "ventoinha": bool(row["ventoinha"]),
        "aquecedor": bool(row["aquecedor"]),
        "pid": bool(row["pid"]),
        "histerese": bool(row["histerese"]),
        "ventoinha_duty": row["ventoinha_duty"],
        "ventoinha_rpm": row["ventoinha_rpm"],
    })


@app.put("/api/atuadores/<nome>")
def set_atuador(nome: str):
    current_user_row()
    if nome not in NOMES_ATUADOR:
        raise ApiError(404, "Atuador desconhecido")
    body = request.get_json(force=True, silent=True) or {}
    if "estado" not in body:
        raise ApiError(400, "Campo 'estado' é obrigatório")
    estado = bool(body["estado"])

    db = get_db()
    act = db.execute("SELECT * FROM atuadores WHERE id = 1").fetchone()
    if nome == "ventoinha" and act["pid"]:
        raise ApiError(409, "A ventoinha está sob controlo PID")
    if nome == "aquecedor" and act["histerese"]:
        raise ApiError(409, "O aquecedor está sob controlo por histerese")

    db.execute(f"UPDATE atuadores SET {nome} = ? WHERE id = 1", (1 if estado else 0,))
    db.commit()
    return jsonify({"ok": True})


# --------------------------------------------------------------------------
# Rotas: PID e histerese
# --------------------------------------------------------------------------

@app.get("/api/pid")
def get_pid():
    current_user_row()
    row = get_db().execute("SELECT * FROM pid_params WHERE id = 1").fetchone()
    return jsonify({"setpoint": row["setpoint"], "kp": row["kp"], "ki": row["ki"], "kd": row["kd"]})


@app.put("/api/pid")
def set_pid():
    current_user_row()
    body = request.get_json(force=True, silent=True) or {}
    try:
        setpoint, kp, ki, kd = float(body["setpoint"]), float(body["kp"]), float(body["ki"]), float(body["kd"])
    except (KeyError, TypeError, ValueError):
        raise ApiError(400, "Parâmetros PID inválidos")

    db = get_db()
    db.execute("UPDATE pid_params SET setpoint = ?, kp = ?, ki = ?, kd = ? WHERE id = 1", (setpoint, kp, ki, kd))
    db.commit()
    return jsonify({"ok": True})


@app.get("/api/histerese")
def get_histerese():
    current_user_row()
    row = get_db().execute("SELECT * FROM hist_params WHERE id = 1").fetchone()
    return jsonify({"setpoint": row["setpoint"], "h": row["h"]})


@app.put("/api/histerese")
def set_histerese():
    current_user_row()
    body = request.get_json(force=True, silent=True) or {}
    try:
        setpoint, h = float(body["setpoint"]), float(body["h"])
    except (KeyError, TypeError, ValueError):
        raise ApiError(400, "Parâmetros de histerese inválidos")

    db = get_db()
    db.execute("UPDATE hist_params SET setpoint = ?, h = ? WHERE id = 1", (setpoint, h))
    db.commit()
    return jsonify({"ok": True})


# --------------------------------------------------------------------------
# Rotas: utilizadores (admin)
# --------------------------------------------------------------------------

@app.get("/api/utilizadores")
def utilizadores():
    require_admin()
    rows = get_db().execute("SELECT * FROM utilizadores ORDER BY id").fetchall()
    return jsonify([user_json(r) for r in rows])


@app.post("/api/utilizadores")
def criar_utilizador():
    require_admin()
    body = request.get_json(force=True, silent=True) or {}
    uid = (body.get("id") or "").strip().upper()
    password = body.get("password") or ""
    perfil = body.get("perfil") or "utilizador"
    estufa = body.get("estufa") or "Estufa #1"
    if not uid or not password:
        raise ApiError(400, "ID e palavra-passe são obrigatórios")
    if perfil not in ("admin", "utilizador"):
        raise ApiError(400, "Perfil inválido")

    db = get_db()
    if db.execute("SELECT 1 FROM utilizadores WHERE id = ?", (uid,)).fetchone():
        raise ApiError(409, "Já existe um utilizador com esse ID")

    db.execute(
        "INSERT INTO utilizadores (id, nome, password_hash, perfil, estufa, ativo) VALUES (?, ?, ?, ?, ?, 1)",
        (uid, uid, generate_password_hash(password), perfil, estufa),
    )
    db.commit()
    return jsonify({"ok": True}), 201


@app.put("/api/utilizadores/<uid>")
def editar_utilizador(uid: str):
    require_admin()
    body = request.get_json(force=True, silent=True) or {}
    db = get_db()
    row = db.execute("SELECT * FROM utilizadores WHERE id = ?", (uid,)).fetchone()
    if row is None:
        raise ApiError(404, "Utilizador não encontrado")

    perfil = body.get("perfil", row["perfil"])
    estufa = body.get("estufa", row["estufa"])
    ativo = bool(body.get("ativo", row["ativo"]))
    nova_password = body.get("password")

    if nova_password:
        db.execute(
            "UPDATE utilizadores SET perfil = ?, estufa = ?, ativo = ?, password_hash = ? WHERE id = ?",
            (perfil, estufa, 1 if ativo else 0, generate_password_hash(nova_password), uid),
        )
    else:
        db.execute(
            "UPDATE utilizadores SET perfil = ?, estufa = ?, ativo = ? WHERE id = ?",
            (perfil, estufa, 1 if ativo else 0, uid),
        )
    db.commit()
    return jsonify({"ok": True})


@app.delete("/api/utilizadores/<uid>")
def eliminar_utilizador(uid: str):
    require_admin()
    db = get_db()
    cur = db.execute("DELETE FROM utilizadores WHERE id = ?", (uid,))
    db.commit()
    if cur.rowcount == 0:
        raise ApiError(404, "Utilizador não encontrado")
    return jsonify({"ok": True})


# --------------------------------------------------------------------------
# Arranque
# --------------------------------------------------------------------------

if __name__ == "__main__":
    init_db()
    threading.Thread(target=loop_sensores, daemon=True).start()
    app.run(host="0.0.0.0", port=5000, debug=True, use_reloader=False)
