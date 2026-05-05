/**
 * ========================================================
 *  Hubitat Zigbee Driver Finder v3.0.0
 * ========================================================
 *  SmartApp para Hubitat Elevation
 *
 *  Identifica dispositivos Zigbee no hub e recomenda o
 *  driver mais adequado com base em manufacturer, model
 *  e clusters reportados.
 *
 *  Autor: Lucas (Hubitat Agent Project)
 *  Versão: 3.0.0
 *  Data: 2026-05-05
 *
 *  Funcionalidades:
 *   - Pesquisa individual de dispositivo
 *   - Scan completo de todos os Zigbee do hub
 *   - Cache local do banco de dados (24h TTL)
 *   - Comparação driver atual vs. recomendado
 *   - Ranking de confiança (⭐⭐⭐ / ⭐⭐ / ⭐)
 *   - Página de estatísticas
 * ========================================================
 */

import groovy.json.JsonSlurper
import groovy.transform.Field

definition(
    name: "Zigbee Driver Finder",
    namespace: "hubitat-agent",
    author: "Lucas",
    description: "Identifica dispositivos Zigbee e recomenda o driver ideal.",
    category: "Utility",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
    singleInstance: true
)

preferences {
    page(name: "mainPage")
    page(name: "scanSinglePage")
    page(name: "scanAllPage")
    page(name: "statsPage")
}

// ─── Constantes ────────────────────────────────────────
@Field static String DB_BASE_URL = "https://raw.githubusercontent.com/lucaslealop-eng/Hubitat-Zigbee-Driver-Finder/main/data/"
@Field static List DB_FILES = ["db_tuya.json", "db_xiaomi_aqara.json", "db_brands.json", "db_other_brands.json", "db_misc_zigbee.json", "db_hpm_scraped.json"]
@Field static String DB_INDEX_URL = "https://raw.githubusercontent.com/lucaslealop-eng/Hubitat-Zigbee-Driver-Finder/main/data/zigbee_driver_db.json"
@Field static String APP_VERSION = "3.0.0"

// ─── Cache Estático (JVM memory, não state) ────────────
@Field static Map cachedDb = null
@Field static Long cacheTs = 0
@Field static Long CACHE_TTL = 86400000

// ═══════════════════════════════════════════════════════
//  PÁGINAS DA INTERFACE
// ═══════════════════════════════════════════════════════

def mainPage() {
    dynamicPage(name: "mainPage", title: "", install: true, uninstall: true) {
        section("<h2>🔍 Zigbee Driver Finder v${APP_VERSION}</h2>") {
            paragraph "<i>Encontre o driver ideal para qualquer dispositivo Zigbee do seu hub.</i>"
        }
        section("<h3>⚙️ Configuração</h3>") {
            input name: "hubDevices", type: "capability.*", title: "Selecione TODOS os dispositivos do hub (necessário apenas uma vez)", required: false, multiple: true, submitOnChange: true
        }
        def zigbeeCount = getZigbeeDevices()?.size() ?: 0
        def totalCount = hubDevices?.size() ?: 0
        if (totalCount > 0) {
            section("") {
                paragraph "<span style='color:#2ecc71;font-size:14px;'>📡 <b>${zigbeeCount}</b> dispositivos Zigbee detectados de ${totalCount} selecionados.</span>"
            }
        }
        section("Menu Principal") {
            href "scanSinglePage", title: "🔎 Pesquisar Dispositivo Individual", description: "Selecione um dispositivo Zigbee e veja a recomendação detalhada"
            href "scanAllPage", title: "📊 Escanear Todos os Zigbee", description: "Analise todos os dispositivos Zigbee de uma vez"
            href "statsPage", title: "📈 Estatísticas", description: "Resumo do hub e status do banco de dados"
        }
        section("<h3>💾 Cache</h3>") {
            paragraph getCacheStatusText()
        }
    }
}

