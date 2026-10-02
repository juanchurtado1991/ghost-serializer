package com.ghost.serialization.compiler.analysis

import com.google.devtools.ksp.symbol.KSClassDeclaration

/**
 * Makes a whitelisted `Qualifier.MEMBER` default (see [DefaultExpressionExtractor]) resolvable from
 * the generated serializer — a separate top-level file that only shares the model's package and the
 * imports KotlinPoet adds for referenced types. A qualifier that starts with a class nested in the
 * model (or in one of its enclosing classes), e.g. `Lifecycle.Type.UNKNOWN` written inside
 * `DeviceLifecycleEventData`, is not visible there, so it is rewritten to that class's
 * fully-qualified name. Any other expression is returned unchanged.
 */
internal object DefaultExpressionQualifier {

    fun qualify(
        expression: String,
        model: KSClassDeclaration?
    ): String {
        if (model == null || !QUALIFIED_REFERENCE.matches(input = expression)) return expression
        val firstSegment = expression.substringBefore(delimiter = DOT)
        val nestedClass = generateSequence(seed = model) { declaration ->
            declaration.parentDeclaration as? KSClassDeclaration
        }
            .flatMap { declaration -> declaration.declarations.filterIsInstance<KSClassDeclaration>() }
            .firstOrNull { declaration -> declaration.simpleName.asString() == firstSegment }
            ?: return expression
        val qualifiedName = nestedClass.qualifiedName?.asString() ?: return expression
        return qualifiedName + expression.removePrefix(prefix = firstSegment)
    }

    private const val DOT = "."
    private val QUALIFIED_REFERENCE = Regex(pattern = "[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")
}
