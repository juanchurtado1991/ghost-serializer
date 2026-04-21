const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

/**
 * Ghost Transpiler Unit Tests
 * Verification suite for the automated bridge generation tool.
 */

const TEST_DIR = './test-env';
const MODELS_DIR = path.join(TEST_DIR, 'ghost-models');
const OUTPUT_DIR = path.join(TEST_DIR, 'generated');

if (fs.existsSync(TEST_DIR)) fs.rmSync(TEST_DIR, { recursive: true });
fs.mkdirSync(MODELS_DIR, { recursive: true });

console.log("[Ghost Test] Running Transpiler Verification Suite...");

// 1. Create a complex Test Interface
const tsContent = `
/**
 * Stress Test Interface
 */
export interface VerificationModel {
    id: number; 
    when: string; // Reserved keyword validation
    tags: string[]; // Array mapping validation
    scores : Array < number > ; // Formatting resilience
    metadata?: any; // Nullability validation
}
`;

fs.writeFileSync(path.join(MODELS_DIR, 'VerificationModel.ts'), tsContent);

// 2. Prepare test runner
const transpiler = fs.readFileSync('./ghost-transpiler.js', 'utf8')
    .replace("const INPUT_DIR = './ghost-models';", `const INPUT_DIR = '${MODELS_DIR}';`)
    .replace("const OUTPUT_DIR = isDev \n    ? '../GhostSerialization/ghost-serialization/src/commonMain/kotlin/com/ghost/serialization/generated'\n    : './node_modules/ghost-serialization-wasm/generated-sources';", `const OUTPUT_DIR = '${OUTPUT_DIR}';`);

fs.writeFileSync(path.join(TEST_DIR, 'runner.js'), transpiler);

// 3. Execute
try {
    execSync(`node ${path.join(TEST_DIR, 'runner.js')}`);
} catch (e) {
    console.error("[Ghost Test] Execution failed.");
    process.exit(1);
}

// 4. Assertions
const ktContent = fs.readFileSync(path.join(OUTPUT_DIR, 'VerificationModel.kt'), 'utf8');

const assertions = [
    { name: 'Keyword Escaping', check: ktContent.includes('val `when`: String = ""') },
    { name: 'Array[] Mapping', check: ktContent.includes('val tags: List<String> = emptyList()') },
    { name: 'Array<T> Mapping', check: ktContent.includes('val scores: List<Int> = emptyList()') },
    { name: 'Nullability Handling', check: ktContent.includes('val metadata: String? = null') },
    { name: 'Correct Annotation', check: ktContent.includes('@GhostSerialization') },
    { name: 'Correct Import', check: ktContent.includes('import com.ghost.serialization.annotations.GhostSerialization') },
    { name: 'Registry Generation', check: fs.existsSync(path.join(OUTPUT_DIR, 'GhostAutoRegistry.kt')) }
];

let failed = false;
assertions.forEach(a => {
    if (a.check) {
        console.log(`  PASS: ${a.name}`);
    } else {
        console.error(`  FAIL: ${a.name}`);
        failed = true;
    }
});

if (failed) process.exit(1);
else console.log("[Ghost Test] All verifications passed.");
