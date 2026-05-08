/**
 * Copyright (c) 2026 Lucas (Hubitat Agent Project). All rights reserved.
 * This software is proprietary. See LICENSE file for details.
 */

const fs = require('fs');
const path = require('path');

const dataDir = path.join(__dirname, '..', 'data');
const priority = {
  'db_overrides.json': 1,
  'db_company_devices.json': 5,
  'db_zwave_devices.json': 8,
  'db_zwave_hpm_scraped.json': 100,
  'db_tuya.json': 10,
  'db_xiaomi_aqara.json': 20,
  'db_brands.json': 30,
  'db_other_brands.json': 40,
  'db_misc_zigbee.json': 50,
  'db_hpm_scraped.json': 100,
  'db_zigbee2mqtt_devices.json': 200,
  'db_zwavejs_devices.json': 200
};

const devices = [];
for (const file of fs.readdirSync(dataDir).filter((name) => name.startsWith('db_') && name.endsWith('.json'))) {
  const db = JSON.parse(fs.readFileSync(path.join(dataDir, file), 'utf8'));
  for (const device of db.devices || []) {
    devices.push({ ...device, _file: file, _priority: priority[file] ?? 999 });
  }
}

const groups = new Map();
for (const device of devices) {
  const protocol = String(device.protocol || 'zigbee').toLowerCase();
  const key = protocol === 'zwave'
    ? `zwave|${String(device.manufacturer_id).toLowerCase()}|${String(device.product_type_id).toLowerCase()}|${String(device.product_id).toLowerCase()}`
    : `zigbee|${String(device.manufacturer).toLowerCase()}|${String(device.model).toLowerCase()}`;
  if (!groups.has(key)) groups.set(key, []);
  groups.get(key).push(device);
}

const conflicts = [...groups.values()]
  .filter((group) => group.length > 1 && new Set(group.map((device) => device.suggested_driver)).size > 1)
  .map((group) => group.sort((a, b) => a._priority - b._priority));

const categories = {
  company_vs_other: [],
  curated_vs_curated: [],
  curated_vs_hpm: [],
  public_vs_known: [],
  hpm_only: [],
  other: []
};

for (const group of conflicts) {
  const files = new Set(group.map((device) => device._file));
  const nonHpm = group.filter((device) => device._file !== 'db_hpm_scraped.json');
  const hasPublic = files.has('db_zigbee2mqtt_devices.json') || files.has('db_zwavejs_devices.json');

  if (files.has('db_company_devices.json')) categories.company_vs_other.push(group);
  else if (files.has('db_overrides.json')) categories.company_vs_other.push(group);
  else if (hasPublic) categories.public_vs_known.push(group);
  else if (nonHpm.length >= 2) categories.curated_vs_curated.push(group);
  else if (files.has('db_hpm_scraped.json') && nonHpm.length === 1) categories.curated_vs_hpm.push(group);
  else if (files.size === 1 && files.has('db_hpm_scraped.json')) categories.hpm_only.push(group);
  else categories.other.push(group);
}

console.log(`Total conflicts: ${conflicts.length}`);
for (const [name, entries] of Object.entries(categories)) {
  console.log(`${name}: ${entries.length}`);
}

for (const [name, entries] of Object.entries(categories)) {
  if (entries.length === 0) continue;
  console.log(`\n## ${name}`);
  for (const group of entries) {
    const chosen = group[0];
    console.log(`\n${chosen.manufacturer} / ${chosen.model}`);
    console.log(`chosen: [${chosen._file}] ${chosen.suggested_driver}`);
    for (const device of group) {
      console.log(`- [${device._file}] ${device.suggested_driver} | ${device.device_type} | ${device.author}`);
    }
  }
}
