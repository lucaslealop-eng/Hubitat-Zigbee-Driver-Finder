const fs = require('fs');
const path = require('path');

const dataDir = path.join(__dirname, '..', 'data');
const version = '2.3.0';

async function fetchJson(url) {
  const response = await fetch(url, { headers: { 'User-Agent': 'hubitat-driver-finder' } });
  if (!response.ok) throw new Error(`HTTP ${response.status} for ${url}`);
  return response.json();
}

async function fetchText(url) {
  const response = await fetch(url, { headers: { 'User-Agent': 'hubitat-driver-finder' } });
  if (!response.ok) throw new Error(`HTTP ${response.status} for ${url}`);
  return response.text();
}

function addUnique(collection, device, keyFields) {
  const key = keyFields.map((field) => String(device[field] || '').toLowerCase()).join('|');
  if (collection.some((item) => keyFields.map((field) => String(item[field] || '').toLowerCase()).join('|') === key)) return false;
  collection.push(device);
  return true;
}

function inferDeviceClass(text) {
  const value = String(text || '').toLowerCase();
  if (value.includes('thermostat') || value.includes('trv')) return 'thermostat';
  if (value.includes('lock')) return 'lock';
  if (value.includes('cover') || value.includes('curtain') || value.includes('shade') || value.includes('blind')) return 'shade';
  if (value.includes('rgb') || value.includes('color')) return 'color_light';
  if (value.includes('dimmer')) return 'dimmer';
  if (value.includes('light') || value.includes('bulb')) return 'light';
  if (value.includes('plug') || value.includes('outlet')) return 'outlet';
  if (value.includes('switch') || value.includes('relay')) return 'switch';
  if (value.includes('motion') || value.includes('occupancy') || value.includes('presence')) return 'motion';
  if (value.includes('contact') || value.includes('door') || value.includes('window')) return 'contact';
  if (value.includes('water') || value.includes('leak') || value.includes('moisture')) return 'water';
  if (value.includes('temperature') || value.includes('humidity')) return 'environment';
  if (value.includes('button') || value.includes('remote')) return 'button';
  return 'unknown';
}

function genericDriverFor(protocol, deviceClass) {
  if (protocol === 'zwave') {
    const map = {
      dimmer: 'Generic Z-Wave Plus Dimmer',
      light: 'Generic Z-Wave Plus Dimmer',
      switch: 'Generic Z-Wave Plus Switch',
      outlet: 'Generic Z-Wave Plus Switch',
      lock: 'Generic Z-Wave Lock',
      thermostat: 'Generic Z-Wave Thermostat',
      contact: 'Generic Z-Wave Contact Sensor',
      motion: 'Generic Z-Wave Motion Sensor',
      water: 'Generic Z-Wave Water Sensor'
    };
    return map[deviceClass] || 'Generic Z-Wave Device';
  }

  const map = {
    dimmer: 'Generic Zigbee Dimmer',
    light: 'Generic Zigbee Bulb',
    color_light: 'Generic Zigbee RGBW Light',
    switch: 'Generic Zigbee Switch',
    outlet: 'Generic Zigbee Outlet',
    lock: 'Generic Zigbee Lock',
    thermostat: 'Generic Zigbee Thermostat',
    shade: 'Generic Zigbee Window Shade',
    contact: 'Generic Zigbee Contact Sensor',
    motion: 'Generic Zigbee Motion Sensor',
    water: 'Generic Zigbee Moisture Sensor',
    environment: 'Generic Zigbee Temperature/Humidity Sensor',
    button: 'Generic Zigbee Button Controller'
  };
  return map[deviceClass] || 'Generic Zigbee Device';
}

