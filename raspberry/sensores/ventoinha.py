# ==================================================
# Biblioteca da ventoinha Sunon EF75070S1-C430-S9A
# Raspberry Pi 3B+ | pigpio | PWM 25 kHz + tacómetro
#
# Antes de usar:
#   sudo apt install pigpio python3-pigpio
#   sudo systemctl enable --now pigpiod
#
# Ligações:
#   Preto    -> GND (fonte externa + GND do Pi, massa comum)
#   Vermelho -> +V (fonte externa, NUNCA pelo Pi)
#   Amarelo  -> GPIO23 (pino 16) tacómetro
#   Azul     -> GPIO18 (pino 12) PWM
#
# CUIDADO: mede o fio do tacómetro com o multímetro antes de o
# ligar ao Pi. Se tiver 5V ou 12V, os GPIO queimam (aguentam 3.3V).
#
# Funções:
#   definir_velocidade(0-100)  -> muda o PWM em %
#   ler_rpm()                  -> devolve os RPM
#   parar()                    -> desliga tudo
# ==================================================

import time
import pigpio

PWM_PIN = 18           # GPIO18 (PWM por hardware)
TACH_PIN = 23          # Tacómetro
PWM_FREQ = 25000       # 25 kHz
PULSES_PER_REV = 2     # Confirmar no datasheet

pulsos = 0
tempo_anterior = time.time()


def contar_pulso(gpio, level, tick):
    # O pigpio chama esta função sozinho, sempre que o
    # tacómetro faz um pulso. Só soma 1 ao contador.
    global pulsos
    pulsos += 1


pi = pigpio.pi()

# Tacómetro com pull-up interno do Pi
pi.set_mode(TACH_PIN, pigpio.INPUT)
pi.set_pull_up_down(TACH_PIN, pigpio.PUD_UP)
pi.set_glitch_filter(TACH_PIN, 100)  # ignora ruído < 100 us
cb = pi.callback(TACH_PIN, pigpio.FALLING_EDGE, contar_pulso)


def definir_velocidade(percentagem):
    # percentagem: 0 a 100
    pi.hardware_PWM(PWM_PIN, PWM_FREQ, int(percentagem * 10000))


def ler_rpm():
    # Devolve os RPM desde a última vez que esta função foi chamada
    global pulsos, tempo_anterior
    agora = time.time()
    segundos = agora - tempo_anterior
    rpm = (pulsos / PULSES_PER_REV) * 60 / segundos
    pulsos = 0
    tempo_anterior = agora
    return rpm


def parar():
    pi.hardware_PWM(PWM_PIN, PWM_FREQ, 0)
    cb.cancel()
    pi.stop()


# ==================================================
# Teste: só corre se executares  python3 ventoinha.py
# ==================================================
if __name__ == "__main__":
    try:
        for duty in (20, 40, 60, 80, 100, 60, 30):
            definir_velocidade(duty)
            time.sleep(3)                  # espera estabilizar
            ler_rpm()                      # limpa o contador
            time.sleep(1)
            rpm = ler_rpm()
            print(f"PWM: {duty:3d}%  |  RPM lidos: {rpm:.0f}")
    finally:
        parar()
