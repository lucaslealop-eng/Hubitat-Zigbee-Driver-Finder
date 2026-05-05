/**
 * ========================================================
 *  Hubitat Zigbee Driver Finder
 * ========================================================
 *  SmartApp para Hubitat Elevation
 *
 *  Identifica dispositivos Zigbee no hub e recomenda o
 *  driver mais adequado com base em manufacturer, model
 *  e clusters reportados.
 *
 *  Autor: Lucas (Hubitat Agent Project)
 *  Versão: 1.0.0
 *  Data: 2026-05-05
 *
 *  O banco de dados de dispositivos é carregado de um
 *  arquivo JSON remoto para permitir atualizações sem
 *  alterar o código do App.
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

// ─── Preferências Globais ──────────────────────────────
preferences {
    page(name: "mainPage")
    page(name: "resultPage")
}

// ─── Constantes ────────────────────────────────────────
@Field static String DB_BASE_URL = "https://raw.githubusercontent.com/lucaslealop-eng/Hubitat-Zigbee-Driver-Finder/main/data/"
@Field static List DB_FILES = ["db_tuya.json", "db_xiaomi_aqara.json", "db_brands.json", "db_other_brands.json", "db_misc_zigbee.json", "db_hpm_scraped.json"]
@Field static String DB_INDEX_URL = "https://raw.githubusercontent.com/lucaslealop-eng/Hubitat-Zigbee-Driver-Finder/main/data/zigbee_driver_db.json"
@Field static String APP_VERSION = "2.0.0"

// ═══════════════════════════════════════════════════════
//  PÁGINAS DA INTERFACE
// ═══════════════════════════════════════════════════════

def mainPage() {
    dynamicPage(name: "mainPage", title: "", install: true, uninstall: true) {
        section("<h2>🔍 Zigbee Driver Finder v${APP_VERSION}</h2>") {
            paragraph "<i>Selecione um dispositivo Zigbee para descobrir qual driver utilizar.</i>"
        }
        section("Selecione o Dispositivo") {
            input name: "selectedDevice", type: "capability.*", title: "Dispositivo Zigbee", required: true, submitOnChange: true
        }

        if (selectedDevice) {
            // Extrai os dados Zigbee do dispositivo selecionado
            def devData = getDeviceZigbeeData(selectedDevice)
            
            section("<h3>📋 Informações do Dispositivo</h3>") {
                paragraph formatDeviceInfo(devData)
            }

            // Tenta buscar o banco de dados e fazer o match
            section("<h3>🎯 Recomendação de Driver</h3>") {
                paragraph "<i>Buscando no banco de dados...</i>"
                // Disparamos a busca assíncrona ao carregar a página
                fetchDriverDatabase(devData)
            }

            // Exibe o resultado se já tivermos processado
            if (state.lastRecommendation) {
                section("") {
                    paragraph state.lastRecommendation
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
//  EXTRAÇÃO DE DADOS DO DISPOSITIVO
// ═══════════════════════════════════════════════════════

/**
 * Extrai as informações Zigbee relevantes de um dispositivo.
 * Usa device.getData() para obter manufacturer, model, etc.
 */
def getDeviceZigbeeData(dev) {
    def data = [:]
    
    try {
        def rawData = dev.getData()
        data.manufacturer = rawData?.manufacturer ?: "Desconhecido"
        data.model = rawData?.model ?: "Desconhecido"
        data.inClusters = rawData?.inClusters ?: ""
        data.outClusters = rawData?.outClusters ?: ""
        data.profileId = rawData?.profileId ?: ""
        data.endpointId = rawData?.endpointId ?: ""
    } catch (e) {
        log.error "Erro ao ler dados do dispositivo: ${e.message}"
        data.manufacturer = "Erro"
        data.model = "Erro"
        data.inClusters = ""
        data.outClusters = ""
    }

    data.deviceName = dev.displayName ?: "Sem nome"
    data.deviceId = dev.id
    data.currentDriver = dev.typeName ?: "Nenhum"

    return data
}

