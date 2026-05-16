/**
 * ========================================================
 *  Hubitat Driver Finder v2.5.1
 * ========================================================
 *  Copyright (c) 2026 Lucas (Hubitat Agent Project)
 *  All rights reserved.
 *
 *  This software is proprietary. Unauthorized copying,
 *  modification, distribution, or commercial use is
 *  strictly prohibited. See LICENSE file for details.
 * ========================================================
 *  SmartApp para Hubitat Elevation
 *
 *  Identifica dispositivos Zigbee e Z-Wave no hub e recomenda o
 *  driver mais adequado com base em manufacturer, model
 *  e clusters reportados.
 *
 *  Autor: Lucas (Hubitat Agent Project)
 *  Versão: 2.5.1
 *  Data: 2026-05-08
 *
 *  Funcionalidades:
 *   - Pesquisa individual de dispositivo
 *   - Scan completo de todos os Zigbee e Z-Wave do hub
 *   - Cache local do banco de dados (24h TTL)
 *   - Comparação driver atual vs. recomendado
 *   - Ranking de confiança (⭐⭐⭐ / ⭐⭐ / ⭐)
 *   - Classificação 4 estados: Ideal / Compatível / Sugestão / Não encontrado
 *   - Alternativas completas com HPM badge e link
 *   - Matching flexível de nomes de driver
 *   - Página de estatísticas
 *   - Indicação HPM disponível + link do driver/autor
 * ========================================================
 */

import groovy.json.JsonSlurper
import groovy.transform.Field

definition(
    name: "Hubitat Driver Finder",
    namespace: "hubitat-agent",
    author: "Lucas",
    description: "Identifica dispositivos Zigbee e Z-Wave e recomenda o driver ideal.",
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
    page(name: "refreshDbPage")
    page(name: "statsPage")
}

// ─── Constantes ────────────────────────────────────────
@Field static String DB_BASE_URL = "https://raw.githubusercontent.com/lucaslealop-eng/Hubitat-Driver-Finder-Data/main/"
@Field static List DB_FILES = ["db_overrides.json", "db_company_devices.json", "db_zwave_devices.json", "db_zwave_hpm_scraped.json", "db_tuya.json", "db_xiaomi_aqara.json", "db_brands.json", "db_other_brands.json", "db_misc_zigbee.json", "db_hpm_scraped.json", "db_zigbee2mqtt_devices.json", "db_zwavejs_devices.json"]
@Field static String DB_INDEX_URL = "https://raw.githubusercontent.com/lucaslealop-eng/Hubitat-Driver-Finder-Data/main/zigbee_driver_db.json"
@Field static Map DB_REQUEST_HEADERS = ["User-Agent": "Hubitat-Driver-Finder/2.5.1"]
@Field static String APP_VERSION = "2.5.1"

// ─── Cache Estático (JVM memory, não state) ────────────
@Field static Map cachedDb = null
@Field static Long cacheTs = 0
@Field static Long CACHE_TTL = 86400000

// ═══════════════════════════════════════════════════════
//  PÁGINAS DA INTERFACE
// ═══════════════════════════════════════════════════════

def mainPage() {
    dynamicPage(name: "mainPage", title: "", install: true, uninstall: true) {
        section("<h2>🔍 Hubitat Driver Finder v${APP_VERSION}</h2>") {
            paragraph "<i>Encontre o driver ideal para dispositivos Zigbee e Z-Wave do seu hub.</i>"
        }
        if (!hubDevices || hubDevices.size() == 0) {
            section("<h3>👋 Bem-vindo! Vamos começar</h3>") {
                paragraph "<span style='color:#f1c40f;font-size:14px;'>Para o app funcionar, ele precisa de acesso aos seus dispositivos.<br/><b>Toque no botão abaixo</b>, depois toque em <b>'Select All'</b> para selecionar todos de uma vez.</span>"
                input name: "hubDevices", type: "capability.*", title: "📱 Toque aqui e selecione todos", required: false, multiple: true, submitOnChange: true
            }
        } else {
            def supportedCount = getSupportedDevices()?.size() ?: 0
            def zigbeeCount = getSupportedDevices().findAll { getDeviceProtocol(it) == "zigbee" }?.size() ?: 0
            def zwaveCount = getSupportedDevices().findAll { getDeviceProtocol(it) == "zwave" }?.size() ?: 0
            def totalCount = hubDevices?.size() ?: 0
            section("") {
                paragraph "<div style='background:#0a3d0a;border:1px solid #27ae60;border-radius:8px;padding:12px;font-family:sans-serif;'>" +
                    "<span style='color:#2ecc71;font-size:15px;'>📡 <b>${supportedCount}</b> dispositivos suportados detectados</span><br/>" +
                    "<span style='color:#bbb;font-size:12px;'>Zigbee: ${zigbeeCount} | Z-Wave: ${zwaveCount}</span><br/>" +
                    "<span style='color:#888;font-size:12px;'>${totalCount} dispositivos totais no hub</span></div>"
            }
            section("O que deseja fazer?") {
                href "scanSinglePage", title: "🔎 Pesquisar Um Dispositivo", description: "Escolha um dispositivo e veja qual driver usar"
                href "scanAllPage", title: "📊 Analisar Todos de Uma Vez", description: "Relatório completo de Zigbee e Z-Wave"
                href "statsPage", title: "📈 Estatísticas do Hub", description: "Resumo geral e banco de dados"
                href "refreshDbPage", title: "🔄 Baixar Database Novamente", description: "Limpa o cache de 24h e baixa a lista de drivers atualizada"
            }
            section("") {
                paragraph getCacheStatusText()
                paragraph getCacheHelpText()
                input name: "hubDevices", type: "capability.*", title: "⚙️ Alterar dispositivos selecionados", required: false, multiple: true, submitOnChange: true
            }
        }
    }
}

