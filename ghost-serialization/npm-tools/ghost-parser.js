/**
 * Ghost Transpiler — TypeScript Parser
 * Parses TypeScript interface definitions into field models.
 */

const { LOG } = require('./ghost-config');

// ---------------------------------------------------------------------------
// Type Mapping
// ---------------------------------------------------------------------------

function mapTsType(tsType, contextName = '') {
    let type = tsType.trim().replace(/\s/g, '');
    const nullable = type.includes('|null');
    if (nullable) type = type.replace(/\|null/g, '');

    // BUG-3 FIX: Detect non-null union types (e.g., "string|number")
    if (type.includes('|')) {
        const alternatives = type.split('|').filter(t => t !== 'null' && t.trim() !== '');
        if (alternatives.length > 1) {
            LOG.warn(`Union type "${tsType}" detected. Using first alternative "${alternatives[0]}" as fallback.`);
        }
        type = alternatives[0] || 'string';
    }

    if (type.endsWith('[]'))   return `List<${mapTsType(type.slice(0, -2), contextName)}>`;
    if (type.startsWith('Array<')) return `List<${mapTsType(type.slice(6, -1), contextName)}>`;
    if (type.startsWith('{'))  return contextName;
    switch (type.toLowerCase()) {
        case 'number':  return 'Int';
        case 'long':    return 'Long';
        case 'string':  return 'String';
        case 'boolean': return 'Boolean';
        case 'double':  return 'Double';
        case 'float':   return 'Double';  // BUG-2 FIX: float maps to Double in Kotlin
        case 'any':                        // Edge case: fallback for untyped fields
        case 'unknown':
        case 'never':
            LOG.warn(`Type "${type}" mapped to String as safe fallback.`);
            return 'String';
        default:
            // Paranoid: handle literal types like 'alive' | 'dead'
            if (type.startsWith("'") || type.startsWith('"')) return 'String';
            return type;
    }
}

// ---------------------------------------------------------------------------
// Default Values
// ---------------------------------------------------------------------------

const ENUMS = new Set();

function registerEnum(name) {
    ENUMS.add(name);
}

function defaultValue(ktType) {
    if (ktType.startsWith('List')) return 'emptyList()';
    if (ktType === 'Int')     return '0';
    if (ktType === 'Long')    return '0L';
    if (ktType === 'Double')  return '0.0';
    if (ktType === 'Boolean') return 'false';
    if (ktType === 'String')  return '""';
    if (ktType.endsWith('?')) return 'null';
    if (ENUMS.has(ktType))    return `${ktType}.unknown`;
    return `${ktType}()`;
}

// ---------------------------------------------------------------------------
// Field Parser
// ---------------------------------------------------------------------------

/** Parse fields from a TS interface body string */
function parseFields(className, body) {
    const fields = [];
    let rem = body;
    while (rem.trim().length > 0) {
        const m = rem.match(/^\s*(\w+)(\?)?\s*:\s*/);
        if (!m) { rem = rem.slice(1); continue; }
        const name = m[1];
        const optional = !!m[2];
        rem = rem.slice(m[0].length);

        if (rem[0] === '{') {
            // Inline object — treat as nested type
            let depth = 0, i = 0;
            for (; i < rem.length; i++) {
                if (rem[i] === '{') depth++;
                if (rem[i] === '}') depth--;
                if (depth === 0) break;
            }
            const nestedBody = rem.slice(1, i);
            const nestedName = className + '_' + name[0].toUpperCase() + name.slice(1);
            
            // Check if it's a list of nested objects: { ... }[]
            const afterNested = rem.slice(i + 1).trim();
            const isNestedList = afterNested.startsWith('[]');
            const tsType = isNestedList ? `${nestedName}[]` : nestedName;
            
            fields.push({ name, tsType, nestedBody, optional });
            rem = rem.slice(i + 1 + (isNestedList ? 2 : 0));
        } else {
            const semi = rem.indexOf(';');
            const rawType = semi === -1 ? rem.trim() : rem.slice(0, semi).trim();
            rem = semi === -1 ? '' : rem.slice(semi + 1);
            const isNullable = /\|\s*null/.test(rawType);
            fields.push({ name, tsType: rawType.replace(/\|\s*null/g, '').trim(), optional: optional || isNullable });
        }
    }
    return fields;
}

module.exports = { mapTsType, defaultValue, parseFields, registerEnum };
