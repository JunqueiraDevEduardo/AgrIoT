# ==================================================
# Ecrã OLED 0.96" (SSD1306, I2C) com luma.oled + botão de páginas
#
# Ligações: VCC->pino 1 (3.3V) | GND->pino 6 | SDA->pino 3 | SCL->pino 5
#           Botão: GPIO24 (pino 18) e GND (pino 20)
# Instalar: pip install luma.oled pigpio
#
# Funções:
#   definir_paginas([["l1","l2"], ["l1","l2"], ...])  -> define as páginas
#   mostrar()      -> desenha a página atual
#   proxima()      -> avança para a página seguinte
#   limpar()       -> apaga o ecrã
#   parar()        -> apaga e fecha o pigpio
# ==================================================

import time
import pigpio
from luma.core.interface.serial import i2c
from luma.core.render import canvas
from luma.oled.device import ssd1306

BOTAO_PIN = 24         # GPIO24 (pino 18)

ecra = ssd1306(i2c(port=1, address=0x3C), width=128, height=64)

paginas = [["Pagina vazia"]]
pagina_atual = 0

pi = pigpio.pi()
if not pi.connected:
    raise SystemExit("pigpiod não está a correr: sudo systemctl start pigpiod")

pi.set_mode(BOTAO_PIN, pigpio.INPUT)
pi.set_pull_up_down(BOTAO_PIN, pigpio.PUD_UP)
pi.set_glitch_filter(BOTAO_PIN, 20000)   # anti-ressalto: ignora pulsos < 20 ms


def definir_paginas(lista):
    global paginas, pagina_atual
    paginas = lista
    pagina_atual = 0


def mostrar():
    linhas = paginas[pagina_atual]
    with canvas(ecra) as desenho:
        for i, texto in enumerate(linhas):
            desenho.text((0, i * 10), str(texto), fill="white")
        # indicador de página no canto inferior direito
        desenho.text((100, 54), f"{pagina_atual + 1}/{len(paginas)}", fill="white")


def proxima():
    global pagina_atual
    pagina_atual = (pagina_atual + 1) % len(paginas)
    mostrar()


def _botao_premido(gpio, level, tick):
    proxima()


cb = pi.callback(BOTAO_PIN, pigpio.FALLING_EDGE, _botao_premido)


def limpar():
    ecra.clear()


def parar():
    cb.cancel()
    limpar()
    pi.stop()


if __name__ == "__main__":
    definir_paginas([
        ["AgrioT", "Pagina 1", "Temp: 25.3 C", "Hum: 60 %"],
        ["Pagina 2", "Aquecimento: ON", "Ventoinha: 40 %"],
        ["Pagina 3", "Solo: 45 %", "Luz: 800 lux"],
    ])
    mostrar()
    print("Carrega no botão para mudar de página. Ctrl+C para sair.")
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        pass
    finally:
        parar()