def refreshDbPage() {
    dynamicPage(name: "refreshDbPage", title: "", install: false) {
        section("<h2>🔄 Atualizar Database</h2>") {
            cachedDb = null
            cacheTs = 0
            def db = getCachedDatabase()
            if (db) {
                paragraph "<div style='background:#0a3d0a;border:1px solid #27ae60;border-radius:8px;padding:12px;font-family:sans-serif;'>" +
                    "<b style='color:#2ecc71;'>Database baixada novamente com sucesso.</b><br/>" +
                    "<span style='color:#bbb;font-size:13px;'>${db.devices?.size() ?: 0} dispositivos carregados. O timer de 24h foi reiniciado.</span></div>"
            } else {
                paragraph formatError("Nao foi possivel baixar a database agora. Verifique a conexao do hub com a internet.")
            }
            href "mainPage", title: "Voltar", description: "Retornar ao menu principal"
        }
    }
}

def scanSinglePage() {
    dynamicPage(name: "scanSinglePage", title: "", install: false) {
        def deviceList = getSupportedDeviceOptions()
        if (deviceList.size() == 0) {
            section("<h2>🔎 Pesquisa Individual</h2>") {
                paragraph "<span style='color:#e74c3c;font-size:14px;'>⚠️ Nenhum dispositivo Zigbee ou Z-Wave encontrado.<br/>Volte ao menu principal e selecione os dispositivos do hub.</span>"
            }
            return
        }
        section("<h2>🔎 Escolha o Dispositivo</h2>") {
            input name: "selectedZigbeeId", type: "enum", title: "Dispositivo (${deviceList.size()} encontrados)", options: deviceList, required: false, submitOnChange: true
        }
        if (selectedZigbeeId) {
            def dev = getSupportedDevices().find { it.id.toString() == selectedZigbeeId }
            if (dev) {
                def db = getCachedDatabase()
                def devData = getDeviceData(dev)
                section("<h3>📋 Informações do Dispositivo</h3>") {
                    paragraph formatDeviceInfo(devData)
                }
                if (db) {
                    def result = findBestMatch(db, devData)
                    section("<h3>🎯 Recomendação</h3>") {
                        paragraph formatMatchResult(result, devData)
                    }
                } else {
                    section("") { paragraph formatError("Não foi possível carregar o banco de dados. Verifique sua conexão.") }
                }
            }
        }
    }
}

