const fs = require('fs');
const path = require('path');

/**
 * Ghost Model Transpiler (TypeScript to Kotlin)
 * Automated bridge generator for Ghost Serialization framework.
 */

const KOTLIN_KEYWORDS = new Set([
    'package', 'as', 'typealias', 'class', 'this', 'super', 'val', 'var', 'fun', 'for', 'is', 'in', 'throw', 
    'return', 'break', 'continue', 'object', 'if', 'try', 'else', 'while', 'do', 'when', 'interface', 'typeof'
]);

function escapeIdentifier(id) {
    if (KOTLIN_KEYWORDS.has(id)) return `\`${id}\``;
    return id;
}

const isDev = fs.existsSync('../ghost-serialization');
const INPUT_DIR = './ghost-models';
const OUTPUT_DIR = isDev 
    ? '../GhostSerialization/ghost-serialization/src/commonMain/kotlin/com/ghost/serialization/generated'
    : './node_modules/ghost-serialization-wasm/generated-sources';
const TS_OUTPUT_DIR = isDev
    ? '../GhostSerialization/ghost-serialization/npm-tools/generated-types'
    : './ghost-generated-types';

if (!fs.existsSync(INPUT_DIR)) fs.mkdirSync(INPUT_DIR, { recursive: true });
if (!fs.existsSync(OUTPUT_DIR)) fs.mkdirSync(OUTPUT_DIR, { recursive: true });
if (!fs.existsSync(TS_OUTPUT_DIR)) fs.mkdirSync(TS_OUTPUT_DIR, { recursive: true });

function mapTsTypeToKotlin(tsType) {
    const type = tsType.trim().replace(/\s/g, '');
    
    if (type.endsWith('[]')) {
        const inner = mapTsTypeToKotlin(type.slice(0, -2));
        return `List<${inner}>`;
    }
    if (type.startsWith('Array<') && type.endsWith('>')) {
        const inner = mapTsTypeToKotlin(type.slice(6, -1));
        return `List<${inner}>`;
    }

    switch (type.toLowerCase()) {
        case 'number': return 'Int';
        case 'string': return 'String';
        case 'boolean': return 'Boolean';
        case 'any': return 'String';
        default: return 'String';
    }
}

function getDefaultValue(ktType) {
    if (ktType.startsWith('List')) return 'emptyList()';
    if (ktType === 'Int') return '0';
    if (ktType === 'Boolean') return 'false';
    return '""';
}

function transpileFile(fileName) {
    const content = fs.readFileSync(path.join(INPUT_DIR, fileName), 'utf8')
        .replace(/\/\*[\s\S]*?\*\/|\/\/.*/g, ''); // Strip comments
    
    const interfaceRegex = /export\s+interface\s+(\w+)\s*{([\s\S]+?)}/g;
    let match;
    
    while ((match = interfaceRegex.exec(content)) !== null) {
        const className = match[1];
        const body = match[2];
        
        const fieldRegex = /(\w+)(\?)?\s*:\s*([^;]+);/g;
        let fieldMatch;
        let kotlinFields = '';
        
        while ((fieldMatch = fieldRegex.exec(body)) !== null) {
            const rawName = fieldMatch[1];
            const isOptional = fieldMatch[2] === '?';
            const tsType = fieldMatch[3].trim();
            
            const name = escapeIdentifier(rawName);
            const ktType = mapTsTypeToKotlin(tsType);
            
            const finalType = isOptional ? `${ktType}?` : ktType;
            const finalDefault = isOptional ? 'null' : getDefaultValue(ktType);

            kotlinFields += `    val ${name}: ${finalType} = ${finalDefault},\n`;
        }

        const kotlinClass = `package com.ghost.serialization.generated

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class ${className}(
${kotlinFields.replace(/,\n$/, '')}
)
`;

        fs.writeFileSync(path.join(OUTPUT_DIR, `${className}.kt`), kotlinClass);
        console.log(`[Ghost] Transpiled ${className} to Kotlin.`);
    }
}

try {
    const files = fs.readdirSync(INPUT_DIR);
    files.forEach(file => {
        if (file.endsWith('.ts')) transpileFile(file);
    });

    // 2. Generate TS Type Safety Bridge
    const tsBridge = `
/**
 * Ghost Serialization - Type Safety Bridge
 * Generated automatically. Do not edit.
 */
import { ghostDeserialize } from "ghost-serialization-wasm";

export interface GhostModels {
${files.filter(f => f.endsWith('.ts')).map(f => {
    const name = f.replace('.ts', '');
    return `    ${name}: import("./ghost-models/${name}").${name};`;
}).join('\n')}
}

export function deserializeModel<K extends keyof GhostModels>(json: string, type: K): GhostModels[K] {
    const result = ghostDeserialize(json, type as string);
    if (!result) {
        throw new Error(\`[Ghost] Failed to deserialize "\${type}". This usually means the model was not synchronized or the WASM engine needs to be rebuilt. Run "npm run ghost:sync" to fix this.\`);
    }
    return JSON.parse(result) as GhostModels[K];
}
`;
    fs.writeFileSync(path.join(TS_OUTPUT_DIR, `ghost-bridge.ts`), tsBridge);

    const autoRegistryKt = `package com.ghost.serialization.generated
import com.ghost.serialization.Ghost

object GhostAutoRegistry {
    fun registerAll() {
        try {
            // Integration hook for KSP generated modules
            Ghost.addRegistry(GhostModuleRegistry_ghost_serialization.INSTANCE)
        } catch (e: Throwable) {}
    }
}
`;
    fs.writeFileSync(path.join(OUTPUT_DIR, `GhostAutoRegistry.kt`), autoRegistryKt);
    console.log("[Ghost] Synchronization orchestrated successfully.");
} catch (e) {
    console.error(`[Ghost] Error during transpilation: ${e.message}`);
    process.exit(1);
}
