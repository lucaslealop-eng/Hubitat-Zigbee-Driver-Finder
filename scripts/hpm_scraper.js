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

async function scrape() {
    console.log("Fetching master repository...");
    const stats = {
        repositoriesFailed: 0,
        manifestsFailed: 0,
        driversFailed: 0,
        emptyDriverNames: 0
    };
    let masterJson;
    try {
        const masterRes = await fetchWithTimeout("https://raw.githubusercontent.com/HubitatCommunity/hubitat-packagerepositories/master/repositories.json");
        masterJson = await masterRes.json();
    } catch (e) {
        console.error("Failed to fetch master repository", e);
        return;
    }
    
    let allDevices = [];
    let repoUrls = masterJson.repositories || [];
    console.log(`Found ${repoUrls.length} repositories.`);

    // Process in chunks of 20 repositories to limit concurrency
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
                                    if (line.includes('fingerprint') && line.includes('model:')) {
                                        const manMatch = line.match(/manufacturer:\s*["']([^"']+)["']/);
                                        const modMatch = line.match(/model:\s*["']([^"']+)["']/);
                                        
                                        if (manMatch && modMatch) {
                                            if (!driver.name) {
                                                stats.emptyDriverNames++;
                                                return;
                                            }
                                            const device = {
                                                manufacturer: manMatch[1],
                                                model: modMatch[1],
                                                device_type: manifestJson.packageName || driver.name,
                                                suggested_driver: driver.name,
                                                author: manifestJson.author || "Community",
                                                hpm_available: true,
                                                url: manifestJson.communityLink || ""
                                            };
                                            if (!allDevices.some(d => d.manufacturer === device.manufacturer && d.model === device.model)) {
                                                allDevices.push(device);
                                                console.log(`  Found: ${device.manufacturer} / ${device.model} in ${driver.name}`);
                                            }
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
        console.log(`Processed batch ${Math.floor(i/chunk_size) + 1}/${Math.ceil(repoUrls.length/chunk_size)}... Total found so far: ${allDevices.length}`);
    }
    
    // Configurado com caminho relativo para funcionar tanto no GitHub Actions quanto localmente
    const outputPath = './data/db_hpm_scraped.json';
    if (allDevices.length < 500) {
        console.error(`Aborting: only ${allDevices.length} devices were scraped. This is probably a network or upstream parsing problem.`);
        process.exitCode = 1;
        return;
    }

    fs.writeFileSync(outputPath, JSON.stringify({ devices: allDevices }, null, 2));
    console.log(`\n========================================`);
    console.log(`Done! Scraped ${allDevices.length} devices. Saved to ${outputPath}`);
    console.log(`Failures: repositories=${stats.repositoriesFailed}, manifests=${stats.manifestsFailed}, drivers=${stats.driversFailed}, emptyDriverNames=${stats.emptyDriverNames}`);
}

scrape();
