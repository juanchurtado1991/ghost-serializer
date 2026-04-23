#!/usr/bin/env node

'use strict';

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

/**
 * Ghost Transpiler — Comprehensive Test Suite
 * Covers all known edge cases for production-scale usage.
 */

const TEST_DIR = path.join(__dirname, '.test-env');
const MODELS_DIR = path.join(TEST_DIR, 'ghost-models');
const OUTPUT_KT_DIR = path.join(TEST_DIR, 'kotlin-out');
const OUTPUT_WASM_KT_DIR = path.join(TEST_DIR, 'wasm-kotlin-out');
const OUTPUT_TS_DIR = path.join(TEST_DIR, 'ts-out');

let passed = 0;
let failed = 0;

function assert(name, condition, detail = '') {
    if (condition) {
        console.log(`  ✅ PASS: ${name}`);
        passed++;
    } else {
        console.error(`  ❌ FAIL: ${name}${detail ? ' — ' + detail : ''}`);
        failed++;
    }
}

function resetEnv() {
    if (fs.existsSync(TEST_DIR)) fs.rmSync(TEST_DIR, { recursive: true });
    fs.mkdirSync(MODELS_DIR, { recursive: true });
    fs.mkdirSync(OUTPUT_KT_DIR, { recursive: true });
    fs.mkdirSync(OUTPUT_WASM_KT_DIR, { recursive: true });
    fs.mkdirSync(OUTPUT_TS_DIR, { recursive: true });
}

function runTranspiler() {
    const src = fs.readFileSync(path.join(__dirname, 'ghost-transpiler.js'), 'utf8');
    const patched = src
        .replace(/const INPUT_DIR\s*=[^\n]+;/, `const INPUT_DIR = ${JSON.stringify(MODELS_DIR)};`)
        .replace(/const OUTPUT_KOTLIN_DIR\s*=[^\n]+;/, `const OUTPUT_KOTLIN_DIR = ${JSON.stringify(OUTPUT_KT_DIR)};`)
        .replace(/const OUTPUT_TS_DIR\s*=[^\n]+;/, `const OUTPUT_TS_DIR = ${JSON.stringify(OUTPUT_TS_DIR)};`)
        .replace(/const OUTPUT_WASM_KT_DIR\s*=[^\n]+;/, `const OUTPUT_WASM_KT_DIR = ${JSON.stringify(OUTPUT_WASM_KT_DIR)};`);
    const runnerPath = path.join(TEST_DIR, 'runner.js');
    fs.writeFileSync(runnerPath, patched);
    try {
        execSync(`node ${runnerPath}`, { stdio: 'pipe' });
        return true;
    } catch (e) {
        console.error('    Transpiler execution error:', e.stderr?.toString() || e.message);
        return false;
    }
}

function writeModel(filename, content) {
    fs.writeFileSync(path.join(MODELS_DIR, filename), content);
}

function readKt(filename) {
    const p = path.join(OUTPUT_KT_DIR, filename);
    return fs.existsSync(p) ? fs.readFileSync(p, 'utf8') : null;
}

function readWasmKt(filename) {
    const p = path.join(OUTPUT_WASM_KT_DIR, filename);
    return fs.existsSync(p) ? fs.readFileSync(p, 'utf8') : null;
}

// ─────────────────────────────────────────────
console.log('\n[Ghost Test Suite] Starting...\n');

// ═══════════════════════════════════════════
// SUITE 1: Primitive types & defaults
// ═══════════════════════════════════════════
console.log('── Suite 1: Primitive types & defaults ──');
resetEnv();
writeModel('Primitives.ts', `
export interface Primitives {
    id: number;
    name: string;
    active: boolean;
}
`);
runTranspiler();
{
    const kt = readKt('Primitives.kt');
    assert('number → Int', kt?.includes('val id: Int = 0'));
    assert('string → String', kt?.includes('val name: String = ""'));
    assert('boolean → Boolean', kt?.includes('val active: Boolean = false'));
    assert('@GhostSerialization annotation', kt?.includes('@com.ghost.serialization.annotations.GhostSerialization'));
    assert('Correct package', kt?.includes('package com.ghost.serialization.generated'));

    const ext = readWasmKt('GhostJsExtensions.kt');
    assert('toJsAny: intToJs for number', ext?.includes('intToJs(this.id)'));
    assert('toJsAny: stringToJs for string', ext?.includes('stringToJs(this.name)'));
    assert('toJsAny: boolToJs for boolean', ext?.includes('boolToJs(this.active)'));
    assert('JsAny files NOT in commonMain (platform isolation)', !fs.existsSync(path.join(OUTPUT_KT_DIR, 'GhostJsExtensions.kt')));
}

