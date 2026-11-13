# AgrIoT – Estufa Inteligente (app Android + servidor Flask)

## Abrir a app (macOS, M2)
1. Descompacta e coloca a pasta `AgrIoT` em `~/AndroidStudioProjects/` (fora de Desktop/Documents/iCloud, sem espaços).
2. Android Studio > File > Open > escolhe a pasta `AgrIoT`.
3. Settings > Build Tools > Gradle > Gradle JDK = **jbr-21** (o JDK que vem com o Android Studio).
4. Sync e Run (emulador ou telemóvel).

Se o Android Studio pedir o Gradle Wrapper, ele descarrega o Gradle 8.9 sozinho (precisa de Internet).

## Entrar
- Modo demonstração (por defeito, sem servidor): `ADM-001` / `admin123` (administrador) ou `CLI-001` / `1234` (utilizador).
- Servidor real: no ecrã de login, **pressão longa na zona verde "AgrIoT"** e escreve `http://100.x.y.z:5000`
  (emulador a falar com o Mac: `http://10.0.2.2:5000`). Vazio = demonstração.

## Servidor
```
cd server
pip3 install -r requirements.txt
python3 app.py
```
Mesmas credenciais iniciais. Os sensores/atuadores estão simulados em `Hardware` (ficheiro `server/app.py`): é aí que ligas o hardware real.

## Estrutura
- `app/.../LoginActivity` – iniciar sessão
- `MainActivity` – barra de navegação inferior (Início, Sensores, Atuadores, PID, Admin)
- `HomeFragment`, `SensoresFragment`, `AtuadoresFragment`, `PidFragment`, `AdminFragment`, `UserFormFragment`
- `Repository.kt` – cliente HTTP/JSON (GET/POST/PUT/DELETE) + modo demonstração
- Cores/estilos: `res/values/colors.xml` e `themes.xml`
- Fontes: usa `sans-serif-condensed` (no lugar da Barlow Condensed) e a fonte do sistema (no lugar da Inter). Para as fontes exatas, põe os .ttf em `res/font` e troca os `fontFamily`.
