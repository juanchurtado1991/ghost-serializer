#!/usr/bin/env node

const fs = require('fs');
const path = require('path');

/**
 * Ghost Serialization Transpiler CLI (v1.1.7)
 * Orchestrates synchronization between TypeScript models and Kotlin backend.
 */

// 1. Configuration (Prioritize Env Vars > CLI Args > Defaults)
const CWD = process.cwd();
const INPUT_DIR = process.env.GHOST_INPUT || process.argv[2] || path.join(CWD, 'ghost-models');
const OUTPUT_KOTLIN_DIR = process.env.GHOST_OUTPUT_KT || process.argv[3] || path.join(CWD, 'src/commonMain/kotlin/com/ghost/serialization/generated');
const OUTPUT_TS_DIR = process.env.GHOST_OUTPUT_TS || process.argv[4] || path.join(CWD, 'src/ghost-generated-types');
const OUTPUT_WASM_KT_DIR = process.env.GHOST_OUTPUT_WASM_KT || path.join(OUTPUT_KOTLIN_DIR, '..', '..', '..', '..', 'wasmJsMain', 'kotlin', 'com', 'ghost', 'serialization', 'generated');

console.log(`[Ghost] Syncing models...`);
console.log(`[Ghost] Input: ${INPUT_DIR}`);
console.log(`[Ghost] Output (KT): ${OUTPUT_KOTLIN_DIR}`);
console.log(`[Ghost] Output (TS): ${OUTPUT_TS_DIR}`);

// 2. Ensure Directories Exist
if (!fs.existsSync(INPUT_DIR)) {
    console.warn(`[Ghost] Warning: Input directory "${INPUT_DIR}" not found. Creating empty one.`);
    fs.mkdirSync(INPUT_DIR, { recursive: true });
}
if (!fs.existsSync(OUTPUT_KOTLIN_DIR)) fs.mkdirSync(OUTPUT_KOTLIN_DIR, { recursive: true });
if (!fs.existsSync(OUTPUT_TS_DIR)) fs.mkdirSync(OUTPUT_TS_DIR, { recursive: true });
if (!fs.existsSync(OUTPUT_WASM_KT_DIR)) fs.mkdirSync(OUTPUT_WASM_KT_DIR, { recursive: true });


function mapTsTypeToKotlin(tsType, contextName = "") {
    let type = tsType.trim().replace(/\s/g, '');
    const isNullable = type.includes('|null');
    if (isNullable) type = type.replace('|null', '');
    if (type.endsWith('[]')) return `List<${mapTsTypeToKotlin(type.slice(0, -2), contextName)}>`;
    if (type.startsWith('Array<')) return `List<${mapTsTypeToKotlin(type.slice(6, -1), contextName)}>`;
    if (type.startsWith('{')) return contextName;
    switch (type.toLowerCase()) {
        case 'number': return 'Int';
        case 'long': return 'Long';
        case 'string': return 'String';
        case 'boolean': return 'Boolean';
        case 'double': return 'Double';
        default: return type;
    }
}

function getDefaultValue(ktType) {
    if (ktType.startsWith('List')) return 'emptyList()';
    if (ktType === 'Int') return '0';
    if (ktType === 'Boolean') return 'false';
    if (ktType === 'String') return '""';
    if (ktType.endsWith('?')) return 'null';
    return `${ktType}()`;
}

const KOTLIN_KEYWORDS = new Set([
    'as', 'break', 'class', 'continue', 'do', 'else', 'false', 'for', 'fun', 'if',
    'in', 'interface', 'is', 'null', 'object', 'package', 'return', 'super', 'this',
    'throw', 'true', 'try', 'typealias', 'typeof', 'val', 'var', 'when', 'while',
    'by', 'catch', 'constructor', 'delegate', 'dynamic', 'field', 'file', 'finally',
    'get', 'import', 'init', 'param', 'property', 'receiver', 'set', 'setparam', 'value', 'where'
]);

function ktIdent(name) {
    return KOTLIN_KEYWORDS.has(name) ? `\`${name}\`` : name;
}

function listElementMapper(elemTsType) {
    const et = elemTsType.toLowerCase().replace(/\s/g, '');
    if (et === 'string') return '{ stringToJs(it) }';
    if (et === 'number' || et === 'int') return '{ intToJs(it) }';
    if (et === 'long' || et === 'double') return '{ doubleToJs(it.toDouble()) }';
    if (et === 'boolean') return '{ boolToJs(it) }';
    return '{ it.toJsAny() }';
}

