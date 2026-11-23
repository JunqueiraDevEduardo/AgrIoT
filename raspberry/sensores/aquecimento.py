# ==================================================
# Biblioteca do aquecimento (resistência 30W 3.3R)
# Relé SONGLE SRD-05VDC-SL-C
#
# Ligações:
#   Módulo relé VCC -> +5V (pino 2 ou 4 do Pi)
#   Módulo relé GND -> GND (pino 6 do Pi ou massa comum)
#   Módulo relé IN  -> GPIO17 (pino 11)
#   Resistência     -> terminais COM e NO do relé (em série com a fonte)
#
# ATENÇÃO: a resistência de 30W / 3.3 ohm não deve ser alimentada
# com mais de ~9.9 V. Usa fusível.
#
# Funções:
#   ligar()      -> liga a resistência
#   desligar()   -> desliga a resistência
#   parar()      -> desliga e fecha o pigpio
# ==================================================

import time
import pigpio

RELE_PIN = 17          # GPIO17 (pino 11)
ATIVO_BAIXO = True     # True se o módulo liga com GPIO a 0 (o mais comum)

pi = pigpio.pi()
if not pi.connected:
    raise SystemExit("pigpiod não está a correr: sudo systemctl start pigpiod")

pi.set_mode(RELE_PIN, pigpio.OUTPUT)


def ligar():
    pi.write(RELE_PIN, 0 if ATIVO_BAIXO else 1)


def desligar():
    pi.write(RELE_PIN, 1 if ATIVO_BAIXO else 0)


def parar():
    desligar()
    pi.stop()


# Garante que arranca desligado
desligar()

if __name__ == "__main__":
    print("Aquecimento LIGADO")
    ligar()
    time.sleep(5)
    print("Aquecimento DESLIGADO")
    desligar()
  #  parar()
