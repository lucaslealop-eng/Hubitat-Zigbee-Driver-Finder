/**
 * Copyright (c) 2026 Lucas (Hubitat Agent Project). All rights reserved.
 * This software is proprietary. See LICENSE file for details.
 */

const fs = require('fs');
const path = require('path');

const dataDir = path.join(__dirname, '..', 'data');
const dbFiles = fs.readdirSync(dataDir)
  .filter((file) => file.startsWith('db_') && file.endsWith('.json'));

let hasError = false;
const allDevices = [];

for (const file of dbFiles) {
  const fullPath = path.join(dataDir, file);
  let parsed;

  try {
    parsed = JSON.parse(fs.readFileSync(fullPath, 'utf8'));
  } catch (error) {
    console.error(`[ERROR] ${file}: invalid JSON - ${error.message}`);
    hasError = true;
    continue;
  }

  if (!Array.isArray(parsed.devices)) {
    console.error(`[ERROR] ${file}: missing devices array`);
    hasError = true;
    continue;
  }

  parsed.devices.forEach((device, index) => {
    const protocol = String(device.protocol || 'zigbee').toLowerCase();
    const requiredFingerprint = protocol === 'zwave'
      ? ['manufacturer_id', 'product_type_id', 'product_id']
      : ['manufacturer', 'model'];
    const missing = [...requiredFingerprint, 'device_type', 'suggested_driver', 'author', 'hpm_available']
      .filter((field) => device[field] === undefined || device[field] === null || device[field] === '');

    if (missing.length > 0) {
      console.error(`[ERROR] ${file}#${index}: missing ${missing.join(', ')} (${device.manufacturer || '?'} / ${device.model || '?'})`);
      hasError = true;
    }

    allDevices.push({ ...device, _file: file });
  });
}

const byFingerprint = new Map();
for (const device of allDevices) {
  const protocol = String(device.protocol || 'zigbee').toLowerCase();
  const key = protocol === 'zwave'
    ? `zwave|${String(device.manufacturer_id).toLowerCase()}|${String(device.product_type_id).toLowerCase()}|${String(device.product_id).toLowerCase()}`
    : `zigbee|${String(device.manufacturer).toLowerCase()}|${String(device.model).toLowerCase()}`;
  if (!byFingerprint.has(key)) byFingerprint.set(key, []);
  byFingerprint.get(key).push(device);
}

const duplicateGroups = [...byFingerprint.values()].filter((group) => group.length > 1);
const conflictGroups = duplicateGroups.filter((group) => new Set(group.map((device) => device.suggested_driver)).size > 1);

console.log(`Validated ${dbFiles.length} database files.`);
console.log(`Devices: ${allDevices.length}`);
console.log(`Unique fingerprints: ${byFingerprint.size}`);
console.log(`Duplicate fingerprints: ${duplicateGroups.length}`);
console.log(`Conflicting recommendations: ${conflictGroups.length}`);

if (conflictGroups.length > 0) {
  console.warn('[WARN] Conflicts are allowed because the app uses overrides, source priority, and scoring to choose the most likely driver.');
}

process.exit(hasError ? 1 : 0);