function listElemType(tsType) {
    const t = tsType.replace(/\s/g, '');
    if (t.endsWith('[]')) return t.slice(0, -2);
    if (/^Array</i.test(t)) return t.slice(6, -1);
    return null;
}

function generateClassBody(className, body) {
    const fields = [];
    const nestedTypes = [];
    let remaining = body;
    while (remaining.trim().length > 0) {
        const fieldMatch = remaining.match(/^\s*(\w+)(\?)?\s*:\s*/);
        if (!fieldMatch) { remaining = remaining.slice(1); continue; }
        const fieldName = fieldMatch[1];
        let isOptional = !!fieldMatch[2];
        let tsType = "";
        remaining = remaining.slice(fieldMatch[0].length);
        if (remaining[0] === '{') {
            let bc = 0; let i = 0;
            for (; i < remaining.length; i++) {
                if (remaining[i] === '{') bc++;
                if (remaining[i] === '}') bc--;
                if (bc === 0) break;
            }
            tsType = remaining.slice(0, i + 1);
            remaining = remaining.slice(i + 1);
            const nestedName = className + "_" + fieldName.charAt(0).toUpperCase() + fieldName.slice(1);
            nestedTypes.push({ name: nestedName, body: tsType.slice(1, -1) });
            tsType = nestedName;
        } else {
            const semiPos = remaining.indexOf(';');
            if (semiPos === -1) { tsType = remaining.trim(); remaining = ""; }
            else { tsType = remaining.slice(0, semiPos).trim(); remaining = remaining.slice(semiPos + 1); }
        }
        const isNullable = /\|\s*null/.test(tsType);
        const cleanTsType = tsType.replace(/\|\s*null/g, '').trim();
        const ktType = mapTsTypeToKotlin(cleanTsType, cleanTsType);
        const finalNullable = isOptional || isNullable;
        const finalType = finalNullable ? `${ktType}?` : ktType;
        const finalDefault = finalNullable ? 'null' : getDefaultValue(ktType);
        fields.push({ name: fieldName, ktName: ktIdent(fieldName), type: finalType, tsType: cleanTsType, nullable: finalNullable, defaultValue: finalDefault });
    }
    let code = `@com.ghost.serialization.annotations.GhostSerialization\ndata class ${className}(\n`;
    code += fields.map(f => `    val ${f.ktName}: ${f.type} = ${f.defaultValue}`).join(',\n');
    code += `\n)\n\n`;
    for (const nested of nestedTypes) code += generateClassBody(nested.name, nested.body);
    return code;
}