/**
 * Formata as informações do dispositivo em HTML para exibição.
 */
def formatDeviceInfo(Map devData) {
    def html = """
    <table style='width:100%; border-collapse:collapse; font-family:monospace; font-size:14px;'>
        <tr style='background:#1a1a2e; color:#e94560;'>
            <td style='padding:8px; border:1px solid #333;'><b>Nome</b></td>
            <td style='padding:8px; border:1px solid #333;'>${devData.deviceName}</td>
        </tr>
        <tr style='background:#16213e; color:#eee;'>
            <td style='padding:8px; border:1px solid #333;'><b>Manufacturer</b></td>
            <td style='padding:8px; border:1px solid #333;'>${devData.manufacturer}</td>
        </tr>
        <tr style='background:#1a1a2e; color:#eee;'>
            <td style='padding:8px; border:1px solid #333;'><b>Model</b></td>
            <td style='padding:8px; border:1px solid #333;'>${devData.model}</td>
        </tr>
        <tr style='background:#16213e; color:#eee;'>
            <td style='padding:8px; border:1px solid #333;'><b>Profile ID</b></td>
            <td style='padding:8px; border:1px solid #333;'>${devData.profileId}</td>
        </tr>
        <tr style='background:#1a1a2e; color:#eee;'>
            <td style='padding:8px; border:1px solid #333;'><b>In Clusters</b></td>
            <td style='padding:8px; border:1px solid #333;'>${devData.inClusters}</td>
        </tr>
        <tr style='background:#16213e; color:#eee;'>
            <td style='padding:8px; border:1px solid #333;'><b>Out Clusters</b></td>
            <td style='padding:8px; border:1px solid #333;'>${devData.outClusters}</td>
        </tr>
        <tr style='background:#1a1a2e; color:#0f3460;'>
            <td style='padding:8px; border:1px solid #333; color:#e94560;'><b>Driver Atual</b></td>
            <td style='padding:8px; border:1px solid #333; color:#e94560;'>${devData.currentDriver}</td>
        </tr>
    </table>
    """
    return html
}

// ═══════════════════════════════════════════════════════
//  BUSCA NO BANCO DE DADOS REMOTO
// ═══════════════════════════════════════════════════════

/**
 * Faz o download assíncrono do banco de dados JSON
 * e processa o resultado no callback.
 */
def fetchDriverDatabase(Map devData) {
    state.currentDevData = devData
    state.lastRecommendation = null

    try {
        // 1. Carregar o índice (fallback rules)
        def indexDb = [fallback_rules: [], devices: []]
        httpGet([uri: DB_INDEX_URL, contentType: "application/json", timeout: 15]) { resp ->
            if (resp.status == 200) {
                indexDb = new JsonSlurper().parseText(resp.data.text)
            }
        }

        // 2. Carregar cada arquivo de dispositivos e consolidar
        def allDevices = []
        DB_FILES.each { fileName ->
            try {
                httpGet([uri: DB_BASE_URL + fileName, contentType: "application/json", timeout: 10]) { resp ->
                    if (resp.status == 200) {
                        def partial = new JsonSlurper().parseText(resp.data.text)
                        if (partial.devices) allDevices.addAll(partial.devices)
                    }
                }
            } catch (e) {
                log.warn "Falha ao carregar ${fileName}: ${e.message}"
            }
        }

        // 3. Montar o banco consolidado
        def db = [devices: allDevices, fallback_rules: indexDb.fallback_rules ?: []]
        log.info "📦 Banco de dados carregado: ${allDevices.size()} dispositivos, ${db.fallback_rules.size()} regras de fallback"
        processMatch(db, devData)

    } catch (e) {
        log.warn "Falha na busca remota do DB: ${e.message}. Usando fallback local."
        processMatchFallbackOnly(devData)
    }
}

// ═══════════════════════════════════════════════════════
//  LÓGICA DE CORRESPONDÊNCIA (MATCHING)
// ═══════════════════════════════════════════════════════