def scanSinglePage() {
    dynamicPage(name: "scanSinglePage", title: "", install: false) {
        def zigbeeList = getZigbeeDeviceOptions()
        section("<h2>🔎 Pesquisa Individual</h2>") {
            if (zigbeeList.size() == 0) {
                paragraph "<span style='color:#e74c3c;'>⚠️ Nenhum dispositivo Zigbee encontrado. Volte ao Menu Principal e selecione os dispositivos do hub primeiro.</span>"
            } else {
                input name: "selectedZigbeeId", type: "enum", title: "Dispositivo Zigbee (${zigbeeList.size()} disponíveis)", options: zigbeeList, required: false, submitOnChange: true
            }
        }
        if (selectedZigbeeId) {
            def dev = getZigbeeDevices().find { it.id.toString() == selectedZigbeeId }
            if (dev) {
                def db = getCachedDatabase()
                def devData = getDeviceZigbeeData(dev)
                section("<h3>📋 Informações do Dispositivo</h3>") {
                    paragraph formatDeviceInfo(devData)
                }
                if (db) {
                    def result = findBestMatch(db, devData)
                    section("<h3>🎯 Recomendação de Driver</h3>") {
                        paragraph formatMatchResult(result, devData)
                    }
                } else {
                    section("") { paragraph formatError("Não foi possível carregar o banco de dados remoto.") }
                }
            }
        }
    }
}

def scanAllPage() {
    dynamicPage(name: "scanAllPage", title: "", install: false) {
        def zigbeeDevs = getZigbeeDevices()
        section("<h2>📊 Scan Completo — ${zigbeeDevs.size()} Dispositivos Zigbee</h2>") {}
        if (!zigbeeDevs || zigbeeDevs.size() == 0) {
            section("") {
                paragraph "<span style='color:#e74c3c;'>⚠️ Nenhum dispositivo Zigbee encontrado. Volte ao Menu Principal e selecione os dispositivos do hub primeiro.</span>"
            }
            return
        }
        def db = getCachedDatabase()
        if (db) {
            def results = []
            def optimal = 0
            def improvable = 0
            def unknown = 0
            zigbeeDevs.each { dev ->
                def devData = getDeviceZigbeeData(dev)
                def match = findBestMatch(db, devData)
                def isOptimal = isDriverOptimal(devData.currentDriver, match)
                if (match.confidence == 0) { unknown++ }
                else if (isOptimal) { optimal++ }
                else { improvable++ }
                results << [devData: devData, match: match, optimal: isOptimal]
            }
            state.lastScanStats = [total: results.size(), optimal: optimal, improvable: improvable, unknown: unknown, scanDate: now()]
            section("<h3>📋 Resultado: ${results.size()} dispositivos Zigbee</h3>") {
                paragraph "<div style='display:flex;gap:12px;margin-bottom:12px;'>" +
                    "<span style='background:#0a3d0a;color:#2ecc71;padding:6px 12px;border-radius:8px;'>✅ Ideal: ${optimal}</span>" +
                    "<span style='background:#3d3a0a;color:#f1c40f;padding:6px 12px;border-radius:8px;'>🟡 Melhorar: ${improvable}</span>" +
                    "<span style='background:#3d0a0a;color:#e74c3c;padding:6px 12px;border-radius:8px;'>🔴 Sem sugestão: ${unknown}</span>" +
                    "</div>"
                paragraph formatScanAllTable(results)
                }
            } else {
                section("") { paragraph formatError("Não foi possível carregar o banco de dados remoto.") }
            }
        }
    }
}