function generateToJsAny(className, body) {
    const fields = [];
    const nestedTypes = [];
    let remaining = body;
    while (remaining.trim().length > 0) {
        const fieldMatch = remaining.match(/^\s*(\w+)(\?)?\s*:\s*/);
        if (!fieldMatch) { remaining = remaining.slice(1); continue; }
        const fieldName = fieldMatch[1];
        const isOptional = !!fieldMatch[2];
        let tsType = "";
        remaining = remaining.slice(fieldMatch[0].length);
        if (remaining[0] === '{') {
            let bc = 0; let i = 0;
            for (; i < remaining.length; i++) {
                if (remaining[i] === '{') bc++;
                if (remaining[i] === '}') bc--;
                if (bc === 0) break;
            }
            tsType = remaining.slice(0, i + 1);
            remaining = remaining.slice(i + 1);
            const nestedName = className + "_" + fieldName.charAt(0).toUpperCase() + fieldName.slice(1);
            nestedTypes.push({ name: nestedName, body: tsType.slice(1, -1) });
            tsType = nestedName;
        } else {
            const semiPos = remaining.indexOf(';');
            if (semiPos === -1) { tsType = remaining.trim(); remaining = ""; }
            else { tsType = remaining.slice(0, semiPos).trim(); remaining = remaining.slice(semiPos + 1); }
        }
        const isNullable = /\|\s*null/.test(tsType) || isOptional;
        const cleanTsType = tsType.replace(/\|\s*null/g, '').trim();
        fields.push({ name: fieldName, tsType: cleanTsType, nullable: isNullable });
    }

    const toJsField = (f) => {
        const ktProp = `this.${ktIdent(f.name)}`;
        const jsonKey = f.name;
        const t = f.tsType.toLowerCase().replace(/\s/g, '');
        const elemType = listElemType(f.tsType);

        if (elemType !== null) {
            const mapper = listElementMapper(elemType);
            if (f.nullable) {
                return `    setJsProperty(obj, "${jsonKey}", ${ktProp}?.toJsAny ${mapper} ?: null)`;
            }
            return `    setJsProperty(obj, "${jsonKey}", ${ktProp}.toJsAny ${mapper})`;
        }
        if (f.nullable) {
            if (t === 'string')  return `    ${ktProp}?.let { setJsProperty(obj, "${jsonKey}", stringToJs(it)) } ?: setJsProperty(obj, "${jsonKey}", null)`;
            if (t === 'number' || t === 'int')  return `    ${ktProp}?.let { setJsProperty(obj, "${jsonKey}", intToJs(it)) } ?: setJsProperty(obj, "${jsonKey}", null)`;
            if (t === 'long' || t === 'double') return `    ${ktProp}?.let { setJsProperty(obj, "${jsonKey}", doubleToJs(it.toDouble())) } ?: setJsProperty(obj, "${jsonKey}", null)`;
            if (t === 'boolean') return `    ${ktProp}?.let { setJsProperty(obj, "${jsonKey}", boolToJs(it)) } ?: setJsProperty(obj, "${jsonKey}", null)`;
            return `    setJsProperty(obj, "${jsonKey}", ${ktProp}?.toJsAny())`;
        }
        if (t === 'string')  return `    setJsProperty(obj, "${jsonKey}", stringToJs(${ktProp}))`;
        if (t === 'number' || t === 'int')  return `    setJsProperty(obj, "${jsonKey}", intToJs(${ktProp}))`;
        if (t === 'long' || t === 'double') return `    setJsProperty(obj, "${jsonKey}", doubleToJs(${ktProp}.toDouble()))`;
        if (t === 'boolean') return `    setJsProperty(obj, "${jsonKey}", boolToJs(${ktProp}))`;
        return `    setJsProperty(obj, "${jsonKey}", ${ktProp}.toJsAny())`;
    };

    let code = `\nfun ${className}.toJsAny(): JsAny {\n    val obj = createJsObject()\n`;
    code += fields.map(toJsField).join('\n') + '\n';
    code += `    return obj\n}\n`;
    for (const nested of nestedTypes) code += generateToJsAny(nested.name, nested.body);
    return code;
}