// ═══════════════════════════════════════════
// SUITE 2: Nullable / Optional fields
// ═══════════════════════════════════════════
console.log('\n── Suite 2: Nullable & optional fields ──');
resetEnv();
writeModel('Nullables.ts', `
export interface Nullables {
    required: string;
    optionalStr?: string;
    nullableNum: number | null;
    optionalNullBool?: boolean | null;
}
`);
runTranspiler();
{
    const kt = readKt('Nullables.kt');
    assert('required field: non-nullable', kt?.includes('val required: String = ""'));
    assert('optional string: nullable with null default', kt?.includes('val optionalStr: String? = null'));
    assert('nullable number: nullable with null default', kt?.includes('val nullableNum: Int? = null'));
    assert('optional+nullable bool: nullable', kt?.includes('val optionalNullBool: Boolean? = null'));

    const ext = readWasmKt('GhostJsExtensions.kt');
    assert('toJsAny: optional string uses let pattern', ext?.includes('this.optionalStr?.let'));
    assert('toJsAny: nullable number uses let pattern', ext?.includes('this.nullableNum?.let'));
}

// ═══════════════════════════════════════════
// SUITE 3: Arrays
// ═══════════════════════════════════════════
console.log('\n── Suite 3: Array types ──');
resetEnv();
writeModel('Arrays.ts', `
export interface Arrays {
    tags: string[];
    scores: Array<number>;
    flags: boolean[];
    optionalList?: string[];
}
`);
runTranspiler();
{
    const kt = readKt('Arrays.kt');
    assert('string[] → List<String>', kt?.includes('val tags: List<String> = emptyList()'));
    assert('Array<number> → List<Int>', kt?.includes('val scores: List<Int> = emptyList()'));
    assert('boolean[] → List<Boolean>', kt?.includes('val flags: List<Boolean> = emptyList()'));
    assert('optional string[] → List<String>?', kt?.includes('val optionalList: List<String>? = null'));

    const ext = readWasmKt('GhostJsExtensions.kt');
    assert('toJsAny: string[] uses stringToJs mapper', ext?.includes('this.tags.toJsAny { stringToJs(it) }'));
    assert('toJsAny: Array<number> uses intToJs mapper', ext?.includes('this.scores.toJsAny { intToJs(it) }'));
    assert('toJsAny: boolean[] uses boolToJs mapper', ext?.includes('this.flags.toJsAny { boolToJs(it) }'));
    assert('toJsAny: optional list uses ?. mapper', ext?.includes('this.optionalList?.toJsAny { stringToJs(it) }'));
}

// ═══════════════════════════════════════════
// SUITE 4: Nested objects
// ═══════════════════════════════════════════
console.log('\n── Suite 4: Nested objects ──');
resetEnv();
writeModel('Nested.ts', `
export interface Nested {
    info: {
        count: number;
        label: string;
    };
    optMeta?: {
        key: string;
    };
}
`);
runTranspiler();
{
    const kt = readKt('Nested.kt');
    assert('nested type generated as inline class', kt?.includes('data class Nested_Info'));
    assert('nested optional type generated', kt?.includes('data class Nested_OptMeta'));
    assert('parent class references nested type', kt?.includes('val info: Nested_Info'));
    assert('optional nested field is nullable', kt?.includes('val optMeta: Nested_OptMeta? = null'));

    const ext = readWasmKt('GhostJsExtensions.kt');
    assert('toJsAny for parent calls nested .toJsAny()', ext?.includes('this.info.toJsAny()'));
    assert('toJsAny for optional nested uses ?.toJsAny()', ext?.includes('this.optMeta?.toJsAny()'));
    assert('toJsAny defined for nested type too', ext?.includes('fun Nested_Info.toJsAny()'));
}

