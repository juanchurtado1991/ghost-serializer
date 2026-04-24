/**
 * Ghost Transpiler — Code Generator
 * Generates Kotlin data classes and JS interop extensions with
 * Single-Crossing Factory optimization.
 */

const { LOG, ktIdent } = require('./ghost-config');
const { mapTsType, defaultValue, parseFields } = require('./ghost-parser');

// ---------------------------------------------------------------------------
// Kotlin Data Class Generator
// ---------------------------------------------------------------------------

function genKotlinClass(className, fields) {
    let code = `@com.ghost.serialization.annotations.GhostSerialization\ndata class ${className}(\n`;
    code += fields.map(f => {
        const kt = mapTsType(f.tsType, f.tsType);
        const type = f.optional ? `${kt}?` : kt;
        const def  = f.optional ? 'null' : defaultValue(kt);
        return `    val ${ktIdent(f.name)}: ${type} = ${def}`;
    }).join(',\n');
    code += '\n)\n\n';
    fields.filter(f => f.nestedBody).forEach(f => {
        code += genKotlinClass(f.tsType, parseFields(f.tsType, f.nestedBody));
    });
    return code;
}

// ---------------------------------------------------------------------------
// Single-Crossing Factory (Fast Path / Safe Path)
// ---------------------------------------------------------------------------

const FAST_PATH_THRESHOLD = 10;
const PRIMITIVE_TYPES = new Set(['string', 'number', 'int', 'boolean', 'double', 'long', 'float']);

/**
 * Determines if a field is a simple primitive that can be passed directly
 * through the @JsFun factory without intermediate conversion.
 */
function isSimplePrimitive(field) {
    if (field.optional || field.nestedBody) return false;
    const t = field.tsType.toLowerCase().replace(/\s/g, '');
    const isList = field.tsType.endsWith('[]') || /^Array</i.test(field.tsType);
    if (isList) return false;
    return PRIMITIVE_TYPES.has(t);
}

/**
 * Returns true if the model qualifies for the Fast Path (Single-Crossing Factory).
 * Criteria: <= FAST_PATH_THRESHOLD fields AND all fields are simple primitives.
 */
function qualifiesForFastPath(fields) {
    if (fields.length === 0 || fields.length > FAST_PATH_THRESHOLD) return false;
    return fields.every(f => isSimplePrimitive(f));
}

/**
 * Maps a TS type to the Kotlin-to-JsAny conversion helper name.
 */
function primitiveToJsHelper(tsType) {
    const t = tsType.toLowerCase().replace(/\s/g, '');
    switch (t) {
        case 'string':  return 'stringToJs';
        case 'number':
        case 'int':     return 'intToJs';
        case 'boolean': return 'boolToJs';
        case 'double':
        case 'float':   return 'doubleToJs';
        case 'long':    return 'intToJs';
        default:        return 'stringToJs';
    }
}

/**
 * Generate a @JsFun factory function for a single bridge crossing.
 * Uses a private external function + internal wrapper to avoid name mangling issues.
 */
function genJsFactory(className, fields) {
    const params = fields.map((_, i) => `p${i}`).join(', ');
    const jsBody = fields.map((f, i) => `${f.name}: p${i}`).join(', ');
    const jsFunCode = `(${params}) => ({ ${jsBody} })`;
    const ktParams = fields.map((_, i) => `p${i}: JsAny?`).join(', ');
    const ktArgs = fields.map((_, i) => `p${i}`).join(', ');

    return `\n@JsFun("${jsFunCode}")\n` +
           `private external fun createJs_${className}_Raw(${ktParams}): JsAny\n\n` +
           `internal fun createJs_${className}(${ktParams}): JsAny = createJs_${className}_Raw(${ktArgs})\n`;
}

// ---------------------------------------------------------------------------
// JS Extension Generator (toJsAny)
// ---------------------------------------------------------------------------

/**
 * Generate toJsAny() using the Fast Path (single crossing) or Safe Path (multi crossing).
 */
