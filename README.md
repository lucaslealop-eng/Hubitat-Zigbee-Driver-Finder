# Hubitat Zigbee Driver Finder 2.0

Identifique dispositivos Zigbee pareados no Hubitat e veja qual driver usar sem pesquisar manualmente em foruns.

## O que e?

O Zigbee Driver Finder e um SmartApp para Hubitat Elevation. Ele le as informacoes Zigbee do dispositivo, como manufacturer, model, inClusters e outClusters, consulta um banco JSON remoto e mostra a recomendacao de driver mais provavel.

## Como funciona?

1. Instale o app no Hubitat.
2. Selecione os dispositivos do hub aos quais o app tera acesso.
3. Escolha um dispositivo Zigbee ou rode a analise completa.
4. O app baixa o banco de dados remoto e compara os dados do dispositivo.
5. O resultado pode ser:
   - Match exato: manufacturer e model encontrados no banco.
   - Match parcial: mesmo fabricante, mas outro modelo.
   - Recomendacao por fabricante: regra de fallback, como Tuya, Aqara ou IKEA.
   - Analise por clusters: sugestao baseada nos clusters Zigbee.
   - Sem sugestao: dispositivo ainda nao mapeado.

## Sobre o timer de 24h

Quando o app mostra que o cache expira em algumas horas, isso significa que ele guardou temporariamente a lista de drivers que acabou de baixar.

Para usuarios leigos: pense nesse timer como uma validade da lista. Durante 24 horas, o app reutiliza a lista ja baixada para ficar mais rapido e evitar downloads repetidos. Depois que o tempo acaba, ele baixa uma lista nova na proxima consulta.

Importante: esta versao nao tem modo offline. Se o hub nao conseguir acessar o banco remoto e nao houver cache valido carregado na memoria, o app mostra uma mensagem de erro pedindo para verificar a conexao.

## Melhorias da versao 2.0

- Prioridade explicita da base curada sobre dados raspados do HPM.
- Base de dispositivos proprios da empresa importada do Google Sheets com prioridade maxima.
- Correcao da regra Tuya `_TZE`, que agora tem prioridade sobre `_TZ`.
- Normalizacao de clusters em formatos como `0006`, `0x0006` e listas com espacos.
- Uso de `inClusters` e `outClusters` na analise por clusters.
- Escape de HTML nos dados exibidos na interface.
- Remocao da promessa de modo offline.
- Explicacao do timer de cache de 24h dentro do app e na documentacao.
- Script de validacao do banco de dados.
- Scraper com contadores de falha e protecao contra coletas anormalmente pequenas.

## Instalacao manual

1. No Hubitat, acesse Apps Code.
2. Clique em New App.
3. Cole o conteudo de `src/ZigbeeDriverFinder.groovy`.
4. Clique em Save.
5. Acesse Apps, depois Add User App.
6. Selecione Zigbee Driver Finder.

## Estrutura do projeto

```text
Zigbee_Driver_Finder_2.0/
├── src/
│   └── ZigbeeDriverFinder.groovy
├── data/
│   ├── zigbee_driver_db.json
│   ├── db_company_devices.json
│   ├── db_tuya.json
│   ├── db_xiaomi_aqara.json
│   ├── db_brands.json
│   ├── db_other_brands.json
│   ├── db_misc_zigbee.json
│   └── db_hpm_scraped.json
├── scripts/
│   ├── hpm_scraper.js
│   └── validate_db.js
├── package.json
└── README.md
```

## Comandos

```bash
npm run validate-db
npm run report-conflicts
npm run import-company-sheet
npm run scrape
```

`npm run validate-db` valida os JSONs locais, campos obrigatorios, duplicatas e conflitos de recomendacao.

`npm run report-conflicts` lista fingerprints duplicadas com recomendacoes conflitantes, mostrando qual driver vence pela prioridade atual.

`npm run import-company-sheet` atualiza `data/db_company_devices.json` a partir da planilha de dispositivos proprios da empresa. Essa base tem prioridade sobre todas as outras.

`npm run scrape` atualiza `data/db_hpm_scraped.json` a partir dos repositorios do Hubitat Package Manager.

## Contribuindo com novos dispositivos

Entradas de dispositivos usam este formato:

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

## Licenca

MIT License.