def statsPage() {
    dynamicPage(name: "statsPage", title: "", install: false) {
        section("<h2>📈 Estatísticas</h2>") {}
        def db = getCachedDatabase()
        section("<h3>📦 Banco de Dados</h3>") {
            def dbSize = db ? db.devices?.size() ?: 0 : 0
            def rulesSize = db ? db.fallback_rules?.size() ?: 0 : 0
            paragraph "<table style='width:100%;border-collapse:collapse;font-family:sans-serif;font-size:14px;'>" +
                "<tr style='background:#16213e;'><td style='padding:8px;color:#3498db;border:1px solid #333;'><b>Versão do App</b></td><td style='padding:8px;color:#eee;border:1px solid #333;'>v${APP_VERSION}</td></tr>" +
                "<tr style='background:#1a1a2e;'><td style='padding:8px;color:#3498db;border:1px solid #333;'><b>Dispositivos no Banco</b></td><td style='padding:8px;color:#eee;border:1px solid #333;'>${dbSize}</td></tr>" +
                "<tr style='background:#16213e;'><td style='padding:8px;color:#3498db;border:1px solid #333;'><b>Regras de Fallback</b></td><td style='padding:8px;color:#eee;border:1px solid #333;'>${rulesSize}</td></tr>" +
                "<tr style='background:#1a1a2e;'><td style='padding:8px;color:#3498db;border:1px solid #333;'><b>Cache</b></td><td style='padding:8px;color:#eee;border:1px solid #333;'>${getCacheStatusText()}</td></tr>" +
                "</table>"
        }
        if (state.lastScanStats) {
            def s = state.lastScanStats
            def pctOpt = s.total > 0 ? Math.round((s.optimal / s.total) * 100) : 0
            section("<h3>🔍 Último Scan Completo</h3>") {
                paragraph "<table style='width:100%;border-collapse:collapse;font-family:sans-serif;font-size:14px;'>" +
                    "<tr style='background:#0a3d0a;'><td style='padding:8px;color:#2ecc71;border:1px solid #333;'><b>✅ Com driver ideal</b></td><td style='padding:8px;color:#eee;border:1px solid #333;'>${s.optimal} (${pctOpt}%)</td></tr>" +
                    "<tr style='background:#3d3a0a;'><td style='padding:8px;color:#f1c40f;border:1px solid #333;'><b>🟡 Com sugestão</b></td><td style='padding:8px;color:#eee;border:1px solid #333;'>${s.improvable}</td></tr>" +
                    "<tr style='background:#3d0a0a;'><td style='padding:8px;color:#e74c3c;border:1px solid #333;'><b>🔴 Sem recomendação</b></td><td style='padding:8px;color:#eee;border:1px solid #333;'>${s.unknown}</td></tr>" +
                    "<tr style='background:#16213e;'><td style='padding:8px;color:#3498db;border:1px solid #333;'><b>Total analisados</b></td><td style='padding:8px;color:#eee;border:1px solid #333;'>${s.total}</td></tr>" +
                    "</table>"
            }
        } else {
            section("") { paragraph "<i style='color:#888;'>Nenhum scan completo realizado ainda. Vá em 'Escanear Todos' primeiro.</i>" }
        }
    }
}

// ═══════════════════════════════════════════════════════
//  FILTRO DE DISPOSITIVOS ZIGBEE
// ═══════════════════════════════════════════════════════

/**
 * Filtra os dispositivos selecionados pelo usuário,
 * retornando apenas os que possuem dados Zigbee (manufacturer != null).
 */
def getZigbeeDevices() {
    if (!hubDevices) return []
    return hubDevices.findAll { dev ->
        def mfg = null
        try { mfg = dev.getDataValue("manufacturer") } catch (e) {}
        return mfg != null && mfg != ""
    }
}

/**
 * Gera um Map [id: "nome (fabricante / modelo)"] para uso em input enum.
 */
def getZigbeeDeviceOptions() {
    def zigbee = getZigbeeDevices()
    def options = [:]
    zigbee.each { dev ->
        def mfg = dev.getDataValue("manufacturer") ?: "?"
        def mdl = dev.getDataValue("model") ?: "?"
        options[dev.id.toString()] = "${dev.displayName} (${mfg} / ${mdl})"
    }
    return options
}

// ═══════════════════════════════════════════════════════
//  CACHE DO BANCO DE DADOS
// ═══════════════════════════════════════════════════════

def getCachedDatabase() {
    def elapsed = now() - cacheTs
    if (cachedDb != null && elapsed < CACHE_TTL) {
        log.debug "📦 Usando cache do banco de dados (${Math.round(elapsed/60000)} min)"
        return cachedDb
    }
    log.info "📦 Cache expirado ou vazio. Baixando banco de dados..."
    def db = fetchRemoteDatabase()
    if (db) {
        cachedDb = db
        cacheTs = now()
    }
    return db
}

def fetchRemoteDatabase() {
    try {
        def indexDb = [fallback_rules: [], devices: []]
        httpGet([uri: DB_INDEX_URL, contentType: "application/json", timeout: 15]) { resp ->
            if (resp.status == 200) { indexDb = resp.data }
        }
        def allDevices = []
        DB_FILES.each { fileName ->
            try {
                httpGet([uri: DB_BASE_URL + fileName, contentType: "application/json", timeout: 10]) { resp ->
                    if (resp.status == 200 && resp.data?.devices) {
                        allDevices.addAll(resp.data.devices)
                    }
                }
            } catch (e) { log.warn "Falha ao carregar ${fileName}: ${e.message}" }
        }
        def db = [devices: allDevices, fallback_rules: indexDb.fallback_rules ?: []]
        log.info "📦 Banco carregado: ${allDevices.size()} dispositivos, ${db.fallback_rules.size()} regras"
        return db
    } catch (e) {
        log.error "Falha ao baixar banco de dados: ${e.message}"
        return null
    }
}

