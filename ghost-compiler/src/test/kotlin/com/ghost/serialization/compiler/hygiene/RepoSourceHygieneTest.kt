package com.ghost.serialization.compiler.hygiene

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.streams.asSequence

class RepoSourceHygieneTest {

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

    @Test
    fun noWildcardImportsInGhostModules() {
        val repoRoot = repoRoot()
        val ghostModules = repoRoot.toFile()
            .listFiles { file -> file.isDirectory && file.name.startsWith("ghost-") }
            ?.map { it.toPath() }
            .orEmpty()

        val wildcardImport = Regex("""^import .+\.\*$""")
        val violations = mutableListOf<String>()

        for (module in ghostModules) {
            Files.walk(module)
                .asSequence()
                .filter { path ->
                    Files.isRegularFile(path) &&
                        path.toString().endsWith(".kt") &&
                        path.toString().contains("${Path.of("src")}${Path.of("/")}") &&
                        !path.toString().contains("${Path.of("build")}${Path.of("/")}")
                }
                .forEach { path ->
                    val relative = repoRoot.relativize(path).toString().replace('\\', '/')
                    Files.readString(path).lineSequence().forEachIndexed { index, line ->
                        if (wildcardImport.matches(line.trim())) {
                            violations += "$relative:${index + 1}: $line"
                        }
                    }
                }
        }

        assertTrue(
            violations.isEmpty(),
            "Wildcard imports are forbidden; use explicit imports instead:\n" +
                violations.joinToString("\n"),
        )
    }
}
