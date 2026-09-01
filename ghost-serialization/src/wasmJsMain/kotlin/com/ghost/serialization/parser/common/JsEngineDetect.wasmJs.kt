package com.ghost.serialization.parser.common

/**
 * Runtime JS-engine probe for Wasm encode-path selection (#16).
 *
 * JavaScriptCore (Safari desktop + every iOS browser) hits a severe cliff on
 * `CharArray.concatToString`; V8 (Chrome/Edge) prefers the char writer.
 */
@OptIn(ExperimentalWasmJsInterop::class)
internal fun ghostJsEnginePrefersUtf8EncodeToString(): Boolean =
    js(
        "(function(){if(typeof navigator==='undefined')return true;var ua=navigator.userAgent||'';if(/iPhone|iPad|iPod/.test(ua))return true;if(/Safari/.test(ua)&&!/Chrome|Chromium|CriOS|Edg\\/|EdgA|EdgiOS|Firefox|FxiOS|OPR\\//.test(ua))return true;return false;})()"
    )