// ═══════════════════════════════════════════
// SUITE 5: Array of custom objects
// ═══════════════════════════════════════════
console.log('\n── Suite 5: Array of custom objects ──');
resetEnv();
writeModel('Response.ts', `
export interface Character {
    id: number;
    name: string;
}
export interface Response {
    results: Character[];
}
`);
runTranspiler();
{
    const kt = readKt('Response.kt');
    assert('results → List<Character>', kt?.includes('val results: List<Character> = emptyList()'));

    const ext = readWasmKt('GhostJsExtensions.kt');
    assert('toJsAny: custom object array uses { it.toJsAny() } mapper', ext?.includes('this.results.toJsAny { it.toJsAny() }'));
}

// ═══════════════════════════════════════════
// SUITE 6: Kotlin reserved keywords
// ═══════════════════════════════════════════
console.log('\n── Suite 6: Kotlin reserved keyword escaping ──');
resetEnv();
writeModel('Keywords.ts', `
export interface Keywords {
    when: string;
    class: number;
    object: boolean;
    is: string;
    in: number;
    val: string;
    var: string;
    fun: string;
    if: string;
    for: number;
    return: string;
    normal: string;
}
`);
runTranspiler();
{
    const kt = readKt('Keywords.kt');
    const ext = readWasmKt('GhostJsExtensions.kt');
    const keywords = ['when', 'class', 'object', 'is', 'in', 'val', 'var', 'fun', 'if', 'for', 'return'];
    for (const kw of keywords) {
        assert(`'${kw}' field escaped in data class`, kt?.includes(`val \`${kw}\``));
        assert(`'${kw}' field escaped in toJsAny access`, ext?.includes(`this.\`${kw}\``));
        assert(`'${kw}' JSON key unescaped`, ext?.includes(`"${kw}"`));
    }
    assert('non-keyword field unescaped', kt?.includes('val normal: String'));
}

// ═══════════════════════════════════════════
// SUITE 7: Generated files exist
// ═══════════════════════════════════════════
console.log('\n── Suite 7: Generated file artifacts ──');
resetEnv();
writeModel('Simple.ts', `export interface Simple { id: number; }`);
runTranspiler();
{
    assert('GhostJsExtensions.kt generated in wasmJsMain', fs.existsSync(path.join(OUTPUT_WASM_KT_DIR, 'GhostJsExtensions.kt')));
    assert('GhostJsObjectRegistry_Generated.kt generated in wasmJsMain', fs.existsSync(path.join(OUTPUT_WASM_KT_DIR, 'GhostJsObjectRegistry_Generated.kt')));
    assert('GhostAutoRegistry.kt generated in commonMain (platform safe)', fs.existsSync(path.join(OUTPUT_KT_DIR, 'GhostAutoRegistry.kt')));
    assert('GhostJsExtensions.kt NOT in commonMain (platform isolation)', !fs.existsSync(path.join(OUTPUT_KT_DIR, 'GhostJsExtensions.kt')));
    assert('GhostJsObjectRegistry_Generated.kt NOT in commonMain (platform isolation)', !fs.existsSync(path.join(OUTPUT_KT_DIR, 'GhostJsObjectRegistry_Generated.kt')));

    const reg = readWasmKt('GhostJsObjectRegistry_Generated.kt');
    assert('Registry registers Simple type', reg?.includes('"Simple"'));
    assert('Registry casts to correct type', reg?.includes('(obj as Simple).toJsAny()'));

    const ext = readWasmKt('GhostJsExtensions.kt');
    assert('Extensions has List<T>.toJsAny helper', ext?.includes('fun <T : Any> List<T>.toJsAny'));
    assert('Extensions has correct imports', ext?.includes('import com.ghost.serialization.createJsObject'));

    const autoReg = readKt('GhostAutoRegistry.kt');
    assert('GhostAutoRegistry has NO JsAny import (platform safe)', !autoReg?.includes('JsAny'));
    assert('GhostAutoRegistry has NO GhostJsRegistryInitializer (platform safe)', !autoReg?.includes('GhostJsRegistryInitializer'));
}

