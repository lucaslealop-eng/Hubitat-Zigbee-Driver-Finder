const fs = require('fs');
const path = require('path');

const SHEET_CSV_URL = process.env.COMPANY_SHEET_CSV_URL
  || 'https://docs.google.com/spreadsheets/d/1haVMgZNGjW8X5_Dq8t_cTuD0xk04DoXrxlNVa_GZb0Y/export?format=csv&gid=0';

const outputPath = path.join(__dirname, '..', 'data', 'db_company_devices.json');

function parseCsv(text) {
  const rows = [];
  let row = [];
  let field = '';
  let quoted = false;

  for (let i = 0; i < text.length; i++) {
    const char = text[i];
    const next = text[i + 1];

    if (quoted) {
      if (char === '"' && next === '"') {
        field += '"';
        i++;
      } else if (char === '"') {
        quoted = false;
      } else {
        field += char;
      }
      continue;
    }

    if (char === '"') quoted = true;
    else if (char === ',') {
      row.push(field);
      field = '';
    } else if (char === '\n') {
      row.push(field);
      rows.push(row);
      row = [];
      field = '';
    } else if (char !== '\r') {
      field += char;
    }
  }

  if (field.length > 0 || row.length > 0) {
    row.push(field);
    rows.push(row);
  }

  return rows;
}

function clean(value) {
  return String(value || '').trim();
}

function isYes(value) {
  return /^sim$/i.test(clean(value));
}

function isUsableDriver(value) {
  const driver = clean(value);
  return driver && driver.toLowerCase() !== 'link do driver';
}

function rowToDevice(row) {
  const brand = clean(row[0]);
  const category = clean(row[1]);
  const companyModel = clean(row[3]);
  const description = clean(row[4]);
  const zigbeeModel = clean(row[5]);
  const zigbeeManufacturer = clean(row[6]);
  const driver = clean(row[7]);

  if (!zigbeeModel || !zigbeeManufacturer || !isUsableDriver(driver)) {
    return null;
  }

  return {
    manufacturer: zigbeeManufacturer,
    model: zigbeeModel,
    device_type: [category, description].filter(Boolean).join(' - ') || 'Dispositivo Zigbee',
    suggested_driver: driver,
    author: brand || 'Company Sheet',
    hpm_available: isYes(row[8]),
    url: '',
    company_brand: brand,
    company_model: companyModel,
    company_description: description,
    in_clusters: clean(row[9]),
    out_clusters: clean(row[10])
  };
}

async function readCsv() {
  const fromFileIndex = process.argv.indexOf('--from-file');
  if (fromFileIndex !== -1 && process.argv[fromFileIndex + 1]) {
    return fs.readFileSync(process.argv[fromFileIndex + 1], 'utf8');
  }

  const response = await fetch(SHEET_CSV_URL);
  if (!response.ok) {
    throw new Error(`Failed to download company sheet: HTTP ${response.status}`);
  }
  return response.text();
}

async function main() {
  const csv = await readCsv();
  const rows = parseCsv(csv);
  const devices = rows.slice(1)
    .map(rowToDevice)
    .filter(Boolean);

  const seen = new Set();
  const uniqueDevices = devices.filter((device) => {
    const key = `${device.manufacturer.toLowerCase()}|${device.model.toLowerCase()}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });

  const db = {
    version: '2.0.0',
    source: 'Company Google Sheets',
    source_url: SHEET_CSV_URL,
    last_updated: new Date().toISOString().slice(0, 10),
    devices: uniqueDevices
  };

  fs.writeFileSync(outputPath, `${JSON.stringify(db, null, 2)}\n`);
  console.log(`Imported ${uniqueDevices.length} company devices into ${outputPath}`);
}

main().catch((error) => {
  console.error(error.message);
  process.exit(1);
});
