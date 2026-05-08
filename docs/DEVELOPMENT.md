# Desenvolvimento — Hubitat Driver Finder

> Documentação técnica para desenvolvedores e mantenedores do projeto.

---

## Estrutura do projeto

```text
Hubitat_Driver_Finder/
├── src/
│   └── HubitatDriverFinder.groovy
├── data/
│   ├── zigbee_driver_db.json
│   ├── db_overrides.json
│   ├── db_company_devices.json
│   ├── db_zwave_devices.json
│   ├── db_zwave_hpm_scraped.json
│   ├── db_zigbee2mqtt_devices.json
│   ├── db_zwavejs_devices.json
│   ├── db_tuya.json
│   ├── db_xiaomi_aqara.json
│   ├── db_brands.json
│   ├── db_other_brands.json
│   ├── db_misc_zigbee.json
│   └── db_hpm_scraped.json
├── docs/
│   ├── ABOUT.md
│   └── DEVELOPMENT.md
├── scripts/
│   ├── hpm_scraper.js
│   ├── import_company_sheet.js
│   ├── report_conflicts.js
│   └── validate_db.js
├── .github/
│   └── workflows/
├── package.json
└── README.md
```

---

## Comandos

```bash
npm run validate-db
npm run report-conflicts
npm run import-company-sheet
npm run import-public-sources
npm run update-index
npm run scrape
```

| Comando | Descrição |
|---|---|
| `validate-db` | Valida JSONs, campos obrigatórios, duplicatas e conflitos |
| `report-conflicts` | Lista fingerprints duplicadas com recomendações conflitantes |
| `import-company-sheet` | Atualiza `db_company_devices.json` a partir do Google Sheets |
| `import-public-sources` | Atualiza bases Zigbee2MQTT e Z-Wave JS |
| `update-index` | Recalcula totais em `zigbee_driver_db.json` |
| `scrape` | Atualiza `db_hpm_scraped.json` e `db_zwave_hpm_scraped.json` via HPM |

---

## Como o scoring funciona

O app calcula um score de probabilidade para cada candidato. Prioridade de fontes:

```text
overrides manuais > planilha da empresa > base Z-Wave curada > bases Zigbee curadas > HPM > fontes públicas
```

O score considera:
- Fingerprint exata (+100) ou match parcial (+45)
- Fonte do dado (override +120, empresa +80, Z-Wave curada +70, HPM +25, pública +5)
- Driver específico vs genérico (+10 / -4)
- Disponibilidade no HPM (+8)
- Compatibilidade de clusters Zigbee (+10 a +20 por hit)
- Driver já em uso (+15)

---

## Formato das entradas

### Zigbee

```json
{
  "protocol": "zigbee",
  "manufacturer": "_TZ3000_xxx",
  "model": "TS0201",
  "device_type": "Temperature Sensor",
  "device_class": "temperature",
  "suggested_driver": "Driver Name Here",
  "author": "Author Name",
  "hpm_available": true,
  "url": "https://community.hubitat.com/...",
  "driver_scope": "specific"
}
```

### Z-Wave

```json
{
  "protocol": "zwave",
  "manufacturer_id": "0086",
  "product_type_id": "0002",
  "product_id": "0064",
  "device_type": "Multisensor",
  "device_class": "motion",
  "suggested_driver": "Generic Z-Wave Plus Motion Sensor",
  "author": "Hubitat Built-in",
  "hpm_available": false,
  "url": "",
  "driver_scope": "generic"
}
```

---

## Changelog

### v2.5.0
- Sistema de classificação com 4 estados: Ideal, Compatível, Sugestão e Não encontrado.
- Matching flexível de nomes de driver (ignora sufixos como `(dev)`, `(beta)`, `v2`).
- Alternativas completas com badge HPM/Built-in e link do autor.
- Indicador `+N outros` na tabela de scan completo.
- Página de estatísticas com 4 categorias.

### v2.4.0
- Rename do arquivo para HubitatDriverFinder.groovy.
- Indicação HPM disponível + link do driver/autor em todas as telas.

### v2.3.0
- Motor de score para afunilar fingerprints duplicadas e conflitantes.
- Banco `db_overrides.json` para decisões manuais.
- Suporte a dispositivos Z-Wave por fingerprint de IDs.
- Fallback genérico para tipos Z-Wave.
- Scraper HPM com fingerprints Zigbee e Z-Wave.
- Importador de fontes públicas Zigbee2MQTT e Z-Wave JS.
- Base de dispositivos da empresa com prioridade alta.
- Scripts de validação e relatório de conflitos.