/**
 * Tenta encontrar o dispositivo no banco de dados.
 * Se não encontrar, aplica as regras de fallback.
 */
def processMatch(Map db, Map devData) {
    def matched = null

    // 1. Match exato por manufacturer + model
    if (db.devices) {
        matched = db.devices.find { entry ->
            entry.manufacturer?.toLowerCase() == devData.manufacturer?.toLowerCase() &&
            entry.model?.toLowerCase() == devData.model?.toLowerCase()
        }
    }

    if (matched) {
        state.lastRecommendation = formatExactMatch(matched)
        log.info "✅ Match exato encontrado: ${matched.suggested_driver}"
        return
    }

    // 2. Match parcial por manufacturer apenas
    def partialMatches = db.devices?.findAll { entry ->
        entry.manufacturer?.toLowerCase() == devData.manufacturer?.toLowerCase()
    }

    if (partialMatches && partialMatches.size() > 0) {
        state.lastRecommendation = formatPartialMatch(devData, partialMatches)
        log.info "⚠️ Match parcial: mesmo fabricante, modelo diferente."
        return
    }

    // 3. Fallback por prefixo do fabricante
    if (db.fallback_rules) {
        def prefixRule = db.fallback_rules.find { rule ->
            rule.condition == "manufacturer_prefix" &&
            devData.manufacturer?.startsWith(rule.match)
        }
        if (prefixRule) {
            state.lastRecommendation = formatFallbackRule(prefixRule)
            log.info "⚠️ Fallback por fabricante: ${prefixRule.message}"
            return
        }
    }

    // 4. Fallback por clusters presentes
    if (db.fallback_rules && devData.inClusters) {
        def clusters = devData.inClusters.replaceAll("\\s", "").split(",")
        def clusterMatches = []

        db.fallback_rules.findAll { it.condition == "cluster_present" }.each { rule ->
            if (clusters.any { c -> c.equalsIgnoreCase(rule.match) }) {
                clusterMatches << rule
            }
        }

        if (clusterMatches.size() > 0) {
            state.lastRecommendation = formatClusterFallback(devData, clusterMatches)
            log.info "⚠️ Fallback por clusters: ${clusterMatches.size()} sugestões."
            return
        }
    }

    // 5. Nenhum match encontrado
    state.lastRecommendation = formatNoMatch(devData)
    log.warn "❌ Nenhum match encontrado para ${devData.manufacturer} / ${devData.model}"
}

/**
 * Quando o banco remoto não está disponível, faz análise
 * local baseada apenas nos clusters do dispositivo.
 */
def processMatchFallbackOnly(Map devData) {
    def suggestions = []

    if (!devData.inClusters) {
        state.lastRecommendation = formatError("Não foi possível acessar o banco de dados remoto e o dispositivo não reportou clusters. Tente novamente mais tarde.")
        return
    }

    def clusters = devData.inClusters.replaceAll("\\s", "").split(",")

    // Regras hardcoded de emergência
    def localRules = [
        ["0006", "Generic Zigbee Switch", "O dispositivo suporta On/Off."],
        ["0008", "Generic Zigbee Dimmer", "O dispositivo suporta Level Control."],
        ["0300", "Generic Zigbee RGBW Light", "O dispositivo suporta Color Control."],
        ["0402", "Generic Zigbee Temperature Sensor", "O dispositivo suporta Temperature Measurement."],
        ["0405", "Generic Zigbee Humidity Sensor", "O dispositivo suporta Relative Humidity."],
        ["0500", "Generic Zigbee Motion Sensor", "O dispositivo suporta IAS Zone (Movimento/Contato)."],
        ["EF00", "⚠️ Tuya Proprietário", "Cluster Tuya 0xEF00 detectado. Drivers genéricos NÃO funcionarão. Procure no HPM por 'Tuya'."]
    ]

    localRules.each { rule ->
        if (clusters.any { c -> c.equalsIgnoreCase(rule[0]) }) {
            suggestions << [driver: rule[1], message: rule[2]]
        }
    }

    if (suggestions.size() > 0) {
        state.lastRecommendation = formatLocalFallback(devData, suggestions)
    } else {
        state.lastRecommendation = formatError("Modo offline: Nenhuma sugestão disponível para os clusters reportados.")
    }
}

