# ==================================================
# Link KY053ADC (ADC 16BITS):
# https://www.joy-it.net/en/products/COM-KY053ADC
#
# Link datasheet:
# https://www.ti.com/lit/ds/symlink/ads1115.pdf
#
# Ligações Sensor Capacitivo v1.2 -> ADS1115 / Raspberry Pi:
#   VCC  -> 3.3V (recomendado, mantém o sinal seguro para o ADS1115)
#   GND  -> GND
#   AOUT -> A0 do ADS1115
# ==================================================

import time
import struct
from smbus2 import SMBus 

I2C_ADDRESS = 0x48
CONFIG_REGISTER = 0x01
CONVERSION_REGISTER = 0x00

i2cbus = SMBus(1)

# Configuração: Modo single-shot, A0 vs GND, ganho ±4.096V, 128 SPS
CONFIG = 0b1100001110000011

# Valores de calibração do sensor capacitivo v1.2 (ajusta com os teus valores reais!)
TENSAO_SECO = 2.80     # 0% humidade (sensor limpo ao ar)
TENSAO_MOLHADO = 1.25  # 100% humidade (sensor mergulhado em água até à linha branca)


def write_config_adc():
    config_high = (CONFIG >> 8) & 0xFF  # extrai o msb
    config_low = CONFIG & 0xFF          # extrai o lsb
    i2cbus.write_i2c_block_data(I2C_ADDRESS, CONFIG_REGISTER, [config_high, config_low])
    time.sleep(0.1)


def read_adc():
    write_config_adc()
    data = i2cbus.read_i2c_block_data(I2C_ADDRESS, CONVERSION_REGISTER, 2)
    raw_adc = struct.unpack('>h', bytes(data))[0]

    voltage = (raw_adc * 4.096) / 32767
    return voltage, raw_adc


def read_soil_moisture():
    voltage, raw_adc = read_adc()

    # Mapeamento inverso: tensão alta = 0%, tensão baixa = 100%
    humidade = (TENSAO_SECO - voltage) / (TENSAO_SECO - TENSAO_MOLHADO) * 100.0

    # Limita o resultado entre 0% e 100%
    if humidade > 100.0:
        humidade = 100.0
    elif humidade < 0.0:
        humidade = 0.0

    return humidade, voltage, raw_adc


if __name__ == "__main__":
    write_config_adc()
    while True:
        humidade, voltage, raw = read_soil_moisture()
        print(f"Humidade Solo: {humidade:5.1f}% | Tensão: {voltage:.4f} V | Raw: {raw}")
        time.sleep(2) 
