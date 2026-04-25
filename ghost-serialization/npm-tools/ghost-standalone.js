/**
 * Ghost Standalone Builder — "The Invisible Bridge"
 * Creates a hidden KMP project, auto-installs tools, compiles Kotlin/Wasm.
 * Zero friction for the user.
 */
const fs = require('fs');
const path = require('path');
const os = require('os');
const { execSync } = require('child_process');
const { LOG } = require('./ghost-config');
const { ensureJdk, ensureGradle } = require('./ghost-downloader');

const GHOST_HOME = path.join(os.homedir(), '.ghost');
const PROJECT_DIR = path.join(GHOST_HOME, 'standalone');
const KOTLIN_VERSION = '2.1.10';
const GHOST_LIB_VERSION = '1.1.13';

function ensureDir(dir) {
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
}

function writeSettingsGradle() {
    fs.writeFileSync(path.join(PROJECT_DIR, 'settings.gradle.kts'), `pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "ghost-standalone"
`);
}

function writeBuildGradle() {
    fs.writeFileSync(path.join(PROJECT_DIR, 'build.gradle.kts'), `plugins {
    kotlin("multiplatform") version "${KOTLIN_VERSION}"
}

repositories {
    mavenCentral()
    maven { url = uri("https://oss.sonatype.org/content/repositories/releases/") }
    maven { url = uri("https://s01.oss.sonatype.org/content/repositories/releases/") }
}

kotlin {
    wasmJs {
        browser()
        binaries.library()
    }

    sourceSets {
        commonMain.dependencies {
            implementation("com.ghostserializer:ghost-serialization:${GHOST_LIB_VERSION}")
            implementation("com.ghostserializer:ghost-api:${GHOST_LIB_VERSION}")
        }
    }
}
`);
}

function copyGeneratedFiles(commonKtDir, wasmKtDir) {
    const targetCommon = path.join(PROJECT_DIR, 'src/commonMain/kotlin/com/ghost/serialization/generated');
    const targetWasm = path.join(PROJECT_DIR, 'src/wasmJsMain/kotlin/com/ghost/serialization/generated');

    ensureDir(targetCommon);
    ensureDir(targetWasm);

    // Clean previous
    [targetCommon, targetWasm].forEach(dir => {
        fs.readdirSync(dir).forEach(f => fs.unlinkSync(path.join(dir, f)));
    });

    // Copy new
    let count = 0;
    [{ src: commonKtDir, dst: targetCommon }, { src: wasmKtDir, dst: targetWasm }].forEach(({ src, dst }) => {
        if (fs.existsSync(src)) {
            fs.readdirSync(src).filter(f => f.endsWith('.kt')).forEach(f => {
                fs.copyFileSync(path.join(src, f), path.join(dst, f));
                count++;
            });
        }
    });
    LOG.info(`  Injected ${count} Kotlin source files.`);
}

async function buildStandalone(commonKtDir, wasmKtDir) {
    LOG.info('');
    LOG.info('╔══════════════════════════════════════════════════╗');
    LOG.info('║   Ghost Standalone Mode — The Invisible Bridge   ║');
    LOG.info('╚══════════════════════════════════════════════════╝');
    LOG.info('');

    // Step 1: Ensure JDK
    LOG.info('[1/5] Ensuring Java Development Kit...');
    const jdkBinDir = ensureJdk();

    // Step 2: Ensure Gradle
    LOG.info('[2/5] Ensuring Gradle build system...');
    const gradleCmd = ensureGradle();

    // Step 3: Create project
    LOG.info('[3/5] Setting up invisible Kotlin/Wasm project...');
    ensureDir(PROJECT_DIR);
    writeSettingsGradle();
    writeBuildGradle();

    // Generate wrapper using the Gradle we just ensured
    const wrapperJar = path.join(PROJECT_DIR, 'gradle/wrapper/gradle-wrapper.jar');
    if (!fs.existsSync(wrapperJar)) {
        LOG.info('  Generating Gradle wrapper...');
        const env = { ...process.env };
        if (jdkBinDir) env.JAVA_HOME = path.dirname(jdkBinDir);
        if (jdkBinDir) env.PATH = `${jdkBinDir}:${env.PATH}`;
        execSync(`"${gradleCmd}" wrapper`, { cwd: PROJECT_DIR, stdio: 'pipe', env });
        LOG.success('  Gradle wrapper ready.');
    }

    // Step 4: Copy generated files
    LOG.info('[4/5] Injecting generated Kotlin models...');
    copyGeneratedFiles(commonKtDir, wasmKtDir);

    // Step 5: Build
    LOG.info('[5/5] Compiling Kotlin/Wasm engine...');
    LOG.info('  (First build downloads dependencies — subsequent builds are fast)');

    const gradlew = path.join(PROJECT_DIR, 'gradlew');
    if (!fs.existsSync(gradlew)) {
        LOG.error('Gradle wrapper not found. Setup failed.');
        process.exit(1);
    }

    const buildEnv = { ...process.env };
    if (jdkBinDir) {
        buildEnv.JAVA_HOME = path.dirname(jdkBinDir);
        buildEnv.PATH = `${jdkBinDir}:${buildEnv.PATH}`;
    }

    try {
        execSync(`"${gradlew}" wasmJsBrowserProductionLibraryDistribution --no-daemon`, {
            cwd: PROJECT_DIR,
            stdio: 'inherit',
            env: buildEnv
        });
    } catch (e) {
        LOG.error('Kotlin/Wasm compilation failed. See output above.');
        process.exit(1);
    }

    // Report
    const dist = path.join(PROJECT_DIR, 'build/dist/wasmJs/productionLibrary');
    if (fs.existsSync(dist)) {
        LOG.success('');
        LOG.success('Build complete! Artifacts:');
        fs.readdirSync(dist).forEach(f => {
            const size = (fs.statSync(path.join(dist, f)).size / 1024).toFixed(1);
            LOG.info(`  📦 ${f} (${size} KB)`);
        });
        return dist;
    }

    LOG.error('Build output not found.');
    return null;
}

module.exports = { buildStandalone, GHOST_HOME, PROJECT_DIR };