def getCacheStatusText() {
    if (cachedDb == null) return "<span style='color:#e74c3c;'>⚪ Não carregado</span>"
    def elapsed = now() - cacheTs
    def remaining = CACHE_TTL - elapsed
    if (remaining <= 0) return "<span style='color:#f39c12;'>⏰ Expirado</span>"
    def hours = Math.round(remaining / 3600000)
    def mins = Math.round((remaining % 3600000) / 60000)
    return "<span style='color:#2ecc71;'>✅ Ativo — expira em ${hours}h ${mins}m</span>"
}

// ═══════════════════════════════════════════════════════
//  EXTRAÇÃO DE DADOS DO DISPOSITIVO
// ═══════════════════════════════════════════════════════

def getDeviceZigbeeData(dev) {
    def data = [:]
    try {
        data.manufacturer = dev.getDataValue("manufacturer") ?: "Desconhecido"
        data.model = dev.getDataValue("model") ?: "Desconhecido"
        data.inClusters = dev.getDataValue("inClusters") ?: ""
        data.outClusters = dev.getDataValue("outClusters") ?: ""
        data.profileId = dev.getDataValue("profileId") ?: ""
        data.endpointId = dev.getDataValue("endpointId") ?: "01"
    } catch (e) {
        log.error "Erro ao ler dados: ${e.message}"
        data.manufacturer = "Erro"; data.model = "Erro"
        data.inClusters = ""; data.outClusters = ""
    }
    data.deviceName = dev.displayName ?: "Sem nome"
    data.deviceId = dev.id
    data.currentDriver = dev.typeName ?: "Nenhum"
    return data
}

// ═══════════════════════════════════════════════════════
//  LÓGICA DE MATCHING (retorna objeto estruturado)
// ═══════════════════════════════════════════════════════

/**
 * Retorna: [type: "exact"|"partial"|"prefix"|"cluster"|"none",
 *           confidence: 3|2|1|0, data: <match>, matches: [...]]
 */
def findBestMatch(Map db, Map devData) {
    if (!db || !db.devices) return [type: "none", confidence: 0, data: null, matches: [], rules: []]

    // 1. Match exato
    def exact = db.devices.find { e ->
        e.manufacturer?.toLowerCase() == devData.manufacturer?.toLowerCase() &&
        e.model?.toLowerCase() == devData.model?.toLowerCase()
    }
    if (exact) return [type: "exact", confidence: 3, data: exact, matches: [], rules: []]

    // 2. Match parcial (mesmo fabricante)
    def partial = db.devices.findAll { e ->
        e.manufacturer?.toLowerCase() == devData.manufacturer?.toLowerCase()
    }
    if (partial && partial.size() > 0) {
        return [type: "partial", confidence: 2, data: partial[0], matches: partial.take(5), rules: []]
    }

    // 3. Fallback por prefixo do fabricante
    if (db.fallback_rules) {
        def prefix = db.fallback_rules.find { r ->
            r.condition == "manufacturer_prefix" && devData.manufacturer?.startsWith(r.match)
        }
        if (prefix) return [type: "prefix", confidence: 1, data: prefix, matches: [], rules: []]
    }

    // 4. Fallback por clusters
    if (db.fallback_rules && devData.inClusters) {
        def clusters = devData.inClusters.replaceAll("\\s", "").split(",")
        def clusterRules = []
        db.fallback_rules.findAll { it.condition == "cluster_present" }.each { rule ->
            if (clusters.any { c -> c.equalsIgnoreCase(rule.match) }) { clusterRules << rule }
        }
        if (clusterRules.size() > 0) {
            return [type: "cluster", confidence: 1, data: null, matches: [], rules: clusterRules]
        }
    }

    return [type: "none", confidence: 0, data: null, matches: [], rules: []]
}

def isDriverOptimal(String currentDriver, Map matchResult) {
    if (matchResult.confidence == 0) return false
    def suggested = matchResult.data?.suggested_driver ?: ""
    if (!suggested) return false
    return currentDriver?.toLowerCase()?.trim() == suggested?.toLowerCase()?.trim()
}

def getConfidenceStars(int confidence) {
    switch(confidence) {
        case 3: return "⭐⭐⭐"
        case 2: return "⭐⭐"
        case 1: return "⭐"
        default: return "—"
    }
}

// ═══════════════════════════════════════════════════════
//  FORMATAÇÃO HTML
// ═══════════════════════════════════════════════════════

