#!/usr/bin/env node
const fs = require('fs');
const path = require('path');
const { LOG, CONFIG: DEFAULT_CONFIG, standaloneMode, GHOST_HOME } = require('./ghost-config');
const { parseFields, registerEnum } = require('./ghost-parser');
const { genKotlinClass, genKotlinEnum, genJsExtension } = require('./ghost-codegen');

async function runTranspiler(overrides = {}) {
    const cfg = { ...DEFAULT_CONFIG, ...overrides };
    LOG.info('Ghost Turbo-Bridge Transpiler starting...');
    const allModels = [];
    const allEnums = [];

    if (!fs.existsSync(cfg.input)) {
        fs.mkdirSync(cfg.input, { recursive: true });
        LOG.success(`Created models directory at ${cfg.input}`);
        LOG.info('Please add your TypeScript interfaces (.ts) to this directory to start.');
        return;
    }

    const files = fs.readdirSync(cfg.input).filter(f => f.endsWith('.ts'));
    if (files.length === 0) {
        LOG.warn(`No TypeScript models found in ${cfg.input}`);
        LOG.info('Add at least one .ts file with an "export interface" to generate the engine.');
        return;
    }

    // In standalone mode, output to a temp directory first
    let effectiveKt = cfg.outputKt;
    let effectiveWasmKt = cfg.outputWasmKt;

    if (standaloneMode && !overrides.outputKt) {
        const tempKt = path.join(GHOST_HOME, 'standalone-gen', 'commonMain');
        const tempWasmKt = path.join(GHOST_HOME, 'standalone-gen', 'wasmJsMain');
        fs.mkdirSync(tempKt, { recursive: true });
        fs.mkdirSync(tempWasmKt, { recursive: true });
        effectiveKt = tempKt;
        effectiveWasmKt = tempWasmKt;
        LOG.info('Standalone mode: generating Kotlin files to ~/.ghost/standalone-gen/');
    }

    if (!standaloneMode && (!effectiveKt || !effectiveWasmKt)) {
        LOG.error('No Kotlin output paths configured. Use ghost.config.json or set GHOST_OUTPUT_KT.');
        return;
    }

    fs.mkdirSync(effectiveKt, { recursive: true });
    fs.mkdirSync(effectiveWasmKt, { recursive: true });
    fs.mkdirSync(cfg.outputTs, { recursive: true });

    LOG.info(`Found ${files.length} TypeScript model file(s) in ${cfg.input}`);

    // PASS 1: Detect and register Enums first (needed for correct default values in classes)
    for (const file of files) {
        const content = fs.readFileSync(path.join(cfg.input, file), 'utf8');
        const enumRegex = /export\s+enum\s+(\w+)\s*\{([\s\S]*?)\}/g;
        let m;
        while ((m = enumRegex.exec(content)) !== null) {
            const name = m[1], body = m[2];
            const values = body.split(',').map(v => v.split('=')[0].trim()).filter(v => v);
            registerEnum(name);
            fs.writeFileSync(path.join(effectiveKt, `${name}.kt`),
                `package com.ghost.serialization.standalone\n\n${genKotlinEnum(name, values)}`);
            allEnums.push(name);
        }
    }

    // PASS 2: Interfaces
    for (const file of files) {
        const content = fs.readFileSync(path.join(cfg.input, file), 'utf8');
        const itfRegex = /export\s+interface\s+(\w+)\s*[^{]*\{([\s\S]*?)\}/g;
        let m;
        while ((m = itfRegex.exec(content)) !== null) {
            const name = m[1], body = m[2];
            const fields = parseFields(name, body);
            fs.writeFileSync(path.join(effectiveKt, `${name}.kt`),
                `package com.ghost.serialization.standalone\n\n${genKotlinClass(name, fields)}`);
            allModels.push({ name, file: file.replace('.ts', ''), fields });
        }
    }

    LOG.info(`Transpiled ${allModels.length} model(s) and ${allEnums.length} enum(s)`);

    fs.writeFileSync(path.join(effectiveKt, 'GhostAutoRegistry.kt'),
        `package com.ghost.serialization.standalone\nimport com.ghost.serialization.Ghost\n\nobject GhostAutoRegistry {\n    fun registerAll() {\n        try {\n            // Auto-discover core registries if they exist\n        } catch (e: Throwable) {}\n    }\n}\n`);

    let extCode = `package com.ghost.serialization.standalone\n\nimport com.ghost.serialization.*\nimport com.ghost.serialization.InternalGhostApi\nimport kotlin.js.JsAny\n\n@OptIn(InternalGhostApi::class)\n`;
    allModels.forEach(m => {
        extCode += genJsExtension(m.name, m.fields);
    });

    let jsRegCode = `package com.ghost.serialization.standalone\nimport kotlin.js.JsAny\nimport com.ghost.serialization.GhostJsObjectRegistry\nimport com.ghost.serialization.InternalGhostApi\n\n@OptIn(InternalGhostApi::class)\nobject GhostJsRegistryInitializer {\n    fun register() {\n`;
    allModels.forEach(m => {
        jsRegCode += `        GhostJsObjectRegistry.register("${m.name}") { (it as ${m.name}).toJsAny() }\n`;
    });
    jsRegCode += `    }\n\n    fun toJsAny(obj: Any): JsAny? = when(obj) {\n`;
    allModels.forEach(m => {
        jsRegCode += `        is ${m.name} -> obj.toJsAny()\n`;
    });
    jsRegCode += `        else -> null\n    }\n}\n`;

    fs.writeFileSync(path.join(effectiveWasmKt, 'GhostJsExtensions.kt'), extCode);
    fs.writeFileSync(path.join(effectiveWasmKt, 'GhostJsRegistryInitializer.kt'), jsRegCode);

    const relModels = path.relative(cfg.outputTs, cfg.input);
    const modelImports = allModels.map(m => `import type { ${m.name} } from "${relModels}/${m.file.replace('.ts', '')}";`).join('\n');
    const modelTypes = allModels.map(m => `    ${m.name}: ${m.name};`).join('\n');
    const enumTypes = allEnums.map(e => `    ${e.name}: ${e.name};`).join('\n');

    const tsBridge = `// AUTO-GENERATED - GHOST SERIALIZATION BRIDGE
import { ghostPrewarm, ghostDeserializeJs, ghostDeserializeBytesJs, memory } from "./ghost-standalone-wasm-js.mjs";
${modelImports}

export interface GhostModels {
${modelTypes}
}

export type ModelName = keyof GhostModels;

/**
 * Ensures the Ghost WASM engine is loaded and initialized.
 */
export async function ensureGhostReady(): Promise<void> {
    // Note: initGhostWasm logic is handled by the .mjs auto-initialization 
    // but we call ghostPrewarm to register serializers.
    ghostPrewarm();
}

/**
 * Deserializes a JSON string into a typed Kotlin/Wasm model.
 */
export function deserializeModelSync<T extends ModelName>(json: string, model: T): GhostModels[T] {
    const result = ghostDeserializeJs(json, model);
    if (result === null || result === undefined) throw new Error(\`Failed to deserialize \${model}. Check if the model is registered.\`);
    return result as GhostModels[T];
}

/**
 * Deserializes raw UTF-8 bytes into a typed Kotlin/Wasm model (Fastest Path).
 */
export function deserializeModelFromBytesSync<T extends ModelName>(bytes: Uint8Array, model: T): GhostModels[T] {
    const result = ghostDeserializeBytesJs(bytes, model);
    if (result === null || result === undefined) throw new Error(\`Failed to deserialize \${model} from bytes.\`);
    return result as GhostModels[T];
}

/**
 * Returns the current memory usage of the Ghost WASM linear memory.
 */
export function getGhostWasmMemoryByteLength(): number {
    if (!memory) return 0;
    return (memory as WebAssembly.Memory).buffer.byteLength;
}

// Re-export types for convenience
${allModels.map(m => `export type { ${m.name} };`).join('\n')}
`;
    fs.writeFileSync(path.join(cfg.outputTs, 'ghost-bridge.ts'), tsBridge);

    LOG.success('Kotlin code generation complete!');

    // Standalone mode: trigger the Wasm compilation
    if (standaloneMode && !overrides.outputKt) {
        LOG.info('');
        LOG.info('Entering standalone compilation phase...');
        const { buildStandalone } = require('./ghost-standalone');
        const outputDir = await buildStandalone(effectiveKt, effectiveWasmKt);
        if (outputDir) {
            // Copy artifacts from ~/.ghost/... to the user's project
            const artifacts = fs.readdirSync(outputDir);
            artifacts.forEach(file => {
                const srcPath = path.join(outputDir, file);
                const destPath = path.join(cfg.outputTs, file);
                
                if (file.endsWith('.mjs')) {
                    let content = fs.readFileSync(srcPath, 'utf8');
                    
                    // HOT PATCH 1: Fix Kotlin/Wasm process.release.name bug in Next.js/Browser
                    // We must check if process.release actually exists before reading .name to prevent crashes in Turbopack.
                    content = content.replace(/process\.release\.name/g, "(process.release && process.release.name)");

                    // HOT PATCH 2: Architecture Fix for Next.js SSR Wasm Loading
                    // The Kotlin compiler uses experimental import.meta.resolve() which fails in Next.js SSR (Node.js).
                    // We rewrite the Node.js loader to use standard isomorphic path resolution (path.dirname + fileURLToPath).
                    content = content.replace(
                        /const filepath = import\.meta\.resolve\(wasmFilePath\);\s*const wasmBuffer = fs\.readFileSync\(url\.fileURLToPath\(filepath\)\);/,
                        "const path = require('path'); const dir = path.dirname(url.fileURLToPath(importMeta.url)); const wasmBuffer = fs.readFileSync(path.join(dir, wasmFilePath));"
                    );

                    fs.writeFileSync(destPath, content);
                } else {
                    fs.copyFileSync(srcPath, destPath);
                }
            });
            LOG.success(`Deployed Wasm artifacts to ${cfg.outputTs}`);
            LOG.success('Ghost Invisible Bridge build complete!');
        }
    } else {
        LOG.success('Ghost Turbo-Bridge generation complete!');
    }
}

if (require.main === module) runTranspiler().catch(e => { LOG.error(e.message); process.exit(1); });
module.exports = { runTranspiler };