// ═══════════════════════════════════════════════════════
//  FORMATAÇÃO HTML DOS RESULTADOS
// ═══════════════════════════════════════════════════════

def formatExactMatch(Map match) {
    def hpmBadge = match.hpm_available ?
        "<span style='background:#27ae60; color:#fff; padding:2px 8px; border-radius:4px; font-size:12px;'>✅ Disponível no HPM</span>" :
        "<span style='background:#2980b9; color:#fff; padding:2px 8px; border-radius:4px; font-size:12px;'>📦 Driver Built-in</span>"

    def linkHtml = match.url ? "<br/><a href='${match.url}' target='_blank' style='color:#3498db;'>🔗 Ver no Fórum da Comunidade</a>" : ""

    return """
    <div style='background:#0a3d0a; border:2px solid #27ae60; border-radius:12px; padding:16px; font-family:sans-serif;'>
        <h3 style='color:#2ecc71; margin:0 0 8px 0;'>✅ Driver Encontrado!</h3>
        <p style='color:#eee; font-size:16px; margin:4px 0;'><b>Driver Recomendado:</b> ${match.suggested_driver}</p>
        <p style='color:#bbb; font-size:14px; margin:4px 0;'><b>Tipo de Dispositivo:</b> ${match.device_type}</p>
        <p style='color:#bbb; font-size:14px; margin:4px 0;'><b>Autor:</b> ${match.author}</p>
        <p style='margin:8px 0;'>${hpmBadge}</p>
        ${linkHtml}
        <hr style='border-color:#333; margin:12px 0;'/>
        <p style='color:#888; font-size:12px;'>
            <b>Como instalar:</b> ${match.hpm_available ? "Abra o <b>Hubitat Package Manager</b>, clique em <b>Install</b>, procure por '<b>${match.suggested_driver}</b>' e instale. Depois, vá ao dispositivo e troque o driver." : "Vá ao dispositivo, clique em <b>Type</b> e selecione '<b>${match.suggested_driver}</b>' na lista de drivers built-in."}
        </p>
    </div>
    """
}

def formatPartialMatch(Map devData, List matches) {
    def matchList = matches.collect { m ->
        "<li style='color:#eee;'><b>${m.model}</b> → ${m.suggested_driver} (por ${m.author})</li>"
    }.join("\n")

    return """
    <div style='background:#3d3a0a; border:2px solid #f39c12; border-radius:12px; padding:16px; font-family:sans-serif;'>
        <h3 style='color:#f1c40f; margin:0 0 8px 0;'>⚠️ Match Parcial</h3>
        <p style='color:#eee;'>O modelo exato <b>${devData.model}</b> não foi encontrado, mas encontramos outros dispositivos do mesmo fabricante (<b>${devData.manufacturer}</b>):</p>
        <ul style='margin:8px 0;'>${matchList}</ul>
        <p style='color:#bbb; font-size:13px;'>Tente um dos drivers acima — fabricantes costumam reutilizar o mesmo firmware entre modelos similares.</p>
    </div>
    """
}

def formatFallbackRule(Map rule) {
    def linkHtml = rule.url ? "<br/><a href='${rule.url}' target='_blank' style='color:#3498db;'>🔗 Ver Referência</a>" : ""

    return """
    <div style='background:#3d2a0a; border:2px solid #e67e22; border-radius:12px; padding:16px; font-family:sans-serif;'>
        <h3 style='color:#e67e22; margin:0 0 8px 0;'>⚠️ Recomendação por Fabricante</h3>
        <p style='color:#eee; font-size:14px;'>${rule.message}</p>
        ${rule.suggested_driver ? "<p style='color:#eee;'><b>Driver sugerido:</b> ${rule.suggested_driver}</p>" : ""}
        ${linkHtml}
    </div>
    """
}

