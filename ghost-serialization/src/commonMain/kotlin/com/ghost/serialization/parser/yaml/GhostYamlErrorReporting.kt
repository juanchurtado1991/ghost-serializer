package com.ghost.serialization.parser.yaml

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.yaml.exception.GhostYamlException
import com.ghost.serialization.yaml.exception.hintForYamlError
import com.ghost.serialization.yaml.GhostYamlErrorMessages as EM

private const val JSON_PATH_ROOT = "$"

@OptIn(InternalGhostApi::class)
internal fun GhostYamlFlatReader.yamlError(message: String): Nothing {
    // Parse phase: no AST cursor yet — keep path at root so we never invent a fake location.
    throw GhostYamlException(
        baseMessage = "$message${EM.ERR_AT_POSITION_PAREN_PREFIX}$position${EM.ERR_AT_POSITION_PAREN_SUFFIX}",
        path = JSON_PATH_ROOT,
        hint = message.hintForYamlError(),
    )
}
