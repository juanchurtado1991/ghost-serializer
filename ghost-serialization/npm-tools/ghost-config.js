const fs = require('fs');
const path = require('path');
const os = require('os');

const LOG = {
    info: (m) => console.log(`\x1b[34m[Ghost]\x1b[0m ${m}`),
    success: (m) => console.log(`\x1b[32m[Ghost]\x1b[0m ${m}`),
    warn: (m) => console.log(`\x1b[33m[Ghost]\x1b[0m Warning: ${m}`),
    error: (m) => console.error(`\x1b[31m[Ghost]\x1b[0m Error: ${m}`),
    fast: (m) => console.log(`\x1b[36m[Ghost]\x1b[0m   ⚡ Fast Path (Single-Crossing): ${m}`),
    safe: (m) => console.log(`\x1b[35m[Ghost]\x1b[0m   🛡️ Safe Path (Multi-Crossing): ${m}`)
};

const GHOST_HOME = path.join(os.homedir(), '.ghost');
const TEST_MODE = process.env.GHOST_TEST_MODE === '1';

if (TEST_MODE) LOG.warn('TEST MODE enabled — forcing standalone build to ~/.ghost');

function discoverPath(pattern) {
    if (TEST_MODE) return null; // Skip discovery in test mode
    const searchRoots = [
        process.cwd(),
        path.join(process.cwd(), '..')
    ];
    for (const root of searchRoots) {
        if (!fs.existsSync(root)) continue;
        try {
            // Check if this looks like a Gradle project first
            const isGradle = fs.existsSync(path.join(root, 'build.gradle.kts')) || 
                             fs.existsSync(path.join(root, 'build.gradle')) ||
                             fs.existsSync(path.join(root, 'settings.gradle.kts'));
            
            if (!isGradle) continue;

            const dirs = fs.readdirSync(root);
            for (const dir of dirs) {
                const fullPath = path.join(root, dir, pattern);
                if (fs.existsSync(fullPath)) return fullPath;
            }
        } catch {}
    }
    return null;
}

const autoCommon = discoverPath('src/commonMain/kotlin');
const autoWasm = discoverPath('src/wasmJsMain/kotlin');

const localConfigPath = path.join(process.cwd(), 'ghost.config.json');
let localConfig = {};
try {
    if (fs.existsSync(localConfigPath)) {
        localConfig = JSON.parse(fs.readFileSync(localConfigPath, 'utf8'));
        LOG.info('Using configuration from ghost.config.json');
    }
} catch {}

const CONFIG = {
    input: process.env.GHOST_INPUT || localConfig.input || './src/ghost-models',
    outputKt: process.env.GHOST_OUTPUT_KT || localConfig.outputKt || autoCommon || null,
    outputWasmKt: process.env.GHOST_OUTPUT_WASM_KT || localConfig.outputWasmKt || autoWasm || null,
    outputTs: process.env.GHOST_OUTPUT_TS || localConfig.outputTs || './src/ghost-generated-types'
};

// Determine if we need standalone mode
const standaloneMode = TEST_MODE || localConfig.standalone || (!CONFIG.outputKt && !CONFIG.outputWasmKt);

if (standaloneMode && !TEST_MODE) {
    LOG.info('No Kotlin project detected. Activating Standalone Mode (Invisible Bridge).');
} else if (!standaloneMode && autoCommon) {
    LOG.info(`Auto-discovered Kotlin project at: ${autoCommon}`);
}

const ktIdent = (id) => {
    const keywords = new Set(['package', 'import', 'class', 'interface', 'fun', 'val', 'var', 'object', 'when', 'is', 'in', 'this', 'super', 'typealias', 'as', 'break', 'continue', 'do', 'else', 'for', 'if', 'null', 'return', 'throw', 'try', 'while', 'value']);
    return keywords.has(id) ? `\`${id}\`` : id;
};

module.exports = { CONFIG, LOG, ktIdent, GHOST_HOME, standaloneMode, TEST_MODE };
