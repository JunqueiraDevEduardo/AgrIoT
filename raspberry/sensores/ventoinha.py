# ==================================================
# Biblioteca da ventoinha Noctua NF-A4x10 5V PWM
#
# Ligações:
#   Preto    -> GND (pino 6 do Pi ou massa comum)
#   Amarelo  -> +5V (pino 2 ou 4 do Pi)
#   Verde    -> GPIO23 (pino 16) 
#   Azul     -> GPIO18 (pino 12) PWM
#
# Referência de RPM (datasheet Noctua):
#   0% -> 0 RPM | 20% -> ~1050 | 40% -> ~2150 | 60% -> ~3250 | 80% -> ~4150 | 100% -> ~5000
#
# Funções:
#   definir_velocidade(0-100)  -> muda o PWM em %
#   ler_rpm()                  -> devolve os RPM
#   parar()                    -> desliga tudo
# ==================================================

import time
import pigpio

PWM_PIN = 18           # GPIO18 (PWM)
TACH_PIN = 23          # Tacómetro (fio verde)
PWM_FREQ = 25000       # 25 kHz (vai de 18 a 30)
PULSES_PER_REV = 2     # 2 pulsos por rotação

pulsos = 0
tempo_anterior = time.time()


def contar_pulso(gpio, level, tick):
    # O pigpio chama esta função sozinho, sempre que o
    # tacómetro faz um pulso. Só soma 1 ao contador.
    global pulsos
    pulsos += 1


pi = pigpio.pi()
if not pi.connected:
    raise SystemExit("pigpiod não está a correr: sudo systemctl start pigpiod")

# Tacómetro 
pi.set_mode(TACH_PIN, pigpio.INPUT)
pi.set_pull_up_down(TACH_PIN, pigpio.PUD_UP)
pi.set_glitch_filter(TACH_PIN, 100)  # ignora ruído < 100 us
cb = pi.callback(TACH_PIN, pigpio.FALLING_EDGE, contar_pulso)


def definir_velocidade(percentagem):
    # percentagem: 0 a 100 (0 = ventoinha parada)
    percentagem = max(0, min(100, percentagem))
    pi.hardware_PWM(PWM_PIN, PWM_FREQ, int(percentagem * 10000))


def ler_rpm():
    # Devolve os RPM desde a última vez que esta função foi chamada
    global pulsos, tempo_anterior
    agora = time.time()
    segundos = agora - tempo_anterior
    if segundos <= 0:
        return 0.0
    rpm = (pulsos / PULSES_PER_REV) * 60 / segundos
    pulsos = 0
    tempo_anterior = agora
    return rpm


def parar():
    pi.hardware_PWM(PWM_PIN, PWM_FREQ, 0)
    cb.cancel()
    pi.stop()

if __name__ == "__main__":
    
    duty=0   
    definir_velocidade(duty)
    time.sleep(10)                  # espera estabilizar
    ler_rpm()                      # limpa o contador
    time.sleep(1)
    rpm = ler_rpm()
    print(f"PWM: {duty:3d}%  |  RPM lidos: {rpm:.0f}")
    