// ═══════════════════════════════════════════
// SUITE 8: Empty model
// ═══════════════════════════════════════════
console.log('\n── Suite 8: Edge — empty interface ──');
resetEnv();
writeModel('Empty.ts', `export interface Empty {}`);
runTranspiler();
{
    const kt = readKt('Empty.kt');
    assert('empty interface generates valid data class', kt?.includes('data class Empty('));
    const ext = readWasmKt('GhostJsExtensions.kt');
    assert('empty model toJsAny returns empty object', ext?.includes('fun Empty.toJsAny()'));
}

// ═══════════════════════════════════════════
// SUITE 9: Multiple models in one file
// ═══════════════════════════════════════════
console.log('\n── Suite 9: Multiple interfaces per file ──');
resetEnv();
writeModel('Multi.ts', `
export interface Alpha { x: number; }
export interface Beta { y: string; }
export interface Gamma { z: boolean; }
`);
runTranspiler();
{
    assert('Alpha.kt generated', fs.existsSync(path.join(OUTPUT_KT_DIR, 'Alpha.kt')));
    assert('Beta.kt generated', fs.existsSync(path.join(OUTPUT_KT_DIR, 'Beta.kt')));
    assert('Gamma.kt generated', fs.existsSync(path.join(OUTPUT_KT_DIR, 'Gamma.kt')));
    const regPath = path.join(OUTPUT_WASM_KT_DIR, 'GhostJsObjectRegistry_Generated.kt');
    const reg = fs.existsSync(regPath) ? fs.readFileSync(regPath, 'utf8') : null;
    assert('Registry contains Alpha', reg?.includes('"Alpha"'));
    assert('Registry contains Beta', reg?.includes('"Beta"'));
    assert('Registry contains Gamma', reg?.includes('"Gamma"'));
}

// ═══════════════════════════════════════════
// SUITE 10: TypeScript bridge generation
// ═══════════════════════════════════════════
console.log('\n── Suite 10: TypeScript bridge ──');
resetEnv();
writeModel('BridgeModel.ts', `export interface BridgeModel { id: number; label: string; }`);
runTranspiler();
{
    const bridgePath = path.join(OUTPUT_TS_DIR, 'ghost-bridge.ts');
    assert('ghost-bridge.ts generated', fs.existsSync(bridgePath));
    const bridge = fs.existsSync(bridgePath) ? fs.readFileSync(bridgePath, 'utf8') : '';
    assert('bridge exports GhostModels interface', bridge.includes('export interface GhostModels'));
    assert('bridge exports ensureGhostReady', bridge.includes('export async function ensureGhostReady'));
    assert('bridge exports deserializeModel', bridge.includes('export async function deserializeModel'));
    assert('bridge exports deserializeModelSync', bridge.includes('export function deserializeModelSync'));
    assert('bridge uses uninstantiated.mjs', bridge.includes('ghost-serialization-wasm.uninstantiated.mjs'));
    assert('bridge has singleton engine', bridge.includes('let _engine'));
    assert('bridge calls ghostPrewarm', bridge.includes('ghostPrewarm()'));
    assert('bridge has GhostInstantiateResult interface', bridge.includes('interface GhostInstantiateResult'));
    assert('bridge has GhostUninstantiatedModule interface', bridge.includes('interface GhostUninstantiatedModule'));
    assert('bridge has no any type', !bridge.includes(': any') && !bridge.includes('as any'));
}

// ─────────────────────────────────────────────
// Results
// ─────────────────────────────────────────────
console.log(`\n[Ghost Test Suite] Results: ${passed} passed, ${failed} failed out of ${passed + failed} assertions.\n`);
if (failed > 0) {
    process.exit(1);
} else {
    console.log('[Ghost Test Suite] ✅ All assertions passed.\n');
}
