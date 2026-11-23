# ==================================================
# Biblioteca do ecrã OLED 0.96" (SSD1306 128x64, I2C) com smbus2
#
# Ligações (Raspberry Pi 3B):
#   VCC -> 3.3V   (pino 1)
#   GND -> GND    (pino 6)
#   SDA -> GPIO2  (pino 3)
#   SCL -> GPIO3  (pino 5)
#
# Preparação:
#   sudo raspi-config  -> Interface Options -> I2C -> Enable
#   sudo apt install python3-smbus2 python3-pil
#   (ou: pip install smbus2 pillow --break-system-packages)
#   Confirmar o endereço:  i2cdetect -y 1   (deve aparecer 3c)
#
# Funções:
#   escrever("linha1", "linha2", ...)  -> mostra até 6 linhas de texto
#   limpar()                           -> apaga o ecrã
#   parar()                            -> apaga e fecha o I2C
# ==================================================

import time
from smbus2 import SMBus
from PIL import Image, ImageDraw, ImageFont

I2C_BUS = 1            # Raspberry Pi 3B usa o bus 1
ENDERECO = 0x3C        # alguns ecrãs usam 0x3D
LARGURA = 128
ALTURA = 64
ALTURA_LINHA = 10      # pixels por linha de texto

bus = SMBus(I2C_BUS)
fonte = ImageFont.load_default()


def _comando(*cmds):
    for c in cmds:
        bus.write_i2c_block_data(ENDERECO, 0x00, [c])


def _dados(lista):
    for i in range(0, len(lista), 16):
        bus.write_i2c_block_data(ENDERECO, 0x40, lista[i:i + 16])


def _enviar(imagem):
    # Converte a imagem (1 bit) para o formato de páginas do SSD1306
    pix = imagem.load()
    buffer = []
    for pagina in range(ALTURA // 8):
        for x in range(LARGURA):
            byte = 0
            for bit in range(8):
                if pix[x, pagina * 8 + bit]:
                    byte |= 1 << bit
            buffer.append(byte)
    _comando(0x21, 0, LARGURA - 1)       # colunas
    _comando(0x22, 0, ALTURA // 8 - 1)   # páginas
    _dados(buffer)


def escrever(*linhas):
    imagem = Image.new("1", (LARGURA, ALTURA), 0)
    desenho = ImageDraw.Draw(imagem)
    for i, texto in enumerate(linhas[:ALTURA // ALTURA_LINHA]):
        desenho.text((0, i * ALTURA_LINHA), str(texto), font=fonte, fill=1)
    _enviar(imagem)


def limpar():
    _enviar(Image.new("1", (LARGURA, ALTURA), 0))


def parar():
    limpar()
    _comando(0xAE)       # ecrã desligado
    bus.close()


# Inicialização do SSD1306
_comando(0xAE,           # ecrã off
         0xD5, 0x80,     # frequência do relógio
         0xA8, 0x3F,     # multiplex 64
         0xD3, 0x00,     # offset vertical
         0x40,           # linha inicial
         0x8D, 0x14,     # charge pump on
         0x20, 0x00,     # endereçamento horizontal
         0xA1, 0xC8,     # orientação normal
         0xDA, 0x12,     # configuração dos pinos COM
         0x81, 0xCF,     # contraste
         0xD9, 0xF1,
         0xDB, 0x40,
         0xA4, 0xA6,     # imagem normal
         0xAF
         )               # ecrã on
limpar()
