/**
 * Ghost Transpiler — Configuration & Constants
 */

const path = require('path');

// ---------------------------------------------------------------------------
// Config
// ---------------------------------------------------------------------------

const CONFIG = {
    input:      process.env.GHOST_INPUT      || path.join(process.cwd(), 'ghost-models'),
    outputKt:   process.env.GHOST_OUTPUT_KT  || path.join(process.cwd(), 'src/commonMain/kotlin/com/ghost/serialization/generated'),
    outputTs:   process.env.GHOST_OUTPUT_TS  || path.join(process.cwd(), 'src/ghost-generated-types'),
    outputWasmKt: process.env.GHOST_OUTPUT_WASM_KT || null, // resolved below
    libSrc:     process.env.GHOST_LIB_SRC    || null,
    libDest:    process.env.GHOST_LIB_DEST   || null,
};

// Derive wasmJsMain/generated from commonMain path if not explicit
if (!CONFIG.outputWasmKt) {
    CONFIG.outputWasmKt = path.resolve(
        CONFIG.outputKt,
        '../../../../wasmJsMain/kotlin/com/ghost/serialization/generated'
    );
}

// ---------------------------------------------------------------------------
// Logger
// ---------------------------------------------------------------------------

const LOG = {
    info:    (m) => console.log(`\x1b[36m[Ghost]\x1b[0m ${m}`),
    warn:    (m) => console.warn(`\x1b[33m[Ghost] Warning:\x1b[0m ${m}`),
    error:   (m) => console.error(`\x1b[31m[Ghost] Error:\x1b[0m ${m}`),
    success: (m) => console.log(`\x1b[32m[Ghost]\x1b[0m ${m}`)
};

// ---------------------------------------------------------------------------
// Kotlin Keywords
// ---------------------------------------------------------------------------

const KOTLIN_KEYWORDS = new Set([
    'as','break','class','continue','do','else','false','for','fun','if','in',
    'interface','is','null','object','package','return','super','this','throw',
    'true','try','typealias','typeof','val','var','when','while','by','catch',
    'constructor','delegate','dynamic','field','file','finally','get','import',
    'init','param','property','receiver','set','setparam','value','where'
]);

const ktIdent = (n) => KOTLIN_KEYWORDS.has(n) ? `\`${n}\`` : n;

module.exports = { CONFIG, LOG, KOTLIN_KEYWORDS, ktIdent };
