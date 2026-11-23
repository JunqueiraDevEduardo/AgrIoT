# ==================================================
# Biblioteca do ecrã OLED 0.96" (SSD1306 / SH1106, I2C) com smbus2
#
# Ligações (Raspberry Pi 3B):
#   VCC -> 3.3V   (pino 1)
#   GND -> GND    (pino 6)
#   SDA -> GPIO2  (pino 3)
#   SCL -> GPIO3  (pino 5)
#
# Funções:
#   iniciar()                    -> inicializa o ecrã (True/False)
#   escrever("l1", "l2", ...)    -> mostra até 6 linhas (True/False)
#   limpar()                     -> apaga o ecrã
#   parar()                      -> apaga e fecha o I2C
#
# Nenhuma função rebenta se o ecrã não estiver ligado:
# devolvem False e imprimem um aviso.
# ==================================================

import time
from smbus2 import SMBus
from PIL import Image, ImageDraw, ImageFont

I2C_BUS = 1
ENDERECOS = (0x3C, 0x3D)      # tenta por esta ordem
CONTROLADOR = "SSD1306"       # muda para "SH1106" se a imagem sair deslocada/com ruído
LARGURA = 128
ALTURA = 64
ALTURA_LINHA = 10

_COLUNA_OFFSET = 2 if CONTROLADOR == "SH1106" else 0

bus = None
endereco = None
pronto = False

try:
    fonte = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf", 9)
except Exception:
    fonte = ImageFont.load_default()


def _comando(*cmds):
    for i in range(0, len(cmds), 30):
        bus.write_i2c_block_data(endereco, 0x00, list(cmds[i:i + 30]))


def _dados(lista):
    for i in range(0, len(lista), 32):
        bus.write_i2c_block_data(endereco, 0x40, lista[i:i + 32])


def _detetar():
    global endereco
    for end in ENDERECOS:
        try:
            bus.write_byte(end, 0x00)
            endereco = end
            return True
        except OSError:
            continue
    return False


def iniciar():
    global bus, pronto
    if pronto:
        return True
    try:
        if bus is None:
            bus = SMBus(I2C_BUS)
        if not _detetar():
            print("OLED: nenhum ecrã em 0x3C/0x3D. Verifica ligações e 'i2cdetect -y 1'.")
            return False
        _comando(0xAE,
                 0xD5, 0x80,
                 0xA8, 0x3F,
                 0xD3, 0x00,
                 0x40,
                 0x8D, 0x14,
                 0x20, 0x02,       # modo por páginas (SSD1306 e SH1106)
                 0xA1, 0xC8,       # se estiver de pernas para o ar: 0xA0, 0xC0
                 0xDA, 0x12,
                 0x81, 0xCF,
                 0xD9, 0xF1,
                 0xDB, 0x40,
                 0xA4, 0xA6,
                 0xAF)
        pronto = True
        return True
    except OSError as e:
        print(f"OLED: erro I2C ({e}). Verifica ligações.")
        return False


def _enviar(imagem):
    pix = imagem.load()
    for pagina in range(ALTURA // 8):
        linha = []
        for x in range(LARGURA):
            byte = 0
            for bit in range(8):
                if pix[x, pagina * 8 + bit]:
                    byte |= 1 << bit
            linha.append(byte)
        _comando(0xB0 + pagina,
                 _COLUNA_OFFSET & 0x0F,
                 0x10 | (_COLUNA_OFFSET >> 4))
        _dados(linha)


def escrever(*linhas):
    if not iniciar():
        return False
    try:
        imagem = Image.new("1", (LARGURA, ALTURA), 0)
        desenho = ImageDraw.Draw(imagem)
        for i, texto in enumerate(linhas[:ALTURA // ALTURA_LINHA]):
            desenho.text((0, i * ALTURA_LINHA), str(texto), font=fonte, fill=1)
        _enviar(imagem)
        return True
    except OSError as e:
        print(f"OLED: erro ao escrever ({e})")
        return False


def limpar():
    if not iniciar():
        return False
    try:
        _enviar(Image.new("1", (LARGURA, ALTURA), 0))
        return True
    except OSError:
        return False


def parar():
    global bus, pronto
    try:
        if pronto:
            limpar()
            _comando(0xAE)
    except OSError:
        pass
    finally:
        if bus is not None:
            bus.close()
        bus = None
        pronto = False


if __name__ == "__main__":
    if escrever("AgrioT", "Temp: 25.3 C", "Hum:  60 %", "Teste do ecra OK"):
        print("Texto no ecra. Ctrl+C para sair.")
        try:
            while True:
                time.sleep(1)
        except KeyboardInterrupt:
            pass
    parar()
