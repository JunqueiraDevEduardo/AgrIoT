# ==================================================
# Link BME280 Datasheet:
# https://www.bosch-sensortec.com/media/boschsensortec/downloads/datasheets/bst-bme280-ds002.pdf
# ==================================================

from smbus2 import SMBus
import struct
import time

# I2C bus
i2cbus = SMBus(1)

# BME280 Address
BME280_ADDRESS = 0x76
BME280_START_ADDRESS = 0x88
BME280_CALIB_HUM_START = 0xE1

# BME280 Globals - Coeficientes de Calibração (H1 a H6, T1 a T3, P1 a P9)
dig_T1, dig_T2, dig_T3 = 0, 0, 0
dig_P1, dig_P2, dig_P3, dig_P4, dig_P5, dig_P6, dig_P7, dig_P8, dig_P9 = 0, 0, 0, 0, 0, 0, 0, 0, 0
dig_H1, dig_H2, dig_H3, dig_H4, dig_H5, dig_H6 = 0, 0, 0, 0, 0, 0
t_fine = 0

'''
Global variables in Python are defined outside of any function and can be 
accessed from anywhere in the code, including inside functions. 
To modify a global variable within a function, you need to use the global keyword.
'''

def init_bme280():
    # 1. Configurar oversampling de humidade em 0xF2 (x4) 
    i2cbus.write_byte_data(BME280_ADDRESS, 0xF2, 0b00000011)
    
    # 2. Configurar modo normal, oversampling de temperatura x1 e pressão x4 em 0xF4
    i2cbus.write_byte_data(BME280_ADDRESS, 0xF4, 0b00101111)
    
    time.sleep(0.2)
    read_and_unpack_calibration_data_bme280()


def read_and_unpack_calibration_data_bme280():
    global dig_T1, dig_T2, dig_T3, dig_P1, dig_P2, dig_P3, dig_P4, dig_P5, dig_P6, dig_P7, dig_P8, dig_P9
    global dig_H1, dig_H2, dig_H3, dig_H4, dig_H5, dig_H6
    
    # Leitura do bloco principal de calibração (Temperatura e Pressão de 0x88 a 0xA1)
    raw_data = i2cbus.read_i2c_block_data(BME280_ADDRESS, BME280_START_ADDRESS, 24)
    dig_T1 = struct.unpack('<H', bytes(raw_data[0:2]))[0]
    dig_T2 = struct.unpack('<h', bytes(raw_data[2:4]))[0]
    dig_T3 = struct.unpack('<h', bytes(raw_data[4:6]))[0]
    
    dig_P1 = struct.unpack('<H', bytes(raw_data[6:8]))[0]
    dig_P2 = struct.unpack('<h', bytes(raw_data[8:10]))[0]
    dig_P3 = struct.unpack('<h', bytes(raw_data[10:12]))[0]
    dig_P4 = struct.unpack('<h', bytes(raw_data[12:14]))[0]
    dig_P5 = struct.unpack('<h', bytes(raw_data[14:16]))[0]
    dig_P6 = struct.unpack('<h', bytes(raw_data[16:18]))[0]
    dig_P7 = struct.unpack('<h', bytes(raw_data[18:20]))[0]
    dig_P8 = struct.unpack('<h', bytes(raw_data[20:22]))[0]
    dig_P9 = struct.unpack('<h', bytes(raw_data[22:24]))[0]

    # Leitura de dig_H1 isolado no endereço 0xA1
    dig_H1 = i2cbus.read_byte_data(BME280_ADDRESS, 0xA1)
    
    # Leitura do bloco de calibração de humidade a partir de 0xE1 (7 bytes)
    h_data = i2cbus.read_i2c_block_data(BME280_ADDRESS, BME280_CALIB_HUM_START, 7)
    
    dig_H2 = struct.unpack('<h', bytes(h_data[0:2]))[0]
    dig_H3 = h_data[2]
    
    h4_msb = struct.unpack('<b', bytes([h_data[3]]))[0]
    h4_lsb = h_data[4] & 0x0F
    dig_H4 = (int(h4_msb) * 16) | int(h4_lsb)

    h5_msb = struct.unpack('<b', bytes([h_data[5]]))[0]
    h5_lsb = h_data[4] >> 4
    dig_H5 = (int(h5_msb) * 16) | int(h5_lsb)

    dig_H6 = struct.unpack('<b', bytes([h_data[6]]))[0]


def read_temperature(adc_T):
    global t_fine
    var1 = ((adc_T / 16384.0) - (dig_T1 / 1024.0)) * dig_T2
    var2 = (((adc_T / 131072.0) - (dig_T1 / 8192.0)) * ((adc_T / 131072.0) - (dig_T1 / 8192.0))) * dig_T3
    t_fine = var1 + var2
    return (var1 + var2) / 5120.0  # Temperatura em °C


def read_pressure(adc_P):
    var1 = (t_fine / 2.0) - 64000.0
    var2 = var1 * var1 * dig_P6 / 32768.0
    var2 = var2 + var1 * dig_P5 * 2.0
    var2 = (var2 / 4.0) + (dig_P4 * 65536.0)
    var1 = ((dig_P3 * var1 * var1 / 524288.0) + (dig_P2 * var1)) / 524288.0
    var1 = (1.0 + var1 / 32768.0) * dig_P1
    
    if var1 == 0.0:
        return 0.0  # Evitar divisão por zero
        
    p = 1048576.0 - adc_P
    p = (p - (var2 / 4096.0)) * 6250.0 / var1
    var1 = dig_P9 * p * p / 2147483648.0
    var2 = p * dig_P8 / 32768.0
    
    return (p + (var1 + var2 + dig_P7) / 16.0) / 100  # Pressão em hPa


def read_humidity(adc_H):
    var_h = t_fine - 76800.0
    
    part1 = adc_H - (dig_H4 * 64.0 + (dig_H5 / 16384.0) * var_h)
    part2 = dig_H2 / 65536.0
    part3 = 1.0 + (dig_H3 / 67108864.0) * var_h
    part4 = 1.0 + (dig_H6 / 67108864.0) * var_h * part3
    
    var_h = part1 * (part2 * part3 * part4)
    var_h = var_h * (1.0 - (dig_H1 * var_h) / 524288.0)
    
    if var_h > 100.0:
        var_h = 100.0
    elif var_h < 0.0:
        var_h = 0.0
        
    return var_h  # Humidade relativa em %RH


def main():
    init_bme280()

    while True:
        time.sleep(2)

        raw_sensor_data = i2cbus.read_i2c_block_data(BME280_ADDRESS, 0xF7, 8)
        
        adc_P = (raw_sensor_data[0] << 12) | (raw_sensor_data[1] << 4) | (raw_sensor_data[2] >> 4)
        adc_T = (raw_sensor_data[3] << 12) | (raw_sensor_data[4] << 4) | (raw_sensor_data[5] >> 4)
        adc_H = (raw_sensor_data[6] << 8) | raw_sensor_data[7]

        temp = read_temperature(adc_T)
        press = read_pressure(adc_P)
        hum = read_humidity(adc_H)

        print(f"BME280 -> T: {temp:.2f}°C, P: {press:.2f} hPa, H: {hum:.2f}%")


if __name__ == "__main__":
    main()
