/**
 * Copyright (c) 2026 Lucas (Hubitat Agent Project). All rights reserved.
 * This software is proprietary. See LICENSE file for details.
 */

const fs = require('fs');
const path = require('path');

const dataDir = path.join(__dirname, '..', 'data');
const indexPath = path.join(dataDir, 'zigbee_driver_db.json');
const index = JSON.parse(fs.readFileSync(indexPath, 'utf8'));

const sourceLabels = {
  'db_overrides.json': 'manual conflict overrides, absolute priority',
  'db_company_devices.json': 'Company Google Sheets, highest priority',
  'db_zwave_devices.json': 'curated Z-Wave fingerprints',
  'db_zwave_hpm_scraped.json': 'Hubitat Package Manager Extracted Z-Wave Database',
  'db_tuya.json': 'Tuya/Moes/Zemismart',
  'db_xiaomi_aqara.json': 'Xiaomi/Aqara/LUMI',
  'db_brands.json': 'IKEA/Philips/SONOFF/Third Reality',
  'db_other_brands.json': 'Sengled/OSRAM/Innr/GLEDOPTO/CentraLite/Yale/etc',
  'db_misc_zigbee.json': 'Inovelli/SmartThings/Bosch/Securifi/Leviton/Jasco/etc',
  'db_hpm_scraped.json': 'Hubitat Package Manager Extracted Zigbee Database',
  'db_zigbee2mqtt_devices.json': 'Zigbee2MQTT public identification database',
  'db_zwavejs_devices.json': 'Z-Wave JS public identification database'
};

const sourceOrder = [
  'db_overrides.json',
  'db_company_devices.json',
  'db_zwave_devices.json',
  'db_zwave_hpm_scraped.json',
  'db_tuya.json',
  'db_xiaomi_aqara.json',
  'db_brands.json',
  'db_other_brands.json',
  'db_misc_zigbee.json',
  'db_hpm_scraped.json',
  'db_zigbee2mqtt_devices.json',
  'db_zwavejs_devices.json'
];

let total = 0;
const sources = [];

for (const file of sourceOrder) {
  const fullPath = path.join(dataDir, file);
  if (!fs.existsSync(fullPath)) continue;
  const db = JSON.parse(fs.readFileSync(fullPath, 'utf8'));
  const count = Array.isArray(db.devices) ? db.devices.length : 0;
  total += count;
  sources.push(`${file} (${count} devices - ${sourceLabels[file] || 'Device database'})`);
}

index.version = '2.3.0';
index.last_updated = new Date().toISOString().slice(0, 10);
index.total_devices = total;
index.sources = sources;

fs.writeFileSync(indexPath, `${JSON.stringify(index, null, 2)}\n`);
console.log(`Updated index: ${total} devices across ${sources.length} sources.`);
