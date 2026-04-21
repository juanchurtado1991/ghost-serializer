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
const INPUT_DIR = 'test-env/ghost-models';
const OUTPUT_DIR = 'test-env/generated';

if (!fs.existsSync(INPUT_DIR)) fs.mkdirSync(INPUT_DIR, { recursive: true });
if (!fs.existsSync(OUTPUT_DIR)) fs.mkdirSync(OUTPUT_DIR, { recursive: true });

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
