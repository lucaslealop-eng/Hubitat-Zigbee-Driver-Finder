# Hubitat Zigbee Driver Finder

> 🔍 Identifique seu dispositivo Zigbee e descubra qual driver usar na Hubitat — sem pesquisar em fóruns.

## O que é?
O **Zigbee Driver Finder** é um SmartApp para o [Hubitat Elevation](https://hubitat.com/) que analisa as "impressões digitais" (manufacturer, model e clusters) de qualquer dispositivo Zigbee pareado no hub e recomenda automaticamente o driver correto para ele.

## Como funciona?
1. Instale o App no seu hub Hubitat.
2. Abra o App e selecione um dispositivo Zigbee no dropdown.
3. O App mostrará as informações técnicas do dispositivo (Manufacturer, Model, Clusters).
4. O App consulta um banco de dados JSON remoto e exibe a recomendação:
   - ✅ **Match Exato** — Driver encontrado com nome, autor e link.
   - ⚠️ **Match Parcial** — Mesmo fabricante, modelos parecidos.
   - 🔎 **Análise por Clusters** — Sugestão inteligente baseada nos clusters.
   - 📡 **Modo Offline** — Se a internet falhar, analisa localmente.

## Instalação
### Via Código (Manual)
1. No hub Hubitat, vá em **Apps Code** → **New App**.
2. Cole o conteúdo do arquivo `src/ZigbeeDriverFinder.groovy`.
3. Clique **Save**.
4. Vá em **Apps** → **Add User App** → selecione **Zigbee Driver Finder**.

## Estrutura do Projeto
```
Zigbee_Driver_Finder/
├── src/
│   └── ZigbeeDriverFinder.groovy   ← O SmartApp principal
├── data/
│   └── zigbee_driver_db.json       ← Banco de dados de dispositivos
└── README.md
```

## Contribuindo com Novos Dispositivos
O banco de dados (`zigbee_driver_db.json`) aceita entradas com a seguinte estrutura:
```json
{
  "manufacturer": "_TZ3000_xxx",
  "model": "TS0201",
  "device_type": "Temperature Sensor",
  "suggested_driver": "Driver Name Here",
  "author": "Author Name",
  "hpm_available": true,
  "url": "https://community.hubitat.com/..."
}
```

## Licença
MIT License — Use, modifique e distribua livremente.