function genJsExtension(className, fields) {
    let code = '';
    const useFastPath = qualifiesForFastPath(fields);

    if (useFastPath) {
        // --- FAST PATH: Single-Crossing Factory ---
        code += genJsFactory(className, fields);
        code += `\nfun ${className}.toJsAny(): JsAny {\n`;
        code += `    return createJs_${className}(\n`;
        fields.forEach((f, i) => {
            const prop = `this.${ktIdent(f.name)}`;
            const helper = primitiveToJsHelper(f.tsType);
            const comma = i < fields.length - 1 ? ',' : '';
            code += `        ${helper}(${prop})${comma}\n`;
        });
        code += `    )\n}\n`;
        LOG.info(`  ⚡ Fast Path (Single-Crossing): ${className} (${fields.length} fields)`);
    } else {
        // --- SAFE PATH: Multi-Crossing Builder (existing pattern) ---
        code += `\nfun ${className}.toJsAny(): JsAny {\n    val obj = createJsObject()\n`;
        fields.forEach(f => {
            const prop = `this.${ktIdent(f.name)}`;
            const key  = `"${f.name}"`;
            const t    = f.tsType.toLowerCase().replace(/\s/g, '');
            const isList = f.tsType.endsWith('[]') || /^Array</i.test(f.tsType);

            if (isList) {
                const elemType = f.tsType.replace(/^Array<|>$|\[\]$/gi, '').toLowerCase();
                let mapper = 'it.toJsAny()';
                if (elemType === 'string') mapper = 'stringToJs(it)';
                else if (elemType === 'number' || elemType === 'int') mapper = 'intToJs(it)';
                else if (elemType === 'boolean') mapper = 'boolToJs(it)';
                else if (elemType === 'double' || elemType === 'float') mapper = 'doubleToJs(it)';
                else if (elemType === 'long') mapper = 'intToJs(it.toInt())';
                if (f.optional) {
                    code += `    ${prop}?.let { setJsProperty(obj, ${key}, it.toJsAny { ${mapper} }) } ?: setJsProperty(obj, ${key}, null)\n`;
                } else {
                    code += `    setJsProperty(obj, ${key}, ${prop}.toJsAny { ${mapper} })\n`;
                }
            } else if (f.optional) {
                if (t === 'string') code += `    ${prop}?.let { setJsProperty(obj, ${key}, stringToJs(it)) } ?: setJsProperty(obj, ${key}, null)\n`;
                else if (t === 'number' || t === 'int') code += `    ${prop}?.let { setJsProperty(obj, ${key}, intToJs(it)) } ?: setJsProperty(obj, ${key}, null)\n`;
                else if (t === 'boolean') code += `    ${prop}?.let { setJsProperty(obj, ${key}, boolToJs(it)) } ?: setJsProperty(obj, ${key}, null)\n`;
                else if (t === 'long') code += `    ${prop}?.let { setJsProperty(obj, ${key}, intToJs(it.toInt())) } ?: setJsProperty(obj, ${key}, null)\n`;
                else if (t === 'double' || t === 'float') code += `    ${prop}?.let { setJsProperty(obj, ${key}, doubleToJs(it.toDouble())) } ?: setJsProperty(obj, ${key}, null)\n`;
                else code += `    ${prop}?.let { setJsProperty(obj, ${key}, it.toJsAny()) } ?: setJsProperty(obj, ${key}, null)\n`;
            } else {
                if (t === 'string') code += `    setJsProperty(obj, ${key}, stringToJs(${prop}))\n`;
                else if (t === 'number' || t === 'int') code += `    setJsProperty(obj, ${key}, intToJs(${prop}))\n`;
                else if (t === 'boolean') code += `    setJsProperty(obj, ${key}, boolToJs(${prop}))\n`;
                else if (t === 'long') code += `    setJsProperty(obj, ${key}, intToJs(${prop}.toInt()))\n`;
                else if (t === 'double' || t === 'float') code += `    setJsProperty(obj, ${key}, doubleToJs(${prop}))\n`;
                else code += `    setJsProperty(obj, ${key}, ${prop}.toJsAny())\n`;
            }
        });
        code += `    return obj\n}\n`;
        LOG.info(`  🛡️ Safe Path (Multi-Crossing): ${className} (${fields.length} fields)`);
    }

    // Recurse into nested types
    fields.filter(f => f.nestedBody).forEach(f => {
        code += genJsExtension(f.tsType, parseFields(f.tsType, f.nestedBody));
    });
    return code;
}

module.exports = { genKotlinClass, genJsExtension };
