#!/usr/bin/env node
const fs = require('fs');
const path = require('path');
const { LOG, CONFIG: DEFAULT_CONFIG } = require('./ghost-config');
const { parseFields } = require('./ghost-parser');
const { genKotlinClass, genJsExtension } = require('./ghost-codegen');

async function runTranspiler(overrides = {}) {
    const cfg = { ...DEFAULT_CONFIG, ...overrides };
    LOG.info('Ghost Turbo-Bridge Transpiler starting...');
    const allModels = [];

    if (!fs.existsSync(cfg.input)) {
        LOG.error(`Input directory not found: ${cfg.input}`);
        return;
    }

    const files = fs.readdirSync(cfg.input).filter(f => f.endsWith('.ts'));
    for (const file of files) {
        const content = fs.readFileSync(path.join(cfg.input, file), 'utf8');
        const itfRegex = /export\s+interface\s+(\w+)\s*[^{]*\{([\s\S]*?)\}/g;
        let m;
        while ((m = itfRegex.exec(content)) !== null) {
            const name = m[1], body = m[2];
            const fields = parseFields(name, body);
            fs.writeFileSync(path.join(cfg.outputKt, `${name}.kt`), 
                `package com.ghost.serialization.generated\n\n${genKotlinClass(name, fields)}`);
            allModels.push({ name, file: file.replace('.ts', ''), fields });
        }
    }

    fs.writeFileSync(path.join(cfg.outputKt, 'GhostAutoRegistry.kt'), 
        `package com.ghost.serialization.generated\nimport com.ghost.serialization.Ghost\n\nobject GhostAutoRegistry {\n    fun registerAll() {\n        try {\n            Ghost.addRegistry(com.ghost.serialization.benchmark.GhostModuleRegistry_ghost_serialization.INSTANCE)\n        } catch (e: Throwable) {}\n    }\n}\n`);

    let extCode = `package com.ghost.serialization.generated\n\nimport com.ghost.serialization.toJsAny\nimport com.ghost.serialization.setJsProperty\nimport com.ghost.serialization.createJsObject\nimport com.ghost.serialization.stringToJs\nimport com.ghost.serialization.intToJs\nimport com.ghost.serialization.boolToJs\nimport com.ghost.serialization.doubleToJs\n\n`;
    let jsRegCode = `package com.ghost.serialization.generated\nimport kotlin.js.JsAny\n\nobject GhostJsObjectRegistry_Generated {\n    fun toJsAny(obj: Any): JsAny? = when(obj) {\n`;

    allModels.forEach(m => {
        extCode += genJsExtension(m.name, m.fields);
        jsRegCode += `        is ${m.name} -> obj.toJsAny()\n`;
    });
    jsRegCode += `        else -> null\n    }\n}\n`;

    fs.writeFileSync(path.join(cfg.outputWasmKt, 'GhostJsExtensions.kt'), extCode);
    fs.writeFileSync(path.join(cfg.outputWasmKt, 'GhostJsObjectRegistry_Generated.kt'), jsRegCode);

    const relModels = path.relative(cfg.outputTs, cfg.input);
    const tsBridge = `// AUTO-GENERATED\nimport type * as GhostWasm from "ghost-serialization-wasm";\nexport interface GhostModels {\n${allModels.map(m => `    ${m.name}: import("${relModels}/${m.file}").${m.name};`).join('\n')}\n}\n`;
    fs.writeFileSync(path.join(cfg.outputTs, 'ghost-bridge.ts'), tsBridge);

    LOG.success('Ghost Turbo-Bridge generation complete!');
}

if (require.main === module) runTranspiler().catch(e => { LOG.error(e.message); process.exit(1); });
module.exports = { runTranspiler };
