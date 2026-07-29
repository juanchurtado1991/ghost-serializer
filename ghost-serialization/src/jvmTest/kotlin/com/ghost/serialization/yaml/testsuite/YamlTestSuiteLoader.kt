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

    /** All loaded cases, sorted by [YamlTestSuiteCase.id] for stable, deterministic test ordering. */
    val cases: List<YamlTestSuiteCase> by lazy { loadCases() }

    private fun loadCases(): List<YamlTestSuiteCase> {
        val root = repoRoot().resolve("ghost-serialization/src/jvmTest/resources/yaml-test-suite")
        val caseDirs = Files.walk(root).use { stream ->
            stream.asSequence()
                .filter { Files.isDirectory(it) && Files.exists(it.resolve("===")) }
                .toList()
        }
        return caseDirs.map { dir -> loadCase(root, dir) }.sortedBy { it.id }
    }

    private fun loadCase(root: Path, dir: Path): YamlTestSuiteCase {
        val id = root.relativize(dir).toString().replace(File.separatorChar, '_')
        val label = Files.readString(dir.resolve("===")).trim()
        val inYamlBytes = Files.readAllBytes(dir.resolve("in.yaml"))
        val inJsonPath = dir.resolve("in.json")
        val inJsonText = if (Files.exists(inJsonPath)) Files.readString(inJsonPath) else null
        val expectError = Files.exists(dir.resolve("error"))
        return YamlTestSuiteCase(id, label, inYamlBytes, inJsonText, expectError)
    }

    /** Walks up from the current working directory until it finds `settings.gradle.kts`. */
    private fun repoRoot(): Path {
        var dir = Paths.get("").toAbsolutePath().normalize()
        while (true) {
            if (Files.isRegularFile(dir.resolve("settings.gradle.kts"))) {
                return dir
            }
            val parent = dir.parent ?: return dir
            dir = parent
        }
    }
}
