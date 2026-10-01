package com.ghost.serialization.parser.common

/**
 * Runtime JS-engine probe for Wasm encode-path selection (#16).
 *
 * JavaScriptCore (Safari desktop + every iOS browser) hits a severe cliff on
 * `CharArray.concatToString`; V8 (Chrome/Edge) prefers the char writer.
 */
@OptIn(ExperimentalWasmJsInterop::class)
internal fun ghostJsEnginePrefersUtf8EncodeToString(): Boolean = js(
    code = """
    (function () {
        if (typeof navigator === 'undefined') return true;
        var ua = navigator.userAgent || '';
        // Every iOS browser runs JavaScriptCore, whatever its branding says.
        if (/iPhone|iPad|iPod/.test(ua)) return true;
        // Safari desktop: has "Safari" but none of the Chromium / Firefox / Opera tokens.
        var isChromiumFirefoxOrOpera =
            /Chrome|Chromium|CriOS|Edg\/|EdgA|EdgiOS|Firefox|FxiOS|OPR\//.test(ua);
        return /Safari/.test(ua) && !isChromiumFirefoxOrOpera;
    })()
    """
)
