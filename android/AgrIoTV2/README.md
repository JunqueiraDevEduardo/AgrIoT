# AgrIoT v2 (app Android + servidor Flask)

Novidades face à v1 (segundo o relatório R2):
- Nova aba **Histerese** na barra inferior, ao lado do PID, com simulação do aquecimento por histerese
  (relé liga em T < Tset − h, desliga em T ≥ Tset; sliders de Setpoint e h; PAUSAR/REPOR).
- **Atuadores**: novo cartão Histerese (on/off). Com o PID ligado a ventoinha passa a automática (e mostra duty % e RPM);
  com a histerese ligada o aquecedor passa a automático.
- **PID corrigido**: e = T − Tset, a ventoinha só atua quando T > Tset (só arrefece), fórmula do relatório (Kp, Ki·Ts·Σe, Kd·Δe/Ts).
  O setpoint é partilhado entre PID e histerese.
- Sensores: legenda BME280 (temperatura, humidade, pressão) e ADS1115 (solo capacitivo).
- Servidor: rotas /api/histerese, estado safe no arranque, parâmetros guardados na BD, relé e ventoinha nunca em simultâneo.
  Mapa de hardware (BME280 0x76, ADS1115 0x48, OLED 0x3C, PWM GPIO12, tacómetro GPIO13, relé, LED) na classe Hardware.

Abrir a app: pasta em ~/AndroidStudioProjects/AgrIoT > Android Studio > Open > jbr-21 > Sync > Run.
Modo demonstração: ADM-001 / admin123 (ou CLI-001 / 1234). Servidor: pressão longa no logótipo do login.
Servidor: cd server && pip3 install -r requirements.txt && python3 app.py
