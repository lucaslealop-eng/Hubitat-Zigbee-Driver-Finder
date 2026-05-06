const fs = require('fs');

async function fetchWithTimeout(url, options = {}) {
    const timeout = 10000;
    const controller = new AbortController();
    const id = setTimeout(() => controller.abort(), timeout);
    const response = await fetch(url, { ...options, signal: controller.signal });
    clearTimeout(id);
    return response;
}

async function scrape() {
    console.log("Fetching master repository...");
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
                if (repoRes.status !== 200) return;
                const repoJson = await repoRes.json();
                
                const packages = repoJson.packages || [];
                for (const pkg of packages) {
                    const manifestUrl = pkg.location;
                    if (!manifestUrl) continue;
                    
                    try {
                        const manifestRes = await fetchWithTimeout(manifestUrl);
                        if (manifestRes.status !== 200) continue;
                        const manifestJson = await manifestRes.json();
                        
                        const drivers = manifestJson.drivers || [];
                        await Promise.all(drivers.map(async (driver) => {
                            const codeUrl = driver.location;
                            if (!codeUrl) return;
                            try {
                                const codeRes = await fetchWithTimeout(codeUrl);
                                if (codeRes.status !== 200) return;
                                const code = await codeRes.text();
                                
                                if (!code.includes('fingerprint')) return;

                                const lines = code.split('\n');
                                for (const line of lines) {
                                    if (line.includes('fingerprint') && line.includes('model:')) {
                                        const manMatch = line.match(/manufacturer:\s*["']([^"']+)["']/);
                                        const modMatch = line.match(/model:\s*["']([^"']+)["']/);
                                        
                                        if (manMatch && modMatch) {
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
                            } catch (e) {}
                        }));
                    } catch (e) {}
                }
            } catch (e) {}
        }));
        console.log(`Processed batch ${Math.floor(i/chunk_size) + 1}/${Math.ceil(repoUrls.length/chunk_size)}... Total found so far: ${allDevices.length}`);
    }
    
    // Configurado com caminho relativo para funcionar tanto no GitHub Actions quanto localmente
    const outputPath = './data/db_hpm_scraped.json';
    fs.writeFileSync(outputPath, JSON.stringify({ devices: allDevices }, null, 2));
    console.log(`\n========================================`);
    console.log(`Done! Scraped ${allDevices.length} devices. Saved to ${outputPath}`);
}

scrape();
