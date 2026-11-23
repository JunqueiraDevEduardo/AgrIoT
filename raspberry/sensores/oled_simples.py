# ==================================================
# Ecrã OLED 0.96" (SSD1306, I2C) com luma.oled
#
# Ligações: VCC->pino 1 (3.3V) | GND->pino 6 | SDA->pino 3 | SCL->pino 5
# Instalar: pip install luma.oled
#
# Funções:
#   escrever("linha1", "linha2", ...)  -> mostra até 6 linhas
#   limpar()                           -> apaga o ecrã
# ==================================================

from luma.core.interface.serial import i2c
from luma.core.render import canvas
from luma.oled.device import ssd1306

ecra = ssd1306(i2c(port=1, address=0x3C), width=128, height=64)


def escrever(*linhas):
    with canvas(ecra) as desenho:
        for i, texto in enumerate(linhas):
            desenho.text((0, i * 10), str(texto), fill="white")


def limpar():
    ecra.clear()


if __name__ == "__main__":
    import time
    escrever("AgrioT", "Temp: 25.3 C", "Hum: 60 %")
    time.sleep(5)
    limpar()