def scanAllPage() {
    dynamicPage(name: "scanAllPage", title: "", install: false) {
        def supportedDevs = getSupportedDevices()
        if (!supportedDevs || supportedDevs.size() == 0) {
            section("<h2>📊 Análise Completa</h2>") {
                paragraph "<span style='color:#e74c3c;font-size:14px;'>⚠️ Nenhum dispositivo Zigbee ou Z-Wave encontrado.<br/>Volte ao menu principal e selecione os dispositivos do hub.</span>"
            }
            return
        }
        section("<h2>📊 Análise Completa — ${supportedDevs.size()} Dispositivos</h2>") {
            paragraph "<i style='color:#888;'>Todos os dispositivos Zigbee e Z-Wave suportados são analisados automaticamente.</i>"
        }
        def db = getCachedDatabase()
        if (db) {
            def results = []
            def cntIdeal = 0
            def cntCompatible = 0
            def cntSuggestion = 0
            def cntUnknown = 0
            supportedDevs.each { dev ->
                def devData = getDeviceData(dev)
                def match = findBestMatch(db, devData)
                def status = getDriverStatus(devData.currentDriver, match)
                switch(status) {
                    case "ideal": cntIdeal++; break
                    case "compatible": cntCompatible++; break
                    case "suggestion": cntSuggestion++; break
                    default: cntUnknown++; break
                }
                results << [devData: devData, match: match, status: status]
            }
            state.lastScanStats = [total: results.size(), optimal: cntIdeal, compatible: cntCompatible, suggestion: cntSuggestion, unknown: cntUnknown, scanDate: now()]
            section("") {
                paragraph "<div style='display:flex;flex-wrap:wrap;gap:8px;margin-bottom:12px;'>" +
                    "<span style='background:#0a3d0a;color:#2ecc71;padding:6px 12px;border-radius:8px;'>✅ Ideal: ${cntIdeal}</span>" +
                    "<span style='background:#0a2a3d;color:#3498db;padding:6px 12px;border-radius:8px;'>🔵 Compatível: ${cntCompatible}</span>" +
                    "<span style='background:#3d3a0a;color:#f1c40f;padding:6px 12px;border-radius:8px;'>🟡 Sugestão: ${cntSuggestion}</span>" +
                    "<span style='background:#3d0a0a;color:#e74c3c;padding:6px 12px;border-radius:8px;'>🔴 Não encontrado: ${cntUnknown}</span>" +
                    "</div>"
                paragraph formatScanAllTable(results)
            }
        } else {
            section("") { paragraph formatError("Não foi possível carregar o banco de dados. Verifique sua conexão.") }
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
            def pctOpt = s.total > 0 ? Math.round(((s.optimal ?: 0) / s.total) * 100) : 0
            def compat = s.compatible ?: 0
            def suggest = s.suggestion ?: s.improvable ?: 0
            section("<h3>🔍 Último Scan Completo</h3>") {
                paragraph "<table style='width:100%;border-collapse:collapse;font-family:sans-serif;font-size:14px;'>" +
                    "<tr style='background:#0a3d0a;'><td style='padding:8px;color:#2ecc71;border:1px solid #333;'><b>✅ Ideal</b></td><td style='padding:8px;color:#eee;border:1px solid #333;'>${s.optimal ?: 0} (${pctOpt}%)</td></tr>" +
                    "<tr style='background:#0a2a3d;'><td style='padding:8px;color:#3498db;border:1px solid #333;'><b>🔵 Compatível</b></td><td style='padding:8px;color:#eee;border:1px solid #333;'>${compat}</td></tr>" +
                    "<tr style='background:#3d3a0a;'><td style='padding:8px;color:#f1c40f;border:1px solid #333;'><b>🟡 Sugestão disponível</b></td><td style='padding:8px;color:#eee;border:1px solid #333;'>${suggest}</td></tr>" +
                    "<tr style='background:#3d0a0a;'><td style='padding:8px;color:#e74c3c;border:1px solid #333;'><b>🔴 Não encontrado</b></td><td style='padding:8px;color:#eee;border:1px solid #333;'>${s.unknown ?: 0}</td></tr>" +
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
        return getDeviceProtocol(dev) == "zigbee"
    }
}

def getSupportedDevices() {
    if (!hubDevices) return []
    return hubDevices.findAll { dev -> getDeviceProtocol(dev) != "unknown" }
}

def getDeviceProtocol(dev) {
    def msr = getZwaveMsr(dev)
    def zwNodeInfo = safeDataValue(dev, "zwNodeInfo")
    def inClusters = safeDataValue(dev, "inClusters")
    def outClusters = safeDataValue(dev, "outClusters")
    def mfg = safeDataValue(dev, "manufacturer")
    def model = safeDataValue(dev, "model")
    def zwMfr = getZwaveMfrId(dev)
    def zwProd = getZwaveProductTypeId(dev)
    def zwModel = getZwaveProductId(dev)

    if (msr) return "zwave"
    if (zwNodeInfo) return "zwave"
    if (zwMfr && zwProd && zwModel) return "zwave"
    if (looksLikeZwaveCommandClasses(inClusters)) return "zwave"
    if (inClusters || outClusters) return "zigbee"
    if (mfg && model && "${mfg}".startsWith("_")) return "zigbee"
    return "unknown"
}

def safeDataValue(dev, String key) {
    try { return dev.getDataValue(key) ?: "" } catch (e) { return "" }
}

def firstDataValue(dev, List keys) {
    for (key in keys) {
        def value = safeDataValue(dev, key)
        if (value) return value
    }
    return ""
}

def normalizeHexId(value) {
    def raw = "${value ?: ''}".trim().replaceFirst("(?i)^0x", "").toUpperCase()
    if (!raw) return ""
    if (!(raw ==~ /[0-9A-F]+/)) return ""
    return raw.padLeft(4, "0")
}

def normalizeHubZwaveId(value) {
    def raw = "${value ?: ''}".trim().replaceFirst("(?i)^0x", "").toUpperCase()
    if (!raw) return ""
    if (!(raw ==~ /[0-9A-F]+/)) return ""
    if (raw ==~ /[0-9]+/) {
        def numeric = raw.toInteger()
        if (numeric > 255 || raw.length() < 4) return Integer.toHexString(numeric).toUpperCase().padLeft(4, "0")
    }
    return raw.padLeft(4, "0")
}

def looksLikeZwaveCommandClasses(String clusters) {
    def raw = "${clusters ?: ''}".trim()
    if (!raw) return false
    return raw.contains("0x5E") || raw.contains("0x86") || raw.contains("0x72") || raw.contains("0x85") || raw.contains("0x9F")
}

def getZwaveMsr(dev) {
    def msr = firstDataValue(dev, ["MSR", "msr"])
    if (!msr) return ""
    def parts = "${msr}".trim().split("-")
    return parts.size() >= 3 ? "${parts[0]}-${parts[1]}-${parts[2]}" : ""
}

def getZwaveMsrPart(dev, int index) {
    def msr = getZwaveMsr(dev)
    if (!msr) return ""
    def parts = msr.split("-")
    return parts.size() > index ? normalizeHexId(parts[index]) : ""
}

def getZwaveMfrId(dev) {
    return getZwaveMsrPart(dev, 0) ?: normalizeHubZwaveId(firstDataValue(dev, ["manufacturerId", "manufacturer"]))
}

def getZwaveProductTypeId(dev) {
    return getZwaveMsrPart(dev, 1) ?: normalizeHubZwaveId(firstDataValue(dev, ["productTypeId", "deviceType", "productType", "deviceTypeId"]))
}

def getZwaveProductId(dev) {
    return getZwaveMsrPart(dev, 2) ?: normalizeHubZwaveId(firstDataValue(dev, ["productId", "deviceId", "deviceIdType", "model"]))
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

def getSupportedDeviceOptions() {
    def devices = getSupportedDevices()
    def options = [:]
    devices.each { dev ->
        def protocol = getDeviceProtocol(dev).toUpperCase()
        if (protocol == "ZWAVE") protocol = "Z-Wave"
        def mfg = protocol == "Z-Wave" ? getZwaveMfrId(dev) : (safeDataValue(dev, "manufacturer") ?: "?")
        def mdl = protocol == "Z-Wave" ? "${getZwaveProductTypeId(dev)}/${getZwaveProductId(dev)}" : (safeDataValue(dev, "model") ?: safeDataValue(dev, "deviceId") ?: "?")
        options[dev.id.toString()] = "${dev.displayName} [${protocol}] (${mfg} / ${mdl})"
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
        httpGet([uri: DB_INDEX_URL, contentType: "application/json", timeout: 30, headers: DB_REQUEST_HEADERS]) { resp ->
            if (resp.status == 200) {
                indexDb = resp.data
            } else {
                log.warn "Indice do banco retornou HTTP ${resp.status}: ${DB_INDEX_URL}"
            }
        }
        def allDevices = []
        def loadedFiles = []
        def failedFiles = []
        DB_FILES.each { fileName ->
            try {
                httpGet([uri: DB_BASE_URL + fileName, contentType: "application/json", timeout: 30, headers: DB_REQUEST_HEADERS]) { resp ->
                    if (resp.status == 200 && resp.data?.devices) {
                        resp.data.devices.each { device ->
                            def enriched = [:] + device
                            enriched._sourceFile = fileName
                            enriched._sourcePriority = getSourcePriority(fileName)
                            allDevices << enriched
                        }
                        loadedFiles << "${fileName} (${resp.data.devices.size()})"
                    } else {
                        failedFiles << "${fileName} (HTTP ${resp.status})"
                    }
                }
            } catch (e) {
                failedFiles << "${fileName} (${e.message})"
                log.warn "Falha ao carregar ${fileName}: ${e.message}"
            }
        }
        if (failedFiles) {
            log.warn "Arquivos do banco com falha: ${failedFiles.join(', ')}"
        }
        if (allDevices.size() == 0) {
            log.error "Banco remoto nao carregou nenhum dispositivo. Verifique acesso do hub a ${DB_BASE_URL}"
            return null
        }
        def db = [devices: allDevices, fallback_rules: indexDb.fallback_rules ?: []]
        log.info "📦 Banco carregado: ${allDevices.size()} dispositivos, ${db.fallback_rules.size()} regras em ${loadedFiles.size()} arquivos"
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

def getCacheHelpText() {
    return "<span style='color:#aaa;font-size:12px;'>O contador de 24h mostra por quanto tempo o app vai reutilizar a lista de drivers que ja baixou. Quando o tempo acabar, ele baixa uma lista atualizada na proxima consulta. Isso evita downloads repetidos e deixa o app mais rapido.</span>"
}

def getSourcePriority(String fileName) {
    switch(fileName) {
        case "db_overrides.json": return 1
        case "db_company_devices.json": return 5
        case "db_zwave_devices.json": return 8
        case "db_zwave_hpm_scraped.json": return 100
        case "db_tuya.json": return 10
        case "db_xiaomi_aqara.json": return 20
        case "db_brands.json": return 30
        case "db_other_brands.json": return 40
        case "db_misc_zigbee.json": return 50
        case "db_hpm_scraped.json": return 100
        case "db_zigbee2mqtt_devices.json": return 200
        case "db_zwavejs_devices.json": return 200
        default: return 999
    }
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
    data.protocol = "zigbee"
    data.deviceName = dev.displayName ?: "Sem nome"
    data.deviceId = dev.id
    data.currentDriver = dev.typeName ?: "Nenhum"
    return data
}

def getDeviceData(dev) {
    def protocol = getDeviceProtocol(dev)
    if (protocol == "zwave") return getDeviceZwaveData(dev)
    return getDeviceZigbeeData(dev)
}

def getDeviceZwaveData(dev) {
    def data = [:]
    data.protocol = "zwave"
    data.manufacturer = safeDataValue(dev, "manufacturer") ?: "Desconhecido"
    data.model = safeDataValue(dev, "model") ?: safeDataValue(dev, "deviceId") ?: "Desconhecido"
    data.manufacturerId = getZwaveMfrId(dev)
    data.productTypeId = getZwaveProductTypeId(dev)
    data.productId = getZwaveProductId(dev)
    data.inClusters = ""
    data.outClusters = ""
    data.profileId = ""
    data.endpointId = ""
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

    def protocol = devData.protocol ?: "zigbee"
    def exactMatches = db.devices.findAll { e -> isExactFingerprint(e, devData, protocol) }
        .collect { e -> scoreCandidate(e, devData, "exact") }
        .sort { a, b -> b.score <=> a.score ?: (a.data._sourcePriority ?: 999) <=> (b.data._sourcePriority ?: 999) }
    if (exactMatches) {
        def best = exactMatches[0]
        def alternatives = exactMatches.drop(1).take(4)
        return [
            type: best.data._sourceFile == "db_overrides.json" ? "override" : "exact",
            confidence: scoreToConfidence(best.score),
            score: best.score,
            data: best.data,
            matches: alternatives.collect { it.data },
            scoredMatches: alternatives,
            rules: [],
            reason: best.reason
        ]
    }

    // 2. Match parcial (mesmo fabricante)
    def partial = db.devices.findAll { e ->
        getEntryProtocol(e) == protocol && e.manufacturer?.toLowerCase() == devData.manufacturer?.toLowerCase()
    }.collect { e -> scoreCandidate(e, devData, "partial") }
     .sort { a, b -> b.score <=> a.score ?: (a.data._sourcePriority ?: 999) <=> (b.data._sourcePriority ?: 999) }
    if (partial && partial.size() > 0) {
        return [type: "partial", confidence: 2, score: partial[0].score, data: partial[0].data, matches: partial.take(5).collect { it.data }, scoredMatches: partial.take(5), rules: [], reason: partial[0].reason]
    }

    // 3. Fallback por prefixo do fabricante
    if (protocol == "zigbee" && db.fallback_rules) {
        def prefix = db.fallback_rules
            .findAll { r -> r.condition == "manufacturer_prefix" && devData.manufacturer?.toLowerCase()?.startsWith(r.match?.toLowerCase()) }
            .sort { a, b -> (b.match?.size() ?: 0) <=> (a.match?.size() ?: 0) }
            .with { it ? it[0] : null }
        if (prefix) return [type: "prefix", confidence: 1, data: prefix, matches: [], rules: []]
    }

    // 4. Fallback por clusters
    if (protocol == "zigbee" && db.fallback_rules && (devData.inClusters || devData.outClusters)) {
        def clusters = normalizeClusters("${devData.inClusters},${devData.outClusters}")
        def clusterRules = []
        db.fallback_rules.findAll { it.condition == "cluster_present" }.each { rule ->
            if (clusters.any { c -> c.equalsIgnoreCase(rule.match) }) { clusterRules << rule }
        }
        if (clusterRules.size() > 0) {
            return [type: "cluster", confidence: 1, data: null, matches: [], rules: clusterRules]
        }
    }

    if (protocol == "zwave") {
        def zwaveFallback = getZwaveFallbackRecommendation(devData)
        if (zwaveFallback) {
            return [type: "zwave_fallback", confidence: 1, score: 60, data: zwaveFallback, matches: [], scoredMatches: [], rules: [], reason: "sugestao generica por tipo Z-Wave"]
        }
    }

    return [type: "none", confidence: 0, data: null, matches: [], rules: []]
}

def getZwaveFallbackRecommendation(Map devData) {
    def text = "${devData.deviceName ?: ''} ${devData.currentDriver ?: ''}".toLowerCase()
    def driver = ""
    def type = ""
    if (text.contains("dimmer")) { driver = "Generic Z-Wave Plus Dimmer"; type = "Z-Wave Dimmer" }
    else if (text.contains("switch") || text.contains("relay")) { driver = "Generic Z-Wave Plus Switch"; type = "Z-Wave Switch" }
    else if (text.contains("lock")) { driver = "Generic Z-Wave Lock"; type = "Z-Wave Lock" }
    else if (text.contains("thermostat")) { driver = "Generic Z-Wave Thermostat"; type = "Z-Wave Thermostat" }
    else if (text.contains("contact") || text.contains("door") || text.contains("window")) { driver = "Generic Z-Wave Contact Sensor"; type = "Z-Wave Contact Sensor" }
    else if (text.contains("motion")) { driver = "Generic Z-Wave Motion Sensor"; type = "Z-Wave Motion Sensor" }
    else if (text.contains("water") || text.contains("leak") || text.contains("moisture")) { driver = "Generic Z-Wave Water Sensor"; type = "Z-Wave Water Sensor" }
    if (!driver) return null
    return [protocol: "zwave", device_type: type, suggested_driver: driver, author: "Hubitat Built-in", hpm_available: false, url: "", driver_scope: "generic"]
}

def getEntryProtocol(Map entry) {
    return (entry.protocol ?: "zigbee").toLowerCase()
}

def isExactFingerprint(Map entry, Map devData, String protocol) {
    if (getEntryProtocol(entry) != protocol) return false
    if (protocol == "zwave") {
        return normalizeHexId(entry.manufacturer_id ?: entry.manufacturerId ?: entry.mfr) == devData.manufacturerId &&
            normalizeHexId(entry.product_type_id ?: entry.productTypeId ?: entry.prod) == devData.productTypeId &&
            normalizeHexId(entry.product_id ?: entry.productId ?: entry.model) == devData.productId
    }
    return entry.manufacturer?.toLowerCase() == devData.manufacturer?.toLowerCase() &&
        entry.model?.toLowerCase() == devData.model?.toLowerCase()
}

def scoreCandidate(Map entry, Map devData, String matchKind) {
    def score = matchKind == "exact" ? 100 : 45
    def reasons = []
    if (matchKind == "exact") reasons << "fingerprint exata"
    if (matchKind == "partial") reasons << "mesmo fabricante"

    switch(entry._sourceFile) {
        case "db_overrides.json": score += 120; reasons << "decisao manual"; break
        case "db_company_devices.json": score += 80; reasons << "base propria da empresa"; break
        case "db_zwave_devices.json": score += 70; reasons << "base Z-Wave curada"; break
        case "db_hpm_scraped.json": score += 25; reasons << "driver encontrado no HPM"; break
        case "db_zwave_hpm_scraped.json": score += 25; reasons << "driver encontrado no HPM"; break
        case "db_zigbee2mqtt_devices.json": score += 5; reasons << "fonte publica de identificacao"; break
        case "db_zwavejs_devices.json": score += 5; reasons << "fonte publica de identificacao"; break
        default: score += 50; reasons << "base curada"
    }

    if (entry.hpm_available == true) { score += 8; reasons << "disponivel no HPM" }
    if ((entry.driver_scope ?: "").toLowerCase() == "specific") { score += 10; reasons << "driver especifico" }
    if ((entry.driver_scope ?: "").toLowerCase() == "generic") { score -= 4; reasons << "driver generico" }

    def clusterScore = getClusterCompatibilityScore(entry, devData)
    score += clusterScore.score
    if (clusterScore.reason) reasons << clusterScore.reason

    if (devData.currentDriver && entry.suggested_driver &&
        devData.currentDriver.toLowerCase().trim() == entry.suggested_driver.toLowerCase().trim()) {
        score += 15
        reasons << "ja esta em uso"
    }

    return [data: entry, score: score, reason: reasons.join(", ")]
}

def getClusterCompatibilityScore(Map entry, Map devData) {
    if ((devData.protocol ?: "zigbee") != "zigbee") return [score: 0, reason: ""]
    def clusters = normalizeClusters("${devData.inClusters},${devData.outClusters}")
    def deviceClass = (entry.device_class ?: inferDeviceClass(entry)).toLowerCase()
    def classClusters = [
        "switch": ["0006"],
        "dimmer": ["0006", "0008"],
        "light": ["0006", "0008"],
        "color_light": ["0300"],
        "outlet": ["0006"],
        "power_outlet": ["0702", "0B04"],
        "temperature": ["0402"],
        "humidity": ["0405"],
        "motion": ["0406", "0500"],
        "contact": ["0500"],
        "lock": ["0101"],
        "shade": ["0102"],
        "thermostat": ["0201"],
        "tuya_proprietary": ["EF00"]
    ]
    def expected = classClusters[deviceClass] ?: []
    if (!expected) return [score: 0, reason: ""]
    def hits = expected.findAll { exp -> clusters.any { c -> c.equalsIgnoreCase(exp) } }
    if (hits) return [score: Math.min(20, hits.size() * 10), reason: "clusters compativeis"]
    return [score: -10, reason: "clusters pouco compativeis"]
}

def inferDeviceClass(Map entry) {
    def text = "${entry.device_class ?: ''} ${entry.device_type ?: ''} ${entry.suggested_driver ?: ''}".toLowerCase()
    if (text.contains("thermostat")) return "thermostat"
    if (text.contains("lock")) return "lock"
    if (text.contains("shade") || text.contains("cover") || text.contains("curtain") || text.contains("cortina")) return "shade"
    if (text.contains("rgb") || text.contains("color")) return "color_light"
    if (text.contains("dimmer")) return "dimmer"
    if (text.contains("light") || text.contains("bulb")) return "light"
    if (text.contains("outlet") || text.contains("plug") || text.contains("tomada")) return "outlet"
    if (text.contains("motion") || text.contains("presence") || text.contains("presen")) return "motion"
    if (text.contains("contact") || text.contains("door") || text.contains("window") || text.contains("porta")) return "contact"
    if (text.contains("humidity")) return "humidity"
    if (text.contains("temperature") || text.contains("temp")) return "temperature"
    if (text.contains("switch") || text.contains("relay")) return "switch"
    return ""
}

def scoreToConfidence(score) {
    if (score >= 180) return 3
    if (score >= 120) return 2
    if (score > 0) return 1
    return 0
}

def normalizeClusters(String rawClusters) {
    if (!rawClusters) return []
    return rawClusters
        .replaceAll("[\\[\\]]", "")
        .replaceAll("\\s", "")
        .split(",")
        .collect { it.replaceFirst("(?i)^0x", "").toUpperCase() }
        .findAll { it }
        .collect { it.padLeft(4, "0") }
}

def isDriverOptimal(String currentDriver, Map matchResult) {
    return getDriverStatus(currentDriver, matchResult) == "ideal"
}

/**
 * Retorna o status de classificação do driver atual:
 *  "ideal"      — driver atual é o recomendado #1
 *  "compatible" — driver atual é uma das alternativas conhecidas
 *  "suggestion" — existem sugestões mas o driver atual não é nenhuma delas
 *  "unknown"    — nenhum match no banco
 */
def getDriverStatus(String currentDriver, Map matchResult) {
    if (matchResult.confidence == 0) return "unknown"
    def suggested = matchResult.data?.suggested_driver ?: ""
    if (!suggested) return "unknown"

    // Ideal: driver atual é o recomendado #1
    if (isDriverNameMatch(currentDriver, suggested)) return "ideal"

    // Compatível: driver atual aparece em alguma alternativa
    def allDrivers = []
    if (matchResult.matches) {
        allDrivers.addAll(matchResult.matches.collect { it?.suggested_driver }.findAll { it })
    }
    if (matchResult.scoredMatches) {
        allDrivers.addAll(matchResult.scoredMatches.collect { it?.data?.suggested_driver }.findAll { it })
    }
    if (allDrivers.any { isDriverNameMatch(currentDriver, it) }) return "compatible"

    return "suggestion"
}

/**
 * Matching flexível de nomes de driver.
 * Ignora sufixos como (dev), (beta), v2 e faz contains bidirecional.
 */
def isDriverNameMatch(String a, String b) {
    if (!a || !b) return false
    def na = normalizeDriverName(a)
    def nb = normalizeDriverName(b)
    if (na == nb) return true
    if (na.length() > 4 && nb.length() > 4) {
        return na.contains(nb) || nb.contains(na)
    }
    return false
}

def normalizeDriverName(String name) {
    if (!name) return ""
    return name.toLowerCase().trim()
        .replaceAll(/\s*\(dev\)\s*/, "")
        .replaceAll(/\s*\(beta\)\s*/, "")
        .replaceAll(/\s*v\d+(\.\d+)?\s*$/, "")
        .replaceAll(/\s+/, " ")
        .trim()
}

def getConfidenceStars(int confidence) {
    switch(confidence) {
        case 3: return "⭐⭐⭐"
        case 2: return "⭐⭐"
        case 1: return "⭐"
        default: return "—"
    }
}

def htmlEscape(value) {
    return "${value ?: ''}"
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}

// ═══════════════════════════════════════════════════════
//  FORMATAÇÃO HTML
// ═══════════════════════════════════════════════════════

def formatDeviceInfo(Map d) {
    def protocolName = (d.protocol ?: "zigbee") == "zwave" ? "Z-Wave" : "Zigbee"
    def zwaveRows = (d.protocol == "zwave") ?
        "<tr style='background:#16213e;color:#eee;'><td style='padding:8px;border:1px solid #333;'><b>Z-Wave IDs</b></td><td style='padding:8px;border:1px solid #333;'>Mfr ${htmlEscape(d.manufacturerId)} / Type ${htmlEscape(d.productTypeId)} / Product ${htmlEscape(d.productId)}</td></tr>" : ""
    return "<table style='width:100%;border-collapse:collapse;font-family:monospace;font-size:14px;'>" +
        "<tr style='background:#1a1a2e;color:#e94560;'><td style='padding:8px;border:1px solid #333;'><b>Nome</b></td><td style='padding:8px;border:1px solid #333;'>${htmlEscape(d.deviceName)}</td></tr>" +
        "<tr style='background:#16213e;color:#eee;'><td style='padding:8px;border:1px solid #333;'><b>Protocolo</b></td><td style='padding:8px;border:1px solid #333;'>${protocolName}</td></tr>" +
        "<tr style='background:#16213e;color:#eee;'><td style='padding:8px;border:1px solid #333;'><b>Manufacturer</b></td><td style='padding:8px;border:1px solid #333;'>${htmlEscape(d.manufacturer)}</td></tr>" +
        "<tr style='background:#1a1a2e;color:#eee;'><td style='padding:8px;border:1px solid #333;'><b>Model</b></td><td style='padding:8px;border:1px solid #333;'>${htmlEscape(d.model)}</td></tr>" +
        zwaveRows +
        "<tr style='background:#16213e;color:#e94560;'><td style='padding:8px;border:1px solid #333;'><b>Driver Atual</b></td><td style='padding:8px;border:1px solid #333;'>${htmlEscape(d.currentDriver)}</td></tr>" +
        "</table>"
}

def formatMatchResult(Map result, Map devData) {
    def optimal = isDriverOptimal(devData.currentDriver, result)
    def stars = getConfidenceStars(result.confidence)

    switch(result.type) {
        case "override":
        case "exact":
            def m = result.data
            def driverName = htmlEscape(m.suggested_driver)
            def deviceType = htmlEscape(m.device_type)
            def author = htmlEscape(m.author)
            def hpmBadge = m.hpm_available ?
                "<span style='background:#27ae60;color:#fff;padding:4px 12px;border-radius:6px;font-size:13px;font-weight:bold;'>✅ Disponível no HPM</span>" :
                "<span style='background:#2980b9;color:#fff;padding:4px 12px;border-radius:6px;font-size:13px;font-weight:bold;'>📦 Driver Built-in (já incluso no Hubitat)</span>"
            def hpmHint = m.hpm_available ?
                "<p style='color:#7dcea0;font-size:12px;margin:4px 0 0 0;'>💡 Instale via Hubitat Package Manager para atualizações automáticas.</p>" :
                "<p style='color:#85c1e9;font-size:12px;margin:4px 0 0 0;'>ℹ️ Já vem instalado no Hubitat. Selecione na lista de drivers do dispositivo.</p>"
            def driverLink = (m.url && m.url.toString().trim()) ?
                "<p style='margin:8px 0 0 0;'><a href='${htmlEscape(m.url)}' target='_blank' style='color:#3498db;font-size:14px;text-decoration:none;'>🔗 Ver página do driver / GitHub do autor</a></p>" : ""
            def optBadge = optimal ?
                "<div style='background:#0a3d0a;border:1px solid #2ecc71;border-radius:8px;padding:8px;margin-top:8px;'><b style='color:#2ecc71;'>✅ Você já está usando o driver ideal!</b></div>" : ""
            def reason = result.reason ? "<p style='color:#bbb;font-size:13px;margin:6px 0;'><b>Motivo:</b> ${htmlEscape(result.reason)}</p>" : ""
            def score = result.score ? "<p style='color:#888;font-size:12px;margin:4px 0;'>Score: ${result.score}</p>" : ""
            def alternatives = formatAlternatives(result.scoredMatches ?: [])
            return "<div style='background:#0a3d0a;border:2px solid #27ae60;border-radius:12px;padding:16px;font-family:sans-serif;'>" +
                "<h3 style='color:#2ecc71;margin:0 0 8px 0;'>✅ Driver Encontrado! ${stars}</h3>" +
                "<p style='color:#eee;font-size:16px;margin:4px 0;'><b>Driver Recomendado:</b> ${driverName}</p>" +
                "<p style='color:#bbb;font-size:14px;margin:4px 0;'><b>Tipo:</b> ${deviceType} | <b>Autor:</b> ${author}</p>" +
                "<div style='margin:10px 0;padding:10px;background:#0d470d;border-radius:8px;'>" +
                "<p style='margin:0 0 4px 0;'>${hpmBadge}</p>${hpmHint}${driverLink}</div>" +
                "${reason}${score}${optBadge}${alternatives}</div>"

        case "partial":
            def list = result.matches.collect { m ->
                def mHpmBadge = m.hpm_available ?
                    "<span style='background:#27ae60;color:#fff;padding:1px 6px;border-radius:4px;font-size:11px;'>HPM</span>" :
                    "<span style='background:#2980b9;color:#fff;padding:1px 6px;border-radius:4px;font-size:11px;'>Built-in</span>"
                def mLink = (m.url && m.url.toString().trim()) ?
                    " <a href='${htmlEscape(m.url)}' target='_blank' style='color:#3498db;font-size:12px;text-decoration:none;'>🔗</a>" : ""
                "<li style='color:#eee;margin:4px 0;'><b>${htmlEscape(m.model)}</b> → ${htmlEscape(m.suggested_driver)} (${htmlEscape(m.author)}) ${mHpmBadge}${mLink}</li>"
            }.join("")
            return "<div style='background:#3d3a0a;border:2px solid #f39c12;border-radius:12px;padding:16px;font-family:sans-serif;'>" +
                "<h3 style='color:#f1c40f;margin:0 0 8px 0;'>⚠️ Match Parcial ${stars}</h3>" +
                "<p style='color:#eee;'>Modelo <b>${htmlEscape(devData.model)}</b> não encontrado, mas há drivers do mesmo fabricante (<b>${htmlEscape(devData.manufacturer)}</b>):</p>" +
                "<ul style='margin:8px 0;'>${list}</ul></div>"

        case "prefix":
            def r = result.data
            def link = r.url ? "<br/><a href='${htmlEscape(r.url)}' target='_blank' style='color:#3498db;'>🔗 Ver Referência</a>" : ""
            return "<div style='background:#3d2a0a;border:2px solid #e67e22;border-radius:12px;padding:16px;font-family:sans-serif;'>" +
                "<h3 style='color:#e67e22;margin:0 0 8px 0;'>⚠️ Recomendação por Fabricante ${stars}</h3>" +
                "<p style='color:#eee;font-size:14px;'>${htmlEscape(r.message)}</p>${link}</div>"

        case "cluster":
            def rList = result.rules.collect { r ->
                def dt = r.suggested_driver ? " → <b>${htmlEscape(r.suggested_driver)}</b>" : ""
                "<li style='color:#eee;'>${htmlEscape(r.message)}${dt}</li>"
            }.join("")
            return "<div style='background:#0a2a3d;border:2px solid #2980b9;border-radius:12px;padding:16px;font-family:sans-serif;'>" +
                "<h3 style='color:#3498db;margin:0 0 8px 0;'>🔎 Análise por Clusters ${stars}</h3>" +
                "<p style='color:#bbb;'>Dispositivo <b>${htmlEscape(devData.manufacturer)} / ${htmlEscape(devData.model)}</b> não no banco. Sugestões baseadas nos clusters:</p>" +
                "<ul style='margin:8px 0;'>${rList}</ul></div>"

        case "zwave_fallback":
            def m = result.data
            return "<div style='background:#0a2a3d;border:2px solid #2980b9;border-radius:12px;padding:16px;font-family:sans-serif;'>" +
                "<h3 style='color:#3498db;margin:0 0 8px 0;'>🔎 Sugestão Z-Wave ${stars}</h3>" +
                "<p style='color:#eee;font-size:16px;margin:4px 0;'><b>Driver Recomendado:</b> ${htmlEscape(m.suggested_driver)}</p>" +
                "<p style='color:#bbb;font-size:13px;'>Nao encontramos uma fingerprint exata no banco, entao esta e uma sugestao generica baseada no tipo aparente do dispositivo.</p></div>"

        default:
            return "<div style='background:#3d0a0a;border:2px solid #c0392b;border-radius:12px;padding:16px;font-family:sans-serif;'>" +
                "<h3 style='color:#e74c3c;margin:0 0 8px 0;'>❌ Dispositivo Não Reconhecido</h3>" +
                "<p style='color:#eee;'>Sem recomendação para: <b>${htmlEscape(devData.manufacturer)} / ${htmlEscape(devData.model)}</b></p>" +
                "<p style='color:#bbb;font-size:13px;'>Procure no <a href='https://community.hubitat.com/' target='_blank' style='color:#3498db;'>Fórum</a> ou no HPM.</p></div>"
    }
}

def formatAlternatives(List scoredMatches) {
    if (!scoredMatches || scoredMatches.size() == 0) return ""
    def items = scoredMatches.take(4).collect { item ->
        def m = item.data
        def altHpmBadge = m.hpm_available ?
            "<span style='background:#27ae60;color:#fff;padding:1px 6px;border-radius:4px;font-size:11px;'>HPM</span>" :
            "<span style='background:#2980b9;color:#fff;padding:1px 6px;border-radius:4px;font-size:11px;'>Built-in</span>"
        def altLink = (m.url && m.url.toString().trim()) ?
            " <a href='${htmlEscape(m.url)}' target='_blank' style='color:#3498db;font-size:12px;text-decoration:none;'>🔗</a>" : ""
        "<li style='color:#bbb;font-size:13px;margin:6px 0;'>" +
            "<b>${htmlEscape(m.suggested_driver)}</b> (${htmlEscape(m.author)}) ${altHpmBadge}${altLink}" +
            "<br/><span style='color:#666;font-size:11px;'>Score: ${item.score}</span></li>"
    }.join("")
    return "<div style='margin-top:10px;border-top:1px solid #1f7a3a;padding-top:8px;'>" +
        "<p style='color:#aaa;font-size:13px;margin:0 0 4px 0;'><b>Alternativas se o recomendado nao funcionar:</b></p>" +
        "<ul style='margin:4px 0 0 18px;padding:0;'>${items}</ul></div>"
}

def formatScanAllTable(List results) {
    def rows = ""
    results.each { r ->
        def d = r.devData
        def m = r.match
        def status = r.status ?: "unknown"
        def suggested = htmlEscape(m.data?.suggested_driver ?: (m.rules?.size() > 0 ? m.rules[0].suggested_driver ?: "Ver clusters" : "—"))
        def protocol = (d.protocol ?: "zigbee") == "zwave" ? "Z-Wave" : "Zigbee"

        // Cores e ícones baseados no status de 4 estados
        def bgColor = "#1a1a2e"
        def statusIcon = "—"
        switch(status) {
            case "ideal":      bgColor = "#0a3d0a"; statusIcon = "✅"; break
            case "compatible": bgColor = "#0a2a3d"; statusIcon = "🔵"; break
            case "suggestion": bgColor = "#3d3a0a"; statusIcon = "🟡"; break
            case "unknown":    bgColor = "#3d0a0a"; statusIcon = "🔴"; break
        }

        def stars = getConfidenceStars(m.confidence)

        // Contagem de alternativas
        def altCount = (m.scoredMatches?.size() ?: 0) + (m.matches?.size() ?: 0)
        if (m.scoredMatches) altCount = m.scoredMatches.size()
        else if (m.matches) altCount = m.matches.size()
        def altLabel = altCount > 0 ? " <span style='color:#aaa;font-size:11px;'>+${altCount} outros</span>" : ""

        def hpmCell = ""
        def linkCell = ""
        if (m.confidence > 0 && m.data) {
            hpmCell = m.data.hpm_available ?
                "<span style='background:#27ae60;color:#fff;padding:1px 6px;border-radius:4px;font-size:11px;'>HPM</span>" :
                "<span style='background:#2980b9;color:#fff;padding:1px 6px;border-radius:4px;font-size:11px;'>Built-in</span>"
            linkCell = (m.data.url && m.data.url.toString().trim()) ?
                "<a href='${htmlEscape(m.data.url)}' target='_blank' style='color:#3498db;text-decoration:none;'>🔗</a>" : "—"
        } else {
            hpmCell = "—"
            linkCell = "—"
        }
        rows += "<tr style='background:${bgColor};'>" +
            "<td style='padding:6px 8px;border:1px solid #333;color:#eee;'>${htmlEscape(d.deviceName)}</td>" +
            "<td style='padding:6px 8px;border:1px solid #333;color:#bbb;'>${protocol}</td>" +
            "<td style='padding:6px 8px;border:1px solid #333;color:#bbb;'>${htmlEscape(d.manufacturer)}</td>" +
            "<td style='padding:6px 8px;border:1px solid #333;color:#bbb;'>${htmlEscape(d.model)}</td>" +
            "<td style='padding:6px 8px;border:1px solid #333;color:#888;'>${htmlEscape(d.currentDriver)}</td>" +
            "<td style='padding:6px 8px;border:1px solid #333;color:#eee;'><b>${suggested}</b>${altLabel}</td>" +
            "<td style='padding:6px 8px;border:1px solid #333;text-align:center;'>${hpmCell}</td>" +
            "<td style='padding:6px 8px;border:1px solid #333;text-align:center;'>${linkCell}</td>" +
            "<td style='padding:6px 8px;border:1px solid #333;text-align:center;'>${stars}</td>" +
            "<td style='padding:6px 8px;border:1px solid #333;text-align:center;'>${statusIcon}</td>" +
            "</tr>"
    }
    return "<div style='overflow-x:auto;'><table style='width:100%;border-collapse:collapse;font-family:sans-serif;font-size:13px;'>" +
        "<tr style='background:#0f3460;'>" +
        "<th style='padding:8px;border:1px solid #333;color:#e94560;text-align:left;'>Dispositivo</th>" +
        "<th style='padding:8px;border:1px solid #333;color:#e94560;text-align:left;'>Protocolo</th>" +
        "<th style='padding:8px;border:1px solid #333;color:#e94560;text-align:left;'>Fabricante</th>" +
        "<th style='padding:8px;border:1px solid #333;color:#e94560;text-align:left;'>Modelo</th>" +
        "<th style='padding:8px;border:1px solid #333;color:#e94560;text-align:left;'>Driver Atual</th>" +
        "<th style='padding:8px;border:1px solid #333;color:#e94560;text-align:left;'>Recomendado</th>" +
        "<th style='padding:8px;border:1px solid #333;color:#e94560;text-align:center;'>HPM</th>" +
        "<th style='padding:8px;border:1px solid #333;color:#e94560;text-align:center;'>Link</th>" +
        "<th style='padding:8px;border:1px solid #333;color:#e94560;text-align:center;'>Confiança</th>" +
        "<th style='padding:8px;border:1px solid #333;color:#e94560;text-align:center;'>Status</th>" +
        "</tr>${rows}</table></div>"
}

def formatError(String msg) {
    return "<div style='background:#3d0a0a;border:2px solid #e74c3c;border-radius:12px;padding:16px;font-family:sans-serif;'>" +
        "<h3 style='color:#e74c3c;margin:0 0 8px 0;'>⚠️ Erro</h3>" +
        "<p style='color:#eee;'>${htmlEscape(msg)}</p></div>"
}

// ═══════════════════════════════════════════════════════
//  CICLO DE VIDA
// ═══════════════════════════════════════════════════════

def installed() { log.info "Hubitat Driver Finder v${APP_VERSION} instalado."; initialize() }
def updated() { log.info "Hubitat Driver Finder v${APP_VERSION} atualizado."; initialize() }
def initialize() { log.info "Hubitat Driver Finder v${APP_VERSION} inicializado." }
def uninstalled() { log.info "Hubitat Driver Finder desinstalado." }
