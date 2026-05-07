const fs = require('fs');

async function fetchWithTimeout(url, options = {}) {
    const timeout = options.timeout || 10000;
    const controller = new AbortController();
    const id = setTimeout(() => controller.abort(), timeout);
    try {
        return await fetch(url, { ...options, signal: controller.signal });
    } finally {
        clearTimeout(id);
    }
}

function extractFingerprintValue(line, key) {
    const regex = new RegExp(`${key}\\s*:\\s*["']([^"']+)["']`, 'i');
    const match = line.match(regex);
    return match ? match[1].trim() : '';
}

function normalizeHex(value) {
    return String(value || '').trim().replace(/^0x/i, '').toUpperCase().padStart(4, '0');
}

function addUnique(collection, device, keyFields) {
    const key = keyFields.map((field) => String(device[field] || '').toLowerCase()).join('|');
    if (collection.some((item) => keyFields.map((field) => String(item[field] || '').toLowerCase()).join('|') === key)) {
        return false;
    }
    collection.push(device);
    return true;
}

function parseFingerprintLine(line, manifestJson, driver) {
    if (!line.includes('fingerprint')) return null;

    const zigbeeManufacturer = extractFingerprintValue(line, 'manufacturer');
    const zigbeeModel = extractFingerprintValue(line, 'model');
    if (zigbeeManufacturer && zigbeeModel) {
        return {
            protocol: 'zigbee',
            manufacturer: zigbeeManufacturer,
            model: zigbeeModel,
            device_type: manifestJson.packageName || driver.name,
            suggested_driver: driver.name,
            author: manifestJson.author || 'Community',
            hpm_available: true,
            url: manifestJson.communityLink || '',
            driver_scope: 'specific'
        };
    }

    const mfr = extractFingerprintValue(line, 'mfr');
    const prod = extractFingerprintValue(line, 'prod');
    const deviceId = extractFingerprintValue(line, 'deviceId');
    if (mfr && prod && deviceId) {
        return {
            protocol: 'zwave',
            manufacturer_id: normalizeHex(mfr),
            product_type_id: normalizeHex(prod),
            product_id: normalizeHex(deviceId),
            device_type: manifestJson.packageName || driver.name,
            suggested_driver: driver.name,
            author: manifestJson.author || 'Community',
            hpm_available: true,
            url: manifestJson.communityLink || '',
            driver_scope: 'specific'
        };
    }

    return null;
}

async function scrape() {
    console.log('Fetching master repository...');
    const stats = {
        repositoriesFailed: 0,
        manifestsFailed: 0,
        driversFailed: 0,
        emptyDriverNames: 0
    };
    let masterJson;
    try {
        const masterRes = await fetchWithTimeout('https://raw.githubusercontent.com/HubitatCommunity/hubitat-packagerepositories/master/repositories.json');
        masterJson = await masterRes.json();
    } catch (e) {
        console.error('Failed to fetch master repository', e);
        return;
    }

    const zigbeeDevices = [];
    const zwaveDevices = [];
    const repoUrls = masterJson.repositories || [];
    console.log(`Found ${repoUrls.length} repositories.`);

    const chunk_size = 20;
    for (let i = 0; i < repoUrls.length; i += chunk_size) {
        const chunk = repoUrls.slice(i, i + chunk_size);
        await Promise.all(chunk.map(async (repo) => {
            const repoUrl = repo.location;
            if (!repoUrl) return;
            try {
                const repoRes = await fetchWithTimeout(repoUrl);
                if (repoRes.status !== 200) {
                    stats.repositoriesFailed++;
                    return;
                }
                const repoJson = await repoRes.json();

                const packages = repoJson.packages || [];
                for (const pkg of packages) {
                    const manifestUrl = pkg.location;
                    if (!manifestUrl) continue;

                    try {
                        const manifestRes = await fetchWithTimeout(manifestUrl);
                        if (manifestRes.status !== 200) {
                            stats.manifestsFailed++;
                            continue;
                        }
                        const manifestJson = await manifestRes.json();

                        const drivers = manifestJson.drivers || [];
                        await Promise.all(drivers.map(async (driver) => {
                            const codeUrl = driver.location;
                            if (!codeUrl) return;
                            if (!driver.name) {
                                stats.emptyDriverNames++;
                                return;
                            }

                            try {
                                const codeRes = await fetchWithTimeout(codeUrl);
                                if (codeRes.status !== 200) {
                                    stats.driversFailed++;
                                    return;
                                }
                                const code = await codeRes.text();
                                if (!code.includes('fingerprint')) return;

                                const lines = code.split('\n');
                                for (const line of lines) {
                                    const device = parseFingerprintLine(line, manifestJson, driver);
                                    if (!device) continue;

                                    if (device.protocol === 'zwave') {
                                        if (addUnique(zwaveDevices, device, ['manufacturer_id', 'product_type_id', 'product_id'])) {
                                            console.log(`  Found Z-Wave: ${device.manufacturer_id}/${device.product_type_id}/${device.product_id} in ${driver.name}`);
                                        }
                                    } else {
                                        if (addUnique(zigbeeDevices, device, ['manufacturer', 'model'])) {
                                            console.log(`  Found Zigbee: ${device.manufacturer} / ${device.model} in ${driver.name}`);
                                        }
                                    }
                                }
                            } catch (e) {
                                stats.driversFailed++;
                            }
                        }));
                    } catch (e) {
                        stats.manifestsFailed++;
                    }
                }
            } catch (e) {
                stats.repositoriesFailed++;
            }
        }));
        console.log(`Processed batch ${Math.floor(i / chunk_size) + 1}/${Math.ceil(repoUrls.length / chunk_size)}... Zigbee: ${zigbeeDevices.length}, Z-Wave: ${zwaveDevices.length}`);
    }

    if (zigbeeDevices.length + zwaveDevices.length < 500) {
        console.error(`Aborting: only ${zigbeeDevices.length + zwaveDevices.length} devices were scraped. This is probably a network or upstream parsing problem.`);
        process.exitCode = 1;
        return;
    }

    fs.writeFileSync('./data/db_hpm_scraped.json', `${JSON.stringify({ version: '2.3.0', devices: zigbeeDevices }, null, 2)}\n`);
    fs.writeFileSync('./data/db_zwave_hpm_scraped.json', `${JSON.stringify({ version: '2.3.0', devices: zwaveDevices }, null, 2)}\n`);

    console.log('\n========================================');
    console.log(`Done! Scraped ${zigbeeDevices.length} Zigbee devices and ${zwaveDevices.length} Z-Wave devices.`);
    console.log('Saved to ./data/db_hpm_scraped.json and ./data/db_zwave_hpm_scraped.json');
    console.log(`Failures: repositories=${stats.repositoriesFailed}, manifests=${stats.manifestsFailed}, drivers=${stats.driversFailed}, emptyDriverNames=${stats.emptyDriverNames}`);
}

scrape();