function parseZigbee2MqttFingerprints(source) {
  const devices = [];
  const definitionBlocks = source.split(/\n\s*{\s*\n/g);

  for (const block of definitionBlocks) {
    if (!block.includes('fingerprint')) continue;
    const vendor = (block.match(/vendor:\s*['"`]([^'"`]+)['"`]/) || [])[1] || 'Zigbee2MQTT';
    const description = (block.match(/description:\s*['"`]([^'"`]+)['"`]/) || [])[1] || 'Zigbee device';
    const model = (block.match(/model:\s*['"`]([^'"`]+)['"`]/) || [])[1] || '';
    const deviceClass = inferDeviceClass(`${model} ${description}`);
    const suggested = genericDriverFor('zigbee', deviceClass);

    const fingerprintRegex = /\{\s*modelID:\s*['"`]([^'"`]+)['"`]\s*,\s*manufacturerName:\s*['"`]([^'"`]+)['"`][^}]*\}/g;
    let match;
    while ((match = fingerprintRegex.exec(block)) !== null) {
      addUnique(devices, {
        protocol: 'zigbee',
        manufacturer: match[2],
        model: match[1],
        device_type: description,
        device_class: deviceClass,
        suggested_driver: suggested,
        author: 'Zigbee2MQTT',
        hpm_available: false,
        url: 'https://www.zigbee2mqtt.io/supported-devices/',
        driver_scope: 'generic',
        source_note: 'Public identification source. Generic Hubitat driver inferred from device description.',
        public_model: model,
        public_vendor: vendor
      }, ['manufacturer', 'model']);
    }
  }

  return devices;
}

async function importZigbee2Mqtt() {
  const tree = await fetchJson('https://api.github.com/repos/Koenkk/zigbee-herdsman-converters/git/trees/master?recursive=1');
  const files = tree.tree
    .filter((item) => item.type === 'blob' && /^src\/devices\/.+\.ts$/.test(item.path))
    .map((item) => item.path);

  const devices = [];
  for (const file of files) {
    const source = await fetchText(`https://raw.githubusercontent.com/Koenkk/zigbee-herdsman-converters/master/${file}`);
    for (const device of parseZigbee2MqttFingerprints(source)) {
      addUnique(devices, device, ['manufacturer', 'model']);
    }
  }

  fs.writeFileSync(path.join(dataDir, 'db_zigbee2mqtt_devices.json'), `${JSON.stringify({
    version,
    source: 'Zigbee2MQTT public device database',
    devices
  }, null, 2)}\n`);
  console.log(`Imported ${devices.length} Zigbee2MQTT fingerprints.`);
}

function normalizeHex(value) {
  return String(value || '').replace(/^0x/i, '').toUpperCase().padStart(4, '0');
}

function stripJsonComments(text) {
  return text
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/^\s*\/\/.*$/gm, '');
}

async function importZwaveJs() {
  const tree = await fetchJson('https://api.github.com/repos/zwave-js/node-zwave-js/git/trees/master?recursive=1');
  const files = tree.tree
    .filter((item) => item.type === 'blob' && /^packages\/config\/config\/devices\/.+\.json$/.test(item.path))
    .map((item) => item.path);

  const devices = [];
  for (const file of files) {
    let json;
    try {
      const source = await fetchText(`https://raw.githubusercontent.com/zwave-js/node-zwave-js/master/${file}`);
      json = JSON.parse(stripJsonComments(source));
    } catch (error) {
      continue;
    }
    if (!json.manufacturerId || !Array.isArray(json.devices)) continue;

    for (const device of json.devices) {
      const productType = device.productType || device.productTypeId;
      const productId = device.productId;
      if (!productType || !productId) continue;
      const label = [json.label, device.label, device.description].filter(Boolean).join(' ');
      const deviceClass = inferDeviceClass(label);
      addUnique(devices, {
        protocol: 'zwave',
        manufacturer_id: normalizeHex(json.manufacturerId),
        product_type_id: normalizeHex(productType),
        product_id: normalizeHex(productId),
        device_type: label || 'Z-Wave device',
        device_class: deviceClass,
        suggested_driver: genericDriverFor('zwave', deviceClass),
        author: 'Z-Wave JS Config DB',
        hpm_available: false,
        url: 'https://github.com/zwave-js/node-zwave-js/tree/master/packages/config/config/devices',
        driver_scope: 'generic',
        source_note: 'Public identification source. Generic Hubitat driver inferred from Z-Wave JS metadata.'
      }, ['manufacturer_id', 'product_type_id', 'product_id']);
    }
  }

  fs.writeFileSync(path.join(dataDir, 'db_zwavejs_devices.json'), `${JSON.stringify({
    version,
    source: 'Z-Wave JS public config database',
    devices
  }, null, 2)}\n`);
  console.log(`Imported ${devices.length} Z-Wave JS fingerprints.`);
}

async function main() {
  await importZigbee2Mqtt();
  await importZwaveJs();
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