def formatDeviceInfo(Map d) {
    return "<table style='width:100%;border-collapse:collapse;font-family:monospace;font-size:14px;'>" +
        "<tr style='background:#1a1a2e;color:#e94560;'><td style='padding:8px;border:1px solid #333;'><b>Nome</b></td><td style='padding:8px;border:1px solid #333;'>${d.deviceName}</td></tr>" +
        "<tr style='background:#16213e;color:#eee;'><td style='padding:8px;border:1px solid #333;'><b>Manufacturer</b></td><td style='padding:8px;border:1px solid #333;'>${d.manufacturer}</td></tr>" +
        "<tr style='background:#1a1a2e;color:#eee;'><td style='padding:8px;border:1px solid #333;'><b>Model</b></td><td style='padding:8px;border:1px solid #333;'>${d.model}</td></tr>" +
        "<tr style='background:#16213e;color:#eee;'><td style='padding:8px;border:1px solid #333;'><b>In Clusters</b></td><td style='padding:8px;border:1px solid #333;'>${d.inClusters}</td></tr>" +
        "<tr style='background:#1a1a2e;color:#eee;'><td style='padding:8px;border:1px solid #333;'><b>Out Clusters</b></td><td style='padding:8px;border:1px solid #333;'>${d.outClusters}</td></tr>" +
        "<tr style='background:#16213e;color:#e94560;'><td style='padding:8px;border:1px solid #333;'><b>Driver Atual</b></td><td style='padding:8px;border:1px solid #333;'>${d.currentDriver}</td></tr>" +
        "</table>"
}

def formatMatchResult(Map result, Map devData) {
    def optimal = isDriverOptimal(devData.currentDriver, result)
    def stars = getConfidenceStars(result.confidence)

    switch(result.type) {
        case "exact":
            def m = result.data
            def hpmBadge = m.hpm_available ?
                "<span style='background:#27ae60;color:#fff;padding:2px 8px;border-radius:4px;font-size:12px;'>✅ HPM</span>" :
                "<span style='background:#2980b9;color:#fff;padding:2px 8px;border-radius:4px;font-size:12px;'>📦 Built-in</span>"
            def optBadge = optimal ?
                "<div style='background:#0a3d0a;border:1px solid #2ecc71;border-radius:8px;padding:8px;margin-top:8px;'><b style='color:#2ecc71;'>✅ Você já está usando o driver ideal!</b></div>" : ""
            return "<div style='background:#0a3d0a;border:2px solid #27ae60;border-radius:12px;padding:16px;font-family:sans-serif;'>" +
                "<h3 style='color:#2ecc71;margin:0 0 8px 0;'>✅ Driver Encontrado! ${stars}</h3>" +
                "<p style='color:#eee;font-size:16px;margin:4px 0;'><b>Driver Recomendado:</b> ${m.suggested_driver}</p>" +
                "<p style='color:#bbb;font-size:14px;margin:4px 0;'><b>Tipo:</b> ${m.device_type} | <b>Autor:</b> ${m.author}</p>" +
                "<p style='margin:8px 0;'>${hpmBadge}</p>${optBadge}</div>"

        case "partial":
            def list = result.matches.collect { m -> "<li style='color:#eee;'><b>${m.model}</b> → ${m.suggested_driver} (${m.author})</li>" }.join("")
            return "<div style='background:#3d3a0a;border:2px solid #f39c12;border-radius:12px;padding:16px;font-family:sans-serif;'>" +
                "<h3 style='color:#f1c40f;margin:0 0 8px 0;'>⚠️ Match Parcial ${stars}</h3>" +
                "<p style='color:#eee;'>Modelo <b>${devData.model}</b> não encontrado, mas há drivers do mesmo fabricante (<b>${devData.manufacturer}</b>):</p>" +
                "<ul style='margin:8px 0;'>${list}</ul></div>"

        case "prefix":
            def r = result.data
            def link = r.url ? "<br/><a href='${r.url}' target='_blank' style='color:#3498db;'>🔗 Ver Referência</a>" : ""
            return "<div style='background:#3d2a0a;border:2px solid #e67e22;border-radius:12px;padding:16px;font-family:sans-serif;'>" +
                "<h3 style='color:#e67e22;margin:0 0 8px 0;'>⚠️ Recomendação por Fabricante ${stars}</h3>" +
                "<p style='color:#eee;font-size:14px;'>${r.message}</p>${link}</div>"

        case "cluster":
            def rList = result.rules.collect { r ->
                def dt = r.suggested_driver ? " → <b>${r.suggested_driver}</b>" : ""
                "<li style='color:#eee;'>${r.message}${dt}</li>"
            }.join("")
            return "<div style='background:#0a2a3d;border:2px solid #2980b9;border-radius:12px;padding:16px;font-family:sans-serif;'>" +
                "<h3 style='color:#3498db;margin:0 0 8px 0;'>🔎 Análise por Clusters ${stars}</h3>" +
                "<p style='color:#bbb;'>Dispositivo <b>${devData.manufacturer} / ${devData.model}</b> não no banco. Sugestões baseadas nos clusters:</p>" +
                "<ul style='margin:8px 0;'>${rList}</ul></div>"

        default:
            return "<div style='background:#3d0a0a;border:2px solid #c0392b;border-radius:12px;padding:16px;font-family:sans-serif;'>" +
                "<h3 style='color:#e74c3c;margin:0 0 8px 0;'>❌ Dispositivo Não Reconhecido</h3>" +
                "<p style='color:#eee;'>Sem recomendação para: <b>${devData.manufacturer} / ${devData.model}</b></p>" +
                "<p style='color:#bbb;font-size:13px;'>Procure no <a href='https://community.hubitat.com/' target='_blank' style='color:#3498db;'>Fórum</a> ou no HPM.</p></div>"
    }
}

