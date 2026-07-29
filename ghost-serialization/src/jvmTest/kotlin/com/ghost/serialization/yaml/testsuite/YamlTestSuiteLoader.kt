package com.ghost.serialization.yaml.testsuite

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.streams.asSequence

/**
 * Loads every case from the vendored yaml-test-suite snapshot under
 * `ghost-serialization/src/jvmTest/resources/yaml-test-suite` (see that directory's `README.md`).
 *
 * Locates the snapshot via [repoRoot] rather than classloader resource listing — adapted from the
 * same walk-up-to-`settings.gradle.kts` approach used by
 * `ghost-compiler`'s `RepoSourceHygieneTest`, which sidesteps classloader directory-listing edge
 * cases entirely.
 */
internal object YamlTestSuiteLoader {

    private const val SNAPSHOT_RELATIVE_PATH = "ghost-serialization/src/jvmTest/resources/yaml-test-suite"
    private const val REPO_MARKER_FILE = "settings.gradle.kts"
    private const val LABEL_FILE = "==="
    private const val IN_YAML_FILE = "in.yaml"
    private const val IN_JSON_FILE = "in.json"
    private const val ERROR_FILE = "error"

    /** All loaded cases, sorted by [YamlTestSuiteCase.id] for stable, deterministic test ordering. */
    val cases: List<YamlTestSuiteCase> by lazy { loadCases() }

    private fun loadCases(): List<YamlTestSuiteCase> {
        val root = repoRoot().resolve(SNAPSHOT_RELATIVE_PATH)
        val caseDirs = Files.walk(root).use { stream ->
            stream.asSequence()
                .filter { Files.isDirectory(it) && Files.exists(it.resolve(LABEL_FILE)) }
                .toList()
        }
        return caseDirs.map { dir -> loadCase(root, dir) }.sortedBy { it.id }
    }

    private fun loadCase(root: Path, dir: Path): YamlTestSuiteCase {
        val id = root.relativize(dir).toString().replace(File.separatorChar, '_')
        val label = Files.readString(dir.resolve(LABEL_FILE)).trim()
        val inYamlBytes = Files.readAllBytes(dir.resolve(IN_YAML_FILE))
        val inJsonPath = dir.resolve(IN_JSON_FILE)
        val inJsonText = if (Files.exists(inJsonPath)) Files.readString(inJsonPath) else null
        val expectError = Files.exists(dir.resolve(ERROR_FILE))
        return YamlTestSuiteCase(id, label, inYamlBytes, inJsonText, expectError)
    }

    /** Walks up from the current working directory until it finds [REPO_MARKER_FILE]. */
    private fun repoRoot(): Path {
        var dir = Paths.get("").toAbsolutePath().normalize()
        while (true) {
            if (Files.isRegularFile(dir.resolve(REPO_MARKER_FILE))) {
                return dir
            }
            val parent = dir.parent ?: return dir
            dir = parent
        }
    }
}