try {
    const files = fs.readdirSync(INPUT_DIR);
    const modelToFiles = {};
    const modelBodies = {};
    files.forEach(file => {
        if (file.endsWith('.ts')) {
            const fileNameNoExt = file.replace('.ts', '');
            let content = fs.readFileSync(path.join(INPUT_DIR, file), 'utf8').replace(/\/\*[\s\S]*?\*\/|\/\/.*/g, '');
            while (content.length > 0) {
                const interfaceMatch = content.match(/export\s+interface\s+(\w+)\s*\{/);
                if (!interfaceMatch) break;
                const className = interfaceMatch[1];
                modelToFiles[className] = fileNameNoExt;
                let startPos = interfaceMatch.index + interfaceMatch[0].length - 1;
                let bc = 0; let i = startPos;
                for (; i < content.length; i++) {
                    if (content[i] === '{') bc++;
                    if (content[i] === '}') bc--;
                    if (bc === 0) break;
                }
                const body = content.slice(startPos + 1, i);
                modelBodies[className] = body;
                const header = `package com.ghost.serialization.generated\n\nimport com.ghost.serialization.annotations.GhostSerialization\n\n`;
                fs.writeFileSync(path.join(OUTPUT_KOTLIN_DIR, `${className}.kt`), header + generateClassBody(className, body));
                console.log(`[Ghost] Transpiled ${className} to Kotlin.`);
                content = content.slice(i + 1);
            }
        }
    });

    // Cleanup: Remove any stale JsAny files that may have ended up in commonMain from a previous run.
    ['GhostJsExtensions.kt', 'GhostJsObjectRegistry_Generated.kt'].forEach(f => {
        const stale = path.join(OUTPUT_KOTLIN_DIR, f);
        if (fs.existsSync(stale)) { fs.unlinkSync(stale); }
    });

    // GhostJsExtensions.kt and GhostJsObjectRegistry_Generated.kt go to wasmJsMain
    const jsExtHeader = `package com.ghost.serialization.generated\n\nimport com.ghost.serialization.createJsObject\nimport com.ghost.serialization.setJsProperty\nimport com.ghost.serialization.stringToJs\nimport com.ghost.serialization.intToJs\nimport com.ghost.serialization.boolToJs\nimport com.ghost.serialization.createJsArray\nimport com.ghost.serialization.pushJsArray\nimport kotlin.js.JsAny\n`;
    let jsExtBody = '';
    for (const [className, body] of Object.entries(modelBodies)) {
        jsExtBody += generateToJsAny(className, body);
    }
    jsExtBody += `\nfun <T : Any> List<T>.toJsAny(mapper: (T) -> JsAny?): JsAny {\n    val arr = createJsArray()\n    forEach { pushJsArray(arr, mapper(it)) }\n    return arr\n}\n`;
    fs.writeFileSync(path.join(OUTPUT_WASM_KT_DIR, 'GhostJsExtensions.kt'), jsExtHeader + jsExtBody);

    const regHeader = `package com.ghost.serialization.generated\n\nimport com.ghost.serialization.GhostJsObjectRegistry\nimport kotlin.js.JsAny\n\nobject GhostJsRegistryInitializer {\n    fun register() {\n`;
    const regEntries = Object.keys(modelBodies).map(n =>
        `        GhostJsObjectRegistry.register("${n}", { obj -> (obj as ${n}).toJsAny() })`
    ).join('\n');
    const regFooter = `\n    }\n}\n`;
    fs.writeFileSync(path.join(OUTPUT_WASM_KT_DIR, 'GhostJsObjectRegistry_Generated.kt'), regHeader + regEntries + regFooter);
    console.log(`[Ghost] Generated JS object registry for ${Object.keys(modelBodies).length} models.`);

    // GhostAutoRegistry.kt stays in commonMain
    const autoRegistry = `package com.ghost.serialization.generated\nimport com.ghost.serialization.Ghost\n\nobject GhostAutoRegistry {\n    fun registerAll() {\n        try {\n            Ghost.addRegistry(com.ghost.serialization.benchmark.GhostModuleRegistry_ghost_serialization.INSTANCE)\n        } catch (e: Throwable) {}\n    }\n}\n`;
    fs.writeFileSync(path.join(OUTPUT_KOTLIN_DIR, 'GhostAutoRegistry.kt'), autoRegistry);


    const relPathToModels = path.relative(OUTPUT_TS_DIR, INPUT_DIR);
    const relPathToNodeModules = path.relative(OUTPUT_TS_DIR, path.join(CWD, 'node_modules'));
    
    // We use standard strings to avoid template nesting issues during sync
    const tsBridge = "/**\n" +
" * Generated by Ghost Serialization (v1.1.7)\n" +
" * DO NOT EDIT MANUALLY.\n" +
" */\n\n" +
"import type * as GhostWasm from \"ghost-serialization-wasm\";\n\n" +
"export interface GhostModels {\n" +
Object.keys(modelToFiles).map(name => `    ${name}: import("${relPathToModels}/${modelToFiles[name]}").${name};`).join('\n') + "\n" +
"}\n\n" +
"type GhostEngine = typeof GhostWasm;\n\n" +
"interface GhostInstantiateResult {\n" +
"    instance: WebAssembly.Instance;\n" +
"    exports: GhostEngine;\n" +
"}\n\n" +
"interface GhostUninstantiatedModule {\n" +
"    instantiate(imports?: WebAssembly.Imports, runInitializer?: boolean): Promise<GhostInstantiateResult>;\n" +
"}\n\n" +
"let _engine: GhostEngine | null = null;\n" +
"let _wasmInstance: WebAssembly.Instance | null = null;\n\n" +
"async function getGhostEngine(): Promise<GhostEngine> {\n" +
"    if (_engine) return _engine;\n" +
"    // @ts-ignore\n" +
"    const mod = (await import(\"" + relPathToNodeModules + "/ghost-serialization-wasm/ghost-serialization-wasm.uninstantiated.mjs\")) as GhostUninstantiatedModule;\n" +
"    const { instance, exports } = await mod.instantiate();\n" +
"    exports.ghostPrewarm();\n" +
"    _engine = exports;\n" +
"    _wasmInstance = instance;\n" +
"    return _engine;\n" +
"}\n\n" +
"export function getGhostWasmMemoryByteLength(): number {\n" +
"    if (!_wasmInstance) return 0;\n" +
"    const memory = _wasmInstance.exports.memory;\n" +
"    if (memory instanceof WebAssembly.Memory) {\n" +
"        return memory.buffer.byteLength;\n" +
"    }\n" +
"    return 0;\n" +
"}\n\n" +
"export async function ensureGhostReady(): Promise<void> {\n" +
"    await getGhostEngine();\n" +
"}\n\n" +
"export async function deserializeModel<K extends keyof GhostModels>(json: string, type: K): Promise<GhostModels[K]> {\n" +
"    const wasm = await getGhostEngine();\n" +
"    const result = wasm.ghostDeserializeJs(json, type as string);\n" +
"    if (result === undefined || result === null) throw new Error(`[Ghost] Failed to deserialize \"${type}\".`);\n" +
"    return result as unknown as GhostModels[K];\n" +
"}\n\n" +
"export function deserializeModelSync<K extends keyof GhostModels>(json: string, type: K): GhostModels[K] {\n" +
"    if (!_engine) throw new Error(\"[Ghost] Engine not ready. Await ensureGhostReady() before calling deserializeModelSync.\");\n" +
"    const result = _engine.ghostDeserializeJs(json, type as string);\n" +
"    if (result === undefined || result === null) throw new Error(`[Ghost] Failed to deserialize \"${type}\".`);\n" +
"    return result as unknown as GhostModels[K];\n" +
"}\n\n" +
"export function deserializeModelFromBytesSync<K extends keyof GhostModels>(bytes: Uint8Array, type: K): GhostModels[K] {\n" +
"    if (!_engine) throw new Error(\"[Ghost] Engine not ready. Await ensureGhostReady() before calling deserializeModelFromBytesSync.\");\n" +
"    // @ts-ignore - wasm.ghostDeserializeBytesJs is added in the latest version\n" +
"    const result = _engine.ghostDeserializeBytesJs(bytes, type as string);\n" +
"    if (result === undefined || result === null) throw new Error(`[Ghost] Failed to deserialize \"${type}\" from bytes.`);\n" +
"    return result as unknown as GhostModels[K];\n" +
"}\n";

    fs.writeFileSync(path.join(OUTPUT_TS_DIR, 'ghost-bridge.ts'), tsBridge);

    console.log("[Ghost] Synchronization complete.");

    // 3. Physical Library Sync (Bypass Turbopack Symlink Restrictions)
    const LIB_SRC = process.env.GHOST_LIB_SRC;
    const LIB_DEST = path.join(CWD, 'node_modules/ghost-serialization-wasm');

    if (LIB_SRC && fs.existsSync(LIB_SRC)) {
        console.log(`[Ghost] Physically syncing library from ${LIB_SRC}...`);
        
        if (fs.existsSync(LIB_DEST)) {
            const stats = fs.lstatSync(LIB_DEST);
            if (stats.isSymbolicLink()) {
                fs.unlinkSync(LIB_DEST);
            }
        }

        if (!fs.existsSync(LIB_DEST)) fs.mkdirSync(LIB_DEST, { recursive: true });
        
        const filesToCopy = fs.readdirSync(LIB_SRC);
        filesToCopy.forEach(file => {
            const srcPath = path.join(LIB_SRC, file);
            if (fs.statSync(srcPath).isFile()) {
                fs.copyFileSync(srcPath, path.join(LIB_DEST, file));
            }
        });
        
        // 4. Patch Kotlin/Wasm bridge for Next.js browser compatibility
        const uninstantiatedPath = path.join(LIB_DEST, 'ghost-serialization-wasm.uninstantiated.mjs');
        if (fs.existsSync(uninstantiatedPath)) {
            console.log("[Ghost] Patching bridge for Next.js compatibility...");
            let content = fs.readFileSync(uninstantiatedPath, 'utf8');
            content = content.replace(
                "(typeof process !== 'undefined') && (process.release.name === 'node')",
                "(typeof process !== 'undefined') && process.release && (process.release.name === 'node')"
            );
            fs.writeFileSync(uninstantiatedPath, content);
            console.log("[Ghost] Patching successful.");
        }

        const toolsSrc = path.join(LIB_SRC, 'tools');
        const toolsDest = path.join(LIB_DEST, 'tools');
        if (fs.existsSync(toolsSrc)) {
            if (!fs.existsSync(toolsDest)) fs.mkdirSync(toolsDest, { recursive: true });
            fs.readdirSync(toolsSrc).forEach(file => {
                const srcPath = path.join(toolsSrc, file);
                if (fs.statSync(srcPath).isFile()) {
                    fs.copyFileSync(srcPath, path.join(toolsDest, file));
                }
            });
        }
        console.log("[Ghost] Physical library sync successful.");
    }
} catch (e) {
    console.error(`[Ghost] Error: ${e.message}`);
    process.exit(1);
}