def formatScanAllTable(List results) {
    def rows = ""
    results.each { r ->
        def d = r.devData
        def m = r.match
        def opt = r.optimal
        def suggested = m.data?.suggested_driver ?: (m.rules?.size() > 0 ? m.rules[0].suggested_driver ?: "Ver clusters" : "—")
        def bgColor = m.confidence == 0 ? "#3d0a0a" : (opt ? "#0a3d0a" : "#3d3a0a")
        def statusIcon = m.confidence == 0 ? "🔴" : (opt ? "✅" : "🟡")
        def stars = getConfidenceStars(m.confidence)
        rows += "<tr style='background:${bgColor};'>" +
            "<td style='padding:6px 8px;border:1px solid #333;color:#eee;'>${d.deviceName}</td>" +
            "<td style='padding:6px 8px;border:1px solid #333;color:#bbb;'>${d.manufacturer}</td>" +
            "<td style='padding:6px 8px;border:1px solid #333;color:#bbb;'>${d.model}</td>" +
            "<td style='padding:6px 8px;border:1px solid #333;color:#888;'>${d.currentDriver}</td>" +
            "<td style='padding:6px 8px;border:1px solid #333;color:#eee;'><b>${suggested}</b></td>" +
            "<td style='padding:6px 8px;border:1px solid #333;text-align:center;'>${stars}</td>" +
            "<td style='padding:6px 8px;border:1px solid #333;text-align:center;'>${statusIcon}</td>" +
            "</tr>"
    }
    return "<div style='overflow-x:auto;'><table style='width:100%;border-collapse:collapse;font-family:sans-serif;font-size:13px;'>" +
        "<tr style='background:#0f3460;'>" +
        "<th style='padding:8px;border:1px solid #333;color:#e94560;text-align:left;'>Dispositivo</th>" +
        "<th style='padding:8px;border:1px solid #333;color:#e94560;text-align:left;'>Fabricante</th>" +
        "<th style='padding:8px;border:1px solid #333;color:#e94560;text-align:left;'>Modelo</th>" +
        "<th style='padding:8px;border:1px solid #333;color:#e94560;text-align:left;'>Driver Atual</th>" +
        "<th style='padding:8px;border:1px solid #333;color:#e94560;text-align:left;'>Recomendado</th>" +
        "<th style='padding:8px;border:1px solid #333;color:#e94560;text-align:center;'>Confiança</th>" +
        "<th style='padding:8px;border:1px solid #333;color:#e94560;text-align:center;'>Status</th>" +
        "</tr>${rows}</table></div>"
}

def formatError(String msg) {
    return "<div style='background:#3d0a0a;border:2px solid #e74c3c;border-radius:12px;padding:16px;font-family:sans-serif;'>" +
        "<h3 style='color:#e74c3c;margin:0 0 8px 0;'>⚠️ Erro</h3>" +
        "<p style='color:#eee;'>${msg}</p></div>"
}

// ═══════════════════════════════════════════════════════
//  CICLO DE VIDA
// ═══════════════════════════════════════════════════════

def installed() { log.info "Zigbee Driver Finder v${APP_VERSION} instalado."; initialize() }
def updated() { log.info "Zigbee Driver Finder v${APP_VERSION} atualizado."; initialize() }
def initialize() { log.info "Zigbee Driver Finder v${APP_VERSION} inicializado." }
def uninstalled() { log.info "Zigbee Driver Finder desinstalado." }
