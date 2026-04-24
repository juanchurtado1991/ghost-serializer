/**
 * Ghost Transpiler — Code Generator
 * Generates Kotlin data classes and JS interop extensions with
 * Single-Crossing Factory optimization.
 */

const { LOG, ktIdent } = require('./ghost-config');
const { mapTsType, defaultValue, parseFields } = require('./ghost-parser');

const ENUMS = new Set();

function registerGeneratedEnum(name) {
    ENUMS.add(name);
}

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

function genKotlinEnum(enumName, values) {
    registerGeneratedEnum(enumName);
    let code = `@com.ghost.serialization.annotations.GhostSerialization\nenum class ${enumName} {\n`;
    code += values.map(v => `    ${v}`).join(',\n');
    if (!values.includes('unknown')) {
        code += ',\n    unknown';
    }
    code += '\n}\n\n';
    return code;
}

// ---------------------------------------------------------------------------
// Single-Crossing Factory (Fast Path / Safe Path)
// ---------------------------------------------------------------------------

const FAST_PATH_THRESHOLD = 10;
const PRIMITIVE_TYPES = new Set(['string', 'number', 'int', 'boolean', 'double', 'long', 'float']);

function isSimplePrimitive(field) {
    if (field.optional || field.nestedBody) return false;
    const t = field.tsType.toLowerCase().replace(/\s/g, '');
    const isList = field.tsType.endsWith('[]') || /^Array</i.test(field.tsType);
    if (isList) return false;
    return PRIMITIVE_TYPES.has(t);
}

function qualifiesForFastPath(fields) {
    if (fields.length === 0 || fields.length > FAST_PATH_THRESHOLD) return false;
    return fields.every(f => isSimplePrimitive(f));
}

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

function genJsExtension(className, fields) {
    let code = '';
    const useFastPath = qualifiesForFastPath(fields);

    if (useFastPath) {
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
        code += `\nfun ${className}.toJsAny(): JsAny {\n    val obj = createJsObject()\n`;
        fields.forEach(f => {
            const prop = `this.${ktIdent(f.name)}`;
            const key  = `"${f.name}"`;
            const ktType = mapTsType(f.tsType, f.tsType);
            const t    = f.tsType.toLowerCase().replace(/\s/g, '');
            const isList = f.tsType.endsWith('[]') || /^Array</i.test(f.tsType);

            if (isList) {
                const elemTypeRaw = f.tsType.replace(/^Array<|>$|\[\]$/gi, '');
                const elemTypeKt = mapTsType(elemTypeRaw, elemTypeRaw);
                const elemTypeLower = elemTypeRaw.toLowerCase();
                
                let mapper = 'it.toJsAny()';
                if (elemTypeLower === 'string') mapper = 'stringToJs(it)';
                else if (elemTypeLower === 'number' || elemTypeLower === 'int') mapper = 'intToJs(it)';
                else if (elemTypeLower === 'boolean') mapper = 'boolToJs(it)';
                else if (elemTypeLower === 'double' || elemTypeLower === 'float') mapper = 'doubleToJs(it)';
                else if (elemTypeLower === 'long') mapper = 'intToJs(it.toInt())';
                else if (ENUMS.has(elemTypeKt)) mapper = 'stringToJs(it.name)';

                if (f.optional) {
                    code += `    ${prop}?.let { setJsProperty(obj, ${key}, it.toJsAny { ${mapper} }) } ?: setJsProperty(obj, ${key}, null)\n`;
                } else {
                    code += `    setJsProperty(obj, ${key}, ${prop}.toJsAny { ${mapper} })\n`;
                }
            } else if (ENUMS.has(ktType)) {
                // ENUM Path
                if (f.optional) {
                    code += `    ${prop}?.let { setJsProperty(obj, ${key}, stringToJs(it.name)) } ?: setJsProperty(obj, ${key}, null)\n`;
                } else {
                    code += `    setJsProperty(obj, ${key}, stringToJs(${prop}.name))\n`;
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

    fields.filter(f => f.nestedBody).forEach(f => {
        code += genJsExtension(f.tsType, parseFields(f.tsType, f.nestedBody));
    });
    return code;
}

module.exports = { genKotlinClass, genKotlinEnum, genJsExtension };