def formatClusterFallback(Map devData, List clusterRules) {
    def ruleList = clusterRules.collect { r ->
        def driverText = r.suggested_driver ? " → <b>${r.suggested_driver}</b>" : ""
        "<li style='color:#eee;'>${r.message}${driverText}</li>"
    }.join("\n")

    return """
    <div style='background:#0a2a3d; border:2px solid #2980b9; border-radius:12px; padding:16px; font-family:sans-serif;'>
        <h3 style='color:#3498db; margin:0 0 8px 0;'>🔎 Análise por Clusters</h3>
        <p style='color:#bbb;'>O dispositivo <b>${devData.manufacturer} / ${devData.model}</b> não foi encontrado no banco de dados, mas analisando seus clusters Zigbee, sugerimos:</p>
        <ul style='margin:8px 0;'>${ruleList}</ul>
        <p style='color:#888; font-size:12px;'>Dica: Comece pelo driver mais específico (ex: RGBW se tiver Color Control) e teste.</p>
    </div>
    """
}

def formatLocalFallback(Map devData, List suggestions) {
    def suggList = suggestions.collect { s ->
        "<li style='color:#eee;'><b>${s.driver}</b> — ${s.message}</li>"
    }.join("\n")

    return """
    <div style='background:#2a0a3d; border:2px solid #8e44ad; border-radius:12px; padding:16px; font-family:sans-serif;'>
        <h3 style='color:#9b59b6; margin:0 0 8px 0;'>📡 Modo Offline — Análise Local</h3>
        <p style='color:#bbb;'>Não foi possível acessar o banco de dados remoto. Baseado nos clusters do dispositivo:</p>
        <ul style='margin:8px 0;'>${suggList}</ul>
    </div>
    """
}

def formatNoMatch(Map devData) {
    return """
    <div style='background:#3d0a0a; border:2px solid #c0392b; border-radius:12px; padding:16px; font-family:sans-serif;'>
        <h3 style='color:#e74c3c; margin:0 0 8px 0;'>❌ Dispositivo Não Reconhecido</h3>
        <p style='color:#eee;'>Não encontramos uma recomendação para:</p>
        <p style='color:#eee; font-family:monospace;'>Manufacturer: <b>${devData.manufacturer}</b><br/>Model: <b>${devData.model}</b></p>
        <hr style='border-color:#333;'/>
        <p style='color:#bbb; font-size:13px;'>
            <b>O que fazer agora:</b><br/>
            1. Procure no <a href='https://community.hubitat.com/' target='_blank' style='color:#3498db;'>Fórum da Comunidade</a> pelo modelo: <b>${devData.model}</b><br/>
            2. Verifique o <b>Hubitat Package Manager</b> (HPM) por drivers do fabricante.<br/>
            3. Se for um dispositivo simples (switch, sensor), tente um driver <b>Generic Zigbee</b> compatível com os clusters acima.
        </p>
    </div>
    """
}

def formatError(String msg) {
    return """
    <div style='background:#3d0a0a; border:2px solid #e74c3c; border-radius:12px; padding:16px; font-family:sans-serif;'>
        <h3 style='color:#e74c3c; margin:0 0 8px 0;'>⚠️ Erro</h3>
        <p style='color:#eee;'>${msg}</p>
    </div>
    """
}

// ═══════════════════════════════════════════════════════
//  MÉTODOS DE CICLO DE VIDA
// ═══════════════════════════════════════════════════════

def installed() {
    log.info "Zigbee Driver Finder instalado."
    initialize()
}

def updated() {
    log.info "Zigbee Driver Finder atualizado."
    initialize()
}

def initialize() {
    log.info "Zigbee Driver Finder v${APP_VERSION} inicializado."
}

def uninstalled() {
    log.info "Zigbee Driver Finder desinstalado."
}
