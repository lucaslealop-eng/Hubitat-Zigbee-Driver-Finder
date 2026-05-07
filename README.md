# Hubitat Driver Finder 2.3.0

Identifique dispositivos Zigbee e Z-Wave pareados no Hubitat e veja qual driver usar sem pesquisar manualmente em foruns.

## O que e?

O Hubitat Driver Finder e um SmartApp para Hubitat Elevation. Ele le as informacoes tecnicas do dispositivo, consulta bancos JSON remotos e mostra a recomendacao de driver mais provavel.

Para Zigbee, o app usa `manufacturer`, `model`, `inClusters` e `outClusters`.

Para Z-Wave, o app usa a fingerprint por IDs: `manufacturer_id`, `product_type_id` e `product_id`. No Hubitat esses dados podem aparecer como `MSR` ou como campos decimais `Manufacturer`, `Device Type` e `Device Id`; o app normaliza esses formatos automaticamente. Se ainda nao houver uma fingerprint exata no banco, ele tenta uma sugestao generica baseada no tipo aparente do dispositivo.

## Como funciona?

1. Instale o app no Hubitat.
2. Selecione os dispositivos do hub aos quais o app tera acesso.
3. Escolha um dispositivo ou rode a analise completa.
4. O app baixa o banco de dados remoto e compara os dados do dispositivo.
5. O resultado pode ser:
   - Override manual: decisao curada para resolver conflito conhecido.
   - Match exato: fingerprint encontrada no banco.
   - Match parcial: mesmo fabricante, mas outro modelo.
   - Recomendacao por fabricante: regra de fallback Zigbee.
   - Analise por clusters: sugestao Zigbee baseada em clusters.
   - Sugestao Z-Wave generica: quando nao existe fingerprint exata, mas o tipo aparente e reconhecivel.
   - Sem sugestao: dispositivo ainda nao mapeado.

## Como o app escolhe em conflitos?

O app nao usa mais apenas a primeira entrada encontrada. A versao 2.3 calcula um score de probabilidade.

Prioridade de fontes:

```text
overrides manuais > planilha da empresa > base Z-Wave curada > bases Zigbee curadas > HPM
```

O score considera:

- fingerprint exata;
- fonte do dado;
- driver especifico versus generico;
- disponibilidade no HPM;
- compatibilidade de clusters Zigbee com o tipo do dispositivo;
- driver atual ja em uso;
- alternativas conflitantes.

Para usuarios leigos, a tela mostra um driver recomendado principal, o motivo da escolha e alternativas se existirem.

## Sobre o timer de 24h

Quando o app mostra que o cache expira em algumas horas, isso significa que ele guardou temporariamente a lista de drivers que acabou de baixar.

Para usuarios leigos: pense nesse timer como uma validade da lista. Durante 24 horas, o app reutiliza a lista ja baixada para ficar mais rapido e evitar downloads repetidos. Depois que o tempo acaba, ele baixa uma lista nova na proxima consulta.

Importante: esta versao nao tem modo offline. Se o hub nao conseguir acessar o banco remoto e nao houver cache valido carregado na memoria, o app mostra uma mensagem de erro pedindo para verificar a conexao.

## Melhorias da versao 2.3

- Motor de score para afunilar fingerprints duplicadas e conflitantes.
- Banco `db_overrides.json` para decisoes manuais em conflitos conhecidos.
- Suporte inicial a dispositivos Z-Wave por fingerprint de IDs.
- Fallback generico para alguns tipos Z-Wave quando ainda nao existe fingerprint exata.
- Botao no app para limpar o cache de 24h e baixar a database novamente.
- Scraper HPM agora coleta fingerprints Zigbee e Z-Wave.
- Deteccao Z-Wave corrigida para dispositivos que mostram command classes em `In Clusters`.
- Importador de fontes publicas Zigbee2MQTT e Z-Wave JS com prioridade baixa e drivers genericos inferidos.
- Base de dispositivos proprios da empresa importada do Google Sheets com prioridade alta.
- Prioridade explicita da base curada sobre dados raspados do HPM.
- Correcao da regra Tuya `_TZE`, que tem prioridade sobre `_TZ`.
- Normalizacao de clusters em formatos como `0006`, `0x0006` e listas com espacos.
- Escape de HTML nos dados exibidos na interface.
- Scripts de validacao e relatorio de conflitos.

## Instalacao manual

1. No Hubitat, acesse Apps Code.
2. Clique em New App.
3. Cole o conteudo de `src/ZigbeeDriverFinder.groovy`.
4. Clique em Save.
5. Acesse Apps, depois Add User App.
6. Selecione Hubitat Driver Finder.

## Estrutura do projeto

```text
Zigbee_Driver_Finder/
├── src/
│   └── ZigbeeDriverFinder.groovy
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
├── scripts/
│   ├── hpm_scraper.js
│   ├── import_company_sheet.js
│   ├── report_conflicts.js
│   └── validate_db.js
├── package.json
└── README.md
```

## Comandos

```bash
npm run validate-db
npm run report-conflicts
npm run import-company-sheet
npm run import-public-sources
npm run update-index
npm run scrape
```

`npm run validate-db` valida os JSONs locais, campos obrigatorios, duplicatas e conflitos de recomendacao.

`npm run report-conflicts` lista fingerprints duplicadas com recomendacoes conflitantes, mostrando qual driver vence pela prioridade atual.

`npm run import-company-sheet` atualiza `data/db_company_devices.json` a partir da planilha de dispositivos proprios da empresa. Essa base tem prioridade sobre as bases publicas.

`npm run import-public-sources` atualiza `data/db_zigbee2mqtt_devices.json` e `data/db_zwavejs_devices.json` a partir de fontes publicas. Essas bases ajudam a identificar modelos e sugerem apenas drivers genericos inferidos.

`npm run update-index` recalcula o total de dispositivos e as fontes em `data/zigbee_driver_db.json`.

`npm run scrape` atualiza `data/db_hpm_scraped.json` e `data/db_zwave_hpm_scraped.json` a partir dos repositorios do Hubitat Package Manager.

## Entradas Zigbee

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

## Entradas Z-Wave

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

## Licenca

MIT License.
