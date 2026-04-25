/**
 * Ghost Auto-Downloader — Zero-Friction Dependency Manager
 * Downloads JDK and Gradle automatically to ~/.ghost/ when not found.
 */
const fs = require('fs');
const path = require('path');
const os = require('os');
const { execSync } = require('child_process');
const { LOG, TEST_MODE } = require('./ghost-config');

const GHOST_HOME = path.join(os.homedir(), '.ghost');
const KOTLIN_VERSION = "2.3.21";
const JDK_DIR = path.join(GHOST_HOME, 'jdk');
const GRADLE_DIR = path.join(GHOST_HOME, 'gradle');

function ensureDir(dir) {
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
}

function getPlatformInfo() {
    const p = os.platform(), a = os.arch();
    const osName = p === 'linux' ? 'linux' : p === 'darwin' ? 'mac' : 'windows';
    const archName = (a === 'arm64' || a === 'aarch64') ? 'aarch64' : 'x64';
    const ext = p === 'win32' ? 'zip' : 'tar.gz';
    return { osName, archName, ext };
}

function download(url, dest) {
    LOG.info(`  ↓ Downloading from ${url.split('/').slice(0, 4).join('/')}...`);
    execSync(`curl -fSL --progress-bar -o "${dest}" "${url}"`, { stdio: 'inherit' });
}

function ensureJdk() {
    const javaBin = path.join(JDK_DIR, 'bin', 'java');
    if (fs.existsSync(javaBin)) {
        const ver = execSync(`"${javaBin}" -version 2>&1`, { encoding: 'utf8' });
        LOG.info(`JDK cached at ~/.ghost/jdk/ (${ver.split('\n')[0].trim()})`);
        return path.join(JDK_DIR, 'bin');
    }

    // Check system Java (skip in test mode)
    if (!TEST_MODE) {
        try {
            const out = execSync('java -version 2>&1', { encoding: 'utf8' });
            const m = out.match(/version "(\d+)/);
            if (m && parseInt(m[1]) >= 17) {
                LOG.info(`System Java ${m[1]} detected — using it.`);
                return null; // null = use system
            }
        } catch {}
    }

    // Auto-download JDK
    LOG.info('Java not found on your system. Auto-installing OpenJDK 25...');
    LOG.info('(This is a one-time download of ~180MB, stored at ~/.ghost/jdk/)');
    const { osName, archName, ext } = getPlatformInfo();
    const url = `https://api.adoptium.net/v3/binary/latest/25/ga/${osName}/${archName}/jdk/hotspot/normal/eclipse`;
    const tmpFile = path.join(GHOST_HOME, `jdk-tmp.${ext}`);

    ensureDir(GHOST_HOME);
    download(url, tmpFile);

    LOG.info('  Extracting JDK...');
    const extractDir = path.join(GHOST_HOME, 'jdk-tmp-extract');
    ensureDir(extractDir);

    if (ext === 'tar.gz') {
        execSync(`tar -xzf "${tmpFile}" -C "${extractDir}"`, { stdio: 'pipe' });
    } else {
        execSync(`unzip -qo "${tmpFile}" -d "${extractDir}"`, { stdio: 'pipe' });
    }

    // The extracted folder has a versioned name like jdk-21.0.x+y
    const extracted = fs.readdirSync(extractDir).find(d => d.startsWith('jdk'));
    if (!extracted) { LOG.error('JDK extraction failed.'); process.exit(1); }

    // On macOS, the JDK is inside Contents/Home
    let jdkRoot = path.join(extractDir, extracted);
    const macHome = path.join(jdkRoot, 'Contents', 'Home');
    if (fs.existsSync(macHome)) jdkRoot = macHome;

    if (fs.existsSync(JDK_DIR)) fs.rmSync(JDK_DIR, { recursive: true });
    fs.renameSync(jdkRoot, JDK_DIR);

    // Cleanup
    try { fs.unlinkSync(tmpFile); } catch {}
    try { fs.rmSync(extractDir, { recursive: true }); } catch {}

    LOG.success('OpenJDK 25 installed to ~/.ghost/jdk/');
    return path.join(JDK_DIR, 'bin');
}

function ensureGradle() {
    const gradleBin = path.join(GRADLE_DIR, 'bin', 'gradle');
    if (fs.existsSync(gradleBin)) {
        LOG.info('Gradle cached at ~/.ghost/gradle/');
        return gradleBin;
    }

    // Check system Gradle (skip in test mode)
    if (!TEST_MODE) {
        try {
            execSync('gradle --version', { stdio: 'pipe' });
            LOG.info('System Gradle detected — using it.');
            return 'gradle';
        } catch {}
    }

    // Auto-download Gradle
    LOG.info('Gradle not found on your system. Auto-installing Gradle 9.0...');
    LOG.info('(This is a one-time download of ~150MB, stored at ~/.ghost/gradle/)');
    const url = 'https://services.gradle.org/distributions/gradle-9.0-bin.zip';
    const tmpFile = path.join(GHOST_HOME, 'gradle-tmp.zip');

    ensureDir(GHOST_HOME);
    download(url, tmpFile);

    LOG.info('  Extracting Gradle...');
    const extractDir = path.join(GHOST_HOME, 'gradle-tmp-extract');
    ensureDir(extractDir);
    execSync(`unzip -qo "${tmpFile}" -d "${extractDir}"`, { stdio: 'pipe' });

    const extracted = fs.readdirSync(extractDir).find(d => d.startsWith('gradle'));
    if (!extracted) { LOG.error('Gradle extraction failed.'); process.exit(1); }

    if (fs.existsSync(GRADLE_DIR)) fs.rmSync(GRADLE_DIR, { recursive: true });
    fs.renameSync(path.join(extractDir, extracted), GRADLE_DIR);

    try { fs.unlinkSync(tmpFile); } catch {}
    try { fs.rmSync(extractDir, { recursive: true }); } catch {}

    LOG.success('Gradle 9.0 installed to ~/.ghost/gradle/');
    return gradleBin;
}

module.exports = { ensureJdk, ensureGradle, JDK_DIR, GRADLE_DIR, GHOST_HOME };
