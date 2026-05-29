/**
 * Ghost Compiler Lab — i18n Module (TypeScript)
 * Bilingual support: Español (es) / English (en)
 */

export type Lang = 'es' | 'en';

export const SUPPORTED_LANGS: Lang[] = ['es', 'en'];
export const DEFAULT_LANG: Lang = 'es';
export const STORAGE_KEY = 'ghost_lab_lang';

type TranslationValue = string | ((...args: any[]) => string);

export const TRANSLATIONS: Record<Lang, Record<string, TranslationValue>> = {
  es: {
    // Header
    'header.badge': 'Ghost Serialization Engine — KSP Lab',
    'header.tagline': 'Un viaje guiado por los <strong>4 pilares de optimización</strong> que hacen a Ghost <strong class="text-sky-400">3× más rápido</strong> y <strong class="text-emerald-400">10× más eficiente en RAM</strong> que Moshi, Gson y kotlinx.serialization.',

    // Nav Steps
    'nav.step1': 'KSP Compiler',
    'nav.step2': 'Bitmasks',
    'nav.step3': 'Key Matching',
    'nav.step4': 'Constructor',
    'nav.step5': 'Serializar',

    // Pillar 1
    'p1.badge': 'Pilar 1 — Hash Perfecto O(1): El Despachador Sin Búsquedas',
    'p1.concept.title': '¿Por qué esto importa?',
    'p1.concept.body': 'Las librerías tradicionales como Gson usan un <code>HashMap&lt;String, Field&gt;</code> para encontrar qué campo corresponde a cada clave JSON — esto requiere calcular el hash de cada String, buscar en la tabla y comparar con <code>equals()</code>. Ghost elimina todo eso: el <strong>KSP Compiler</strong> pre-calcula en tiempo de compilación un <strong>hash perfecto sin colisiones</strong>. En runtime, encontrar un campo es una operación matemática <strong>O(1)</strong> directa sobre bytes crudos.',
    'p1.input.label': 'Tu Data Class Kotlin',
    'p1.substep1': '1. Byte Packing',
    'p1.substep2': '2. Tabla Dispatch',
    'p1.substep3': '3. Código Generado',
    'btn.compile': 'Ejecutar KSP Compiler',
    'p1.terminal.title': 'Terminal — KSP Output',
    'p1.terminal.ready': 'ready',
    'p1.terminal.waiting': '> Pulsa "Ejecutar KSP Compiler" para comenzar...',
    'p1.pack.title': 'Paso 1 de 3 — Byte Packing (4 bytes → Int32)',
    'p1.pack.field': 'Campo actual:',
    'p1.pack.int32': 'Int32 (key)',
    'p1.pack.byte3': 'Byte 3 (bits 24-31)',
    'p1.pack.byte2': 'Byte 2 (bits 16-23)',
    'p1.pack.byte1': 'Byte 1 (bits 8-15)',
    'p1.pack.byte0': 'Byte 0 (bits 0-7)',
    'p1.pack.formula.label': 'Fórmula real (PerfectHashFinder.kt):',
    'p1.grid.title': 'Paso 2 de 3 — Tabla Dispatch O(1) (1024 slots)',
    'p1.grid.hint': '👉 Hover sobre un slot verde',
    'p1.grid.desc': 'Cada punto <span class="text-emerald-400 font-bold">verde</span> = un campo mapeado sin colisiones. Los grises son slots vacíos.',
    'p1.grid.empty': 'Ejecuta el compilador para ver el código generado y explorar la matemática de cada slot.',
    'p1.code.title': 'Paso 3 de 3 — Código KSP generado (JsonReaderOptions):',
    'p1.math.field.prefix': 'Campo:',
    'p1.math.slot.prefix': '→ Slot',
    'p1.math.close': '✕ Cerrar',
    'p1.math.step1': '1. Bytes del campo:',
    'p1.math.step2': '2. key × Multiplier:',
    'p1.math.step3': '3. + len (anti-colisión):',
    'p1.math.step4': '4. >> Shift (CPU):',
    'p1.math.step5': '5. & 1023 (slot final):',
    'p1.cta.text': '✅ <strong>Pilar 1 completado.</strong> El KSP generó la tabla dispatch O(1). Ahora: cómo Ghost rastrea qué campos leyó sin allocar nada.',
    'btn.next.p2': 'Siguiente: Bitmask State Tracking →',

    // Pillar 2
    'p2.badge': 'Pilar 2 — Bitmask State Tracking: Rastreo de Campos Sin Allocar Memoria',
    'p2.concept.title': '¿Por qué esto importa?',
    'p2.concept.body': 'Las librerías tradicionales usan un <code>HashSet&lt;String&gt;</code> para recordar qué campos ya leyeron. Cada inserción = <strong>un objeto nuevo en el Heap = presión en el GC</strong>. Ghost usa un único número entero (<code>Long</code> de 64 bits) en el <strong>Stack</strong>. Activar un campo = <strong>1 instrucción OR</strong>. Validar todos los requeridos = <strong>1 instrucción AND</strong>. Cero allocations.<br><br><strong class="text-emerald-300">Haz clic en "Paso Siguiente ⏵" para seguir la deserialización.</strong>',
    'btn.load.json': 'Cargar JSON',
    'btn.restart': 'Reiniciar ⟳',
    'btn.next.step': 'Paso Siguiente ⏵',
    'btn.restart.debugger': 'Reiniciar Debugger ⟳',
    'p2.code.title': 'GhostJsonFlatReader.kt',
    'p2.json.title': 'Stream JSON (ByteArray crudo)',
    'p2.json.pos': 'Pos:',
    'p2.stack.title': 'Variables (Stack)',
    'p2.bitmask.title': 'Bitmask (mask0)',
    'p2.step.initial': 'Esperando...',
    'p2.step.initial.desc': 'Haz clic en <strong>"Paso Siguiente ⏵"</strong> para comenzar la simulación de deserialización.',
    'p2.cta.text': '✅ <strong>Pilar 2 completado.</strong> Viste cómo mask0 se activó bit por bit, sin crear ningún objeto. Ahora: comparación de bytes sin crear Strings.',
    'btn.next.p3': 'Siguiente: Zero-Allocation Key Matching →',

    // Pillar 3
    'p3.badge': 'Pilar 3 — Zero-Allocation Key Matching: Comparación Desenrollada de Bytes',
    'p3.concept.title': '¿Por qué esto importa?',
    'p3.concept.body': 'Cuando el hash dice "creo que este campo es <code>name</code>", Ghost tiene que <strong>confirmar</strong> que los bytes leídos son <code>n-a-m-e</code>. Lo normal sería hacer <code>String.equals()</code> — que aloca un String. Ghost compara bytes directamente en bloques de 4, con <strong>4 comparaciones individuales de bytes por iteración</strong> dentro de <code>verifyKeyMatch()</code>. Cero objetos creados.<br><br><strong class="text-pink-300">Escribe dos palabras y ve el proceso byte a byte.</strong>',
    'p3.expected.label': 'Clave esperada (constante compilada por KSP)',
    'p3.candidate.label': 'Candidato leído del JSON',
    'p3.hint': 'Prueba: "name" (match), "nane" (colisión de longitud), "na" (largo diferente)',
    'btn.run.keymatch': '⚡ Ejecutar verifyKeyMatch()',
    'btn.run.keymatch.badge': 'Listo',
    'p3.trace.title': 'Rastreo CPU — verifyKeyMatch() — Cero Strings Creados',
    'p3.trace.initial': 'Introduce datos y pulsa el botón para ver la comparación byte a byte...',
    'p3.field_selector.title': 'Seleccionar campo a probar:',
    'p3.result.match': 'MATCH ✓',
    'p3.result.fail': 'NO COINCIDE ✗',
    'p3.result.match.sub': 'Los bytes coinciden exactamente. Ghost acepta el campo.',
    'p3.result.fail.sub': 'Diferencia detectada. Ghost descarta el candidato.',
    'p3.cta.text': '✅ <strong>Pilar 3 completado.</strong> Viste la comparación de bytes sin crear Strings. Último pilar: Constructor Dispatch.',
    'btn.next.p4': 'Siguiente: Constructor Dispatch →',

    // Pillar 4
    'p4.badge': 'Pilar 4 — Constructor Dispatch: Inyección Nativa de Defaults Sin Reflection',
    'p4.concept.title': '¿Por qué esto importa?',
    'p4.concept.body': '¿Qué pasa si el JSON no incluye un campo opcional como <code>score</code>? Las librerías tradicionales usan <strong>Reflection</strong> o asignan <code>null</code> y lo reemplazan con el default. Ghost usa el valor de <code>mask0</code> para saber exactamente cuáles campos llegaron y llama al <strong>constructor exacto</strong> que inyecta solo los defaults necesarios. El KSP genera <strong>2ⁿ ramas de código</strong> — una por cada combinación posible de campos opcionales.<br><br><strong class="text-yellow-300">Marca/desmarca los campos que "llegaron" en el JSON y observa qué rama se activa.</strong>',
    'p4.fields.title': 'Campos presentes en el JSON (simula mask0)',
    'p4.mask.label': 'mask0 =',
    'p4.branches.title': 'Ramas de Código Generadas por KSP',
    'p4.field.required': 'Requerido (siempre presente)',
    'p4.field.optional': 'Opcional (tiene default)',
    'p4.branch.matched': '← RAMA ACTIVA',
    'p4.cta.text': '✅ <strong>¡Los 4 pilares explicados!</strong> Ahora: cómo Ghost serializa de vuelta a JSON con igual eficiencia.',
    'btn.next.p5': 'Siguiente: Serialización Directa →',

    // Pillar 5
    'p5.badge': 'Bonus — Serialización Directa a ByteArray: Cero Strings Intermedios',
    'p5.concept.title': '¿Por qué esto importa?',
    'p5.concept.body': 'Al serializar, las librerías construyen primero un <code>StringBuilder</code> y luego lo convierten a bytes. Ghost pre-codifica en UTF-8 los nombres de los campos (<code>val H_NAME = "\"name\":".encodeUtf8()</code>) y los escribe <strong>directo al ByteArray de salida</strong>. Ningún String temporal. Ningún objeto intermedio.',
    'p5.values.title': 'Valores de la Instancia',
    'btn.serialize': '⚡ Ejecutar Serialización',
    'p5.headers.title': 'UTF-8 Cached Headers (Pre-compilados por KSP)',
    'p5.emission.title': 'Secuencia de Emisión (writer calls)',
    'p5.emission.initial': 'Pulsa el botón para ejecutar...',
    'p5.buffer.title': 'Buffer de Salida (ByteArray)',

    // Summary
    'summary.title': '🏆 ¿Por qué Ghost gana?',
    'summary.p1.title': 'Hash Perfecto O(1)',
    'summary.p1.desc': 'Dispatch matemático vs HashMap con String.equals()',
    'summary.p2.title': 'Bitmask Tracking',
    'summary.p2.desc': 'Un Long en el Stack vs HashSet en el Heap',
    'summary.p3.title': 'Zero-Alloc Matching',
    'summary.p3.desc': 'Comparación de bytes en hardware vs String.equals()',
    'summary.p4.title': 'Constructor Dispatch',
    'summary.p4.desc': 'Constructor nativo exacto vs Reflection + defaults',

    // Footer
    'footer.text': '👻 Ghost Compiler Lab · Construido con la velocidad del metal · Cero Allocations en el Hot Path',

    // Dynamic Logs
    'log.start': 'Iniciando KSP Compiler Pipeline...',
    'log.parsed': (n: number) => `DTO parseado. ${n} campo(s) detectado(s):`,
    'log.field': (name: string, type: string, hasDef: boolean) => `  — <strong>${name}</strong>: ${type} (default: ${hasDef})`,
    'log.pack.step': (name: string, packStr: string, key: number) => `  → Pack <strong>"${name}"</strong>: ${packStr} = <span class="text-indigo-400 font-bold font-mono">${key}</span>`,
    'log.hash.searching': 'Ejecutando PerfectHashFinder (búsqueda sin colisiones)...',
    'log.hash.trial': (m: number, s: number, slot: number) => `  — Evaluando Multiplier = ${m}, Shift = ${s} ... <span class="text-yellow-500 font-bold">Colisión</span> en slot ${slot}`,
    'log.hash.found': (m: number, s: number) => `¡Hash Perfecto! Multiplier = ${m}, Shift = ${s}`,
    'log.hash.fail': 'KSP Fatal: No se pudo resolver una distribución sin colisiones.',
    'log.error.nofields': 'KSP Error: No se encontraron campos "val" válidos en el DTO.',

    // Debugger Steps
    'dbg.prealloc.title': 'Asignación de Variables en la Pila (Stack)',
    'dbg.prealloc.desc': 'Ghost declara variables primitivas locales en el <strong>Stack</strong> — el espacio de memoria más rápido del procesador. Ningún objeto en el Heap todavía.',
    'dbg.begin.title': 'beginObject() — Inicio del Objeto JSON',
    'dbg.begin.desc': 'Ghost verifica que el primer byte sea <code>{</code> e incrementa el contador de profundidad. Sin tokenizadores pesados.',
    'dbg.loop.title': 'Bucle Principal while(true)',
    'dbg.loop.desc': 'Se inicia el loop de lectura del stream. Ghost lee bytes directamente del array y procesa campos sobre la marcha.',
    'dbg.select.title': (field: string) => `selectNameAndConsume() → Campo "${field}"`,
    'dbg.select.desc': (field: string, slot: number) => `<strong>Paso Crítico de Optimización:</strong> El lector extrae los bytes del nombre de la clave <code>"${field}"</code> directamente del stream JSON <em>sin crear un String</em> en memoria (cero allocations).<br><br>` +
      `<strong>¿Cómo funciona bajo el capó?</strong><br>` +
      `1. <strong>Byte Packing:</strong> Empaqueta los primeros 4 bytes leídos en un entero de 32 bits.<br>` +
      `2. <strong>Fórmula Perfect Hash:</strong> Calcula la posición matemática: <code>slot = ((packedKey * multiplier) + length) >>> shift & 1023</code>.<br>` +
      `3. <strong>Búsqueda O(1):</strong> Consulta el array de despacho en el slot <strong>${slot}</strong>.<br>` +
      `4. <strong>Verificación:</strong> Compara byte por byte contra los bytes estáticos esperados para evitar falsos positivos por colisión.<br><br>` +
      `Si coincide, avanza el puntero de lectura y retorna el slot (<strong>${slot}</strong>) para saltar inmediatamente a la rama correcta del bloque <code>when</code>.`,
    'dbg.branch.title': (slot: number) => `Salto a la Rama ${slot} (when dispatch)`,
    'dbg.branch.desc': 'El despachador <code>when</code> salta directamente al bloque del índice correcto. <strong>Cero iteraciones</strong> en listas o diccionarios.',
    'dbg.read.title': (field: string, val: any) => `Lectura del Valor: ${field} = ${val}`,
    'dbg.read.desc': (field: string, val: any) => `Los bytes se decodifican a tipo nativo y se asignan en la variable del Stack: <code>${field}Value = ${val}</code>. Sin crear Strings intermedios para primitivos.`,
    'dbg.mask.title': (field: string, bit: number) => `mask0 = mask0 or MASK_${field.toUpperCase()} (bit ${bit})`,
    'dbg.mask.desc': (field: string, bit: number) => `<strong>Pillar 2 en acción:</strong> Se enciende el bit <code>${bit}</code> para registrar que <code>"${field}"</code> fue leído exitosamente.<br><code>mask0 = mask0 or (1L shl ${bit})</code>`,
    'dbg.skip.title': () => `Campo Desconocido → skipValue()`,
    'dbg.skip.desc': (field: string) => `El campo <code>"${field}"</code> no pertenece a este DTO. Ghost salta su valor sin decodificarlo. No lanza excepción — leniente por diseño.`,
    'dbg.endloop.title': 'Fin del Objeto → break',
    'dbg.endloop.desc': 'El parser detecta <code>}</code>. <code>selectNameAndConsume()</code> retorna <strong>-1</strong>, rompiendo el bucle <code>while</code>.',
    'dbg.endobject.title': 'endObject() — Cierre del Objeto',
    'dbg.endobject.desc': 'Reduce el nivel de profundidad y valida el balance de llaves.',
    'dbg.validate.title': 'validateRequiredFields(mask0)',
    'dbg.validate.desc': (req: string) => `<strong>Validación O(1):</strong> <code>(mask0 and ${req}L) == ${req}L</code>. ¡Una operación AND. Cero bucles, cero instanciaciones!`,
    'dbg.return.title': '¡Deserialización Exitosa! — return BenchUser(...)',
    'dbg.return.desc': 'Se invoca el constructor nativo con las variables del Stack. <strong>Heap generado: Cero HashMaps. Cero iteradores. Cero basura.</strong>',
    'dbg.reset.title': 'Debugger Reiniciado',
    'dbg.reset.desc': 'Haz clic en <strong>"Paso Siguiente ⏵"</strong> para empezar de nuevo.',

    // KeyMatcher
    'km.len.check': (cl: number, tl: number) => `Verificar longitudes: candidato(${cl}) == esperado(${tl})`,
    'km.len.pass': 'Longitudes iguales. Procedemos a comparar los bytes.',
    'km.len.fail': 'Fallo instantáneo. Longitudes distintas → imposible coincidencia.',
    'km.block.title': (i: number) => `Bloque 4 bytes [${i}–${i+3}]`,
    'km.byte.check': (i: number, c: number, e: number, cc: string, ec: string) => `  Byte[${i}]: '${cc}'(${c}) == '${ec}'(${e})`,
    'km.byte.pass': 'Byte coincide ✓',
    'km.byte.fail': 'Byte diferente ✗ — fallo inmediato',
    'km.block.pass': 'Las 4 comparaciones del bloque pasan ✓',
    'km.remain.title': (i: number) => `Byte restante [${i}]`,

    // Dispatch
    'dispatch.if': (cond: string) => `if (${cond}) {`,
    'dispatch.else': 'else {',
    'dispatch.matched': '← RAMA ACTIVA',

    // Serializer
    'ser.begin': 'writer.beginObject()',
    'ser.begin.desc': 'Escribe la llave de apertura <code>{</code> en el ByteArray de salida.',
    'ser.comma': 'writer.writeComma()',
    'ser.comma.desc': 'Escribe el separador <code>,</code> directo en el buffer.',
    'ser.header': (f: string) => `writer.writeNameRaw(H_${f.toUpperCase()})`,
    'ser.header.desc': (f: string) => `Inyecta bytes pre-codificados de <code>H_${f.toUpperCase()}</code>. Sin codificación UTF-8 en caliente.`,
    'ser.value': (f: string) => `writer.value(value.${f})`,
    'ser.value.desc': (v: string) => `Escribe <code>${v}</code> directo al ByteArray. Sin Strings intermedios.`,
    'ser.end': 'writer.endObject()',
    'ser.end.desc': 'Inserta <code>}</code> en el buffer.',
  },

  en: {
    // Header
    'header.badge': 'Ghost Serialization Engine — KSP Lab',
    'header.tagline': 'A guided tour through the <strong>4 optimization pillars</strong> that make Ghost <strong class="text-sky-400">3× faster</strong> and <strong class="text-emerald-400">10× more RAM efficient</strong> than Moshi, Gson, and kotlinx.serialization.',

    // Nav Steps
    'nav.step1': 'KSP Compiler',
    'nav.step2': 'Bitmasks',
    'nav.step3': 'Key Matching',
    'nav.step4': 'Constructor',
    'nav.step5': 'Serialize',

    // Pillar 1
    'p1.badge': 'Pillar 1 — Perfect Hash O(1): The Zero-Lookup Dispatcher',
    'p1.concept.title': 'Why does this matter?',
    'p1.concept.body': 'Traditional libraries like Gson use a <code>HashMap&lt;String, Field&gt;</code> to find which field matches each JSON key — this requires computing the hash of every String, searching the table, and comparing with <code>equals()</code>. Ghost eliminates all of that: the <strong>KSP Compiler</strong> pre-calculates a <strong>collision-free perfect hash</strong> at compile time. At runtime, finding a field is a direct mathematical <strong>O(1)</strong> operation on raw bytes.',
    'p1.input.label': 'Your Kotlin Data Class',
    'p1.substep1': '1. Byte Packing',
    'p1.substep2': '2. Dispatch Table',
    'p1.substep3': '3. Generated Code',
    'btn.compile': 'Run KSP Compiler',
    'p1.terminal.title': 'Terminal — KSP Output',
    'p1.terminal.ready': 'ready',
    'p1.terminal.waiting': '> Press "Run KSP Compiler" to start...',
    'p1.pack.title': 'Step 1 of 3 — Byte Packing (4 bytes → Int32)',
    'p1.pack.field': 'Current field:',
    'p1.pack.int32': 'Int32 (key)',
    'p1.pack.byte3': 'Byte 3 (bits 24-31)',
    'p1.pack.byte2': 'Byte 2 (bits 16-23)',
    'p1.pack.byte1': 'Byte 1 (bits 8-15)',
    'p1.pack.byte0': 'Byte 0 (bits 0-7)',
    'p1.pack.formula.label': 'Real formula (PerfectHashFinder.kt):',
    'p1.grid.title': 'Step 2 of 3 — Dispatch Table O(1) (1024 slots)',
    'p1.grid.hint': '👉 Hover over a green slot',
    'p1.grid.desc': 'Each <span class="text-emerald-400 font-bold">green</span> dot = a DTO field mapped without collisions. Gray slots are empty.',
    'p1.grid.empty': 'Run the compiler to see the generated code and explore the math behind each slot.',
    'p1.code.title': 'Step 3 of 3 — KSP Generated Code (JsonReaderOptions):',
    'p1.math.field.prefix': 'Field:',
    'p1.math.slot.prefix': '→ Slot',
    'p1.math.close': '✕ Close',
    'p1.math.step1': '1. Field bytes:',
    'p1.math.step2': '2. key × Multiplier:',
    'p1.math.step3': '3. + len (anti-collision):',
    'p1.math.step4': '4. >> Shift (CPU):',
    'p1.math.step5': '5. & 1023 (final slot):',
    'p1.cta.text': '✅ <strong>Pillar 1 complete.</strong> KSP generated the O(1) dispatch table. Next: how Ghost tracks which fields it read without allocating anything.',
    'btn.next.p2': 'Next: Bitmask State Tracking →',

    // Pillar 2
    'p2.badge': 'Pillar 2 — Bitmask State Tracking: Field Tracking Without Memory Allocation',
    'p2.concept.title': 'Why does this matter?',
    'p2.concept.body': 'Traditional libraries use a <code>HashSet&lt;String&gt;</code> to remember which fields they\'ve already read. Every insertion = <strong>a new Heap object = GC pressure</strong>. Ghost uses a single integer (<code>Long</code>, 64 bits) on the <strong>Stack</strong>. Activating a field = <strong>1 OR instruction</strong>. Validating all required fields = <strong>1 AND instruction</strong>. Zero allocations.<br><br><strong class="text-emerald-300">Click "Next Step ⏵" to follow the deserialization step by step.</strong>',
    'btn.load.json': 'Load JSON',
    'btn.restart': 'Restart ⟳',
    'btn.next.step': 'Next Step ⏵',
    'btn.restart.debugger': 'Restart Debugger ⟳',
    'p2.code.title': 'GhostJsonFlatReader.kt',
    'p2.json.title': 'JSON Stream (Raw ByteArray)',
    'p2.json.pos': 'Pos:',
    'p2.stack.title': 'Variables (Stack)',
    'p2.bitmask.title': 'Bitmask (mask0)',
    'p2.step.initial': 'Waiting...',
    'p2.step.initial.desc': 'Click <strong>"Next Step ⏵"</strong> to start the deserialization simulation.',
    'p2.cta.text': '✅ <strong>Pillar 2 complete.</strong> You saw how mask0 lit up bit by bit, without creating any objects. Next: byte comparison without creating Strings.',
    'btn.next.p3': 'Next: Zero-Allocation Key Matching →',

    // Pillar 3
    'p3.badge': 'Pillar 3 — Zero-Allocation Key Matching: Unrolled Byte Comparison',
    'p3.concept.title': 'Why does this matter?',
    'p3.concept.body': 'When the hash says "I think this field is <code>name</code>", Ghost needs to <strong>confirm</strong> that the bytes read are actually <code>n-a-m-e</code>. Normally this would mean calling <code>String.equals()</code> — which allocates a String. Ghost compares bytes directly in 4-byte blocks, with <strong>4 individual byte comparisons per iteration</strong> inside <code>verifyKeyMatch()</code>. No objects created.<br><br><strong class="text-pink-300">Enter two words below and see the byte-by-byte process.</strong>',
    'p3.expected.label': 'Expected key (KSP compiled constant)',
    'p3.candidate.label': 'Candidate read from JSON',
    'p3.hint': 'Try: "name" (match), "nane" (length collision), "na" (different length)',
    'btn.run.keymatch': '⚡ Run verifyKeyMatch()',
    'btn.run.keymatch.badge': 'Ready',
    'p3.trace.title': 'CPU Trace — verifyKeyMatch() — Zero Strings Created',
    'p3.trace.initial': 'Enter data and press the button to see the byte-by-byte comparison...',
    'p3.field_selector.title': 'Select field to test:',
    'p3.result.match': 'MATCH ✓',
    'p3.result.fail': 'NO MATCH ✗',
    'p3.result.match.sub': 'Bytes match exactly. Ghost accepts the field.',
    'p3.result.fail.sub': 'Difference detected. Ghost discards the candidate.',
    'p3.cta.text': '✅ <strong>Pillar 3 complete.</strong> You saw hardware-level byte comparison without creating Strings. Last pillar: Constructor Dispatch.',
    'btn.next.p4': 'Next: Constructor Dispatch →',

    // Pillar 4
    'p4.badge': 'Pillar 4 — Constructor Dispatch: Native Default Injection Without Reflection',
    'p4.concept.title': 'Why does this matter?',
    'p4.concept.body': 'What if the JSON doesn\'t include an optional field like <code>score</code>? Traditional libraries use <strong>Reflection</strong> or assign <code>null</code> and replace it with the default. Ghost uses the value of <code>mask0</code> to know exactly which fields arrived and calls the <strong>exact constructor</strong> that injects only the necessary defaults. KSP generates <strong>2ⁿ code branches</strong> — one per possible combination of optional fields.<br><br><strong class="text-yellow-300">Check/uncheck the fields that "arrived" in the JSON and observe which branch activates.</strong>',
    'p4.fields.title': 'Fields present in JSON (simulates mask0)',
    'p4.mask.label': 'mask0 =',
    'p4.branches.title': 'KSP Generated Code Branches',
    'p4.field.required': 'Required (always present)',
    'p4.field.optional': 'Optional (has default)',
    'p4.branch.matched': '← ACTIVE BRANCH',
    'p4.cta.text': '✅ <strong>All 4 pillars explained!</strong> Now: how Ghost serializes an object back to JSON with equal efficiency.',
    'btn.next.p5': 'Next: Direct Serialization →',

    // Pillar 5
    'p5.badge': 'Bonus — Direct ByteArray Serialization: Zero Intermediate Strings',
    'p5.concept.title': 'Why does this matter?',
    'p5.concept.body': 'When serializing, libraries typically build a <code>StringBuilder</code> first and then convert to bytes. Ghost pre-encodes field names in UTF-8 (<code>val H_NAME = "\"name\":".encodeUtf8()</code>) and writes them <strong>directly to the output ByteArray</strong>. No temporary Strings. No intermediate objects.',
    'p5.values.title': 'Instance Values',
    'btn.serialize': '⚡ Run Serialization',
    'p5.headers.title': 'UTF-8 Cached Headers (KSP Pre-compiled)',
    'p5.emission.title': 'Emission Sequence (writer calls)',
    'p5.emission.initial': 'Press the button to execute...',
    'p5.buffer.title': 'Output Buffer (ByteArray)',

    // Summary
    'summary.title': '🏆 Why Does Ghost Win?',
    'summary.p1.title': 'Perfect Hash O(1)',
    'summary.p1.desc': 'Mathematical dispatch vs HashMap with String.equals()',
    'summary.p2.title': 'Bitmask Tracking',
    'summary.p2.desc': 'One Long on the Stack vs HashSet on the Heap',
    'summary.p3.title': 'Zero-Alloc Matching',
    'summary.p3.desc': 'Hardware byte comparison vs String.equals()',
    'summary.p4.title': 'Constructor Dispatch',
    'summary.p4.desc': 'Exact native constructor vs Reflection + defaults',

    // Footer
    'footer.text': '👻 Ghost Compiler Lab · Built at the speed of metal · Zero Allocations on the Hot Path',

    // Dynamic Logs
    'log.start': 'Starting KSP Compiler Pipeline...',
    'log.parsed': (n: number) => `DTO parsed successfully. ${n} field(s) detected:`,
    'log.field': (name: string, type: string, hasDef: boolean) => `  — <strong>${name}</strong>: ${type} (default: ${hasDef})`,
    'log.pack.step': (name: string, packStr: string, key: number) => `  → Pack <strong>"${name}"</strong>: ${packStr} = <span class="text-indigo-400 font-bold font-mono">${key}</span>`,
    'log.hash.searching': 'Running PerfectHashFinder (searching for zero-collision distribution)...',
    'log.hash.trial': (m: number, s: number, slot: number) => `  — Trying Multiplier = ${m}, Shift = ${s} ... <span class="text-yellow-500 font-bold">Collision</span> at slot ${slot}`,
    'log.hash.found': (m: number, s: number) => `Perfect Hash Found! Multiplier = ${m}, Shift = ${s}`,
    'log.hash.fail': 'KSP Fatal: Could not resolve a collision-free distribution.',
    'log.error.nofields': 'KSP Error: No valid "val" fields found in the DTO.',

    // Debugger Steps
    'dbg.prealloc.title': 'Stack Variable Pre-allocation',
    'dbg.prealloc.desc': 'Ghost declares primitive local variables on the <strong>Stack</strong> — the fastest memory region available to the processor. No Heap objects yet.',
    'dbg.begin.title': 'beginObject() — JSON Object Start',
    'dbg.begin.desc': 'Ghost verifies the first byte is <code>{</code> and increments the depth counter. No heavy tokenizers instantiated.',
    'dbg.loop.title': 'Main Loop while(true)',
    'dbg.loop.desc': 'The stream reading loop begins. Ghost reads bytes directly from the array and processes fields on the fly.',
    'dbg.select.title': (field: string) => `selectNameAndConsume() → Field "${field}"`,
    'dbg.select.desc': (field: string, slot: number) => `<strong>Critical Optimization Step:</strong> The reader extracts the key name bytes <code>"${field}"</code> directly from the raw JSON stream <em>without allocating a String</em> object on the Heap (zero allocations).<br><br>` +
      `<strong>How does it work under the hood?</strong><br>` +
      `1. <strong>Byte Packing:</strong> Packs the first 4 bytes read into a single 32-bit integer.<br>` +
      `2. <strong>Perfect Hash Formula:</strong> Computes the mathematical position: <code>slot = ((packedKey * multiplier) + length) >>> shift & 1023</code>.<br>` +
      `3. <strong>O(1) Lookup:</strong> Queries the dispatch array at slot <strong>${slot}</strong>.<br>` +
      `4. <strong>Verification:</strong> Performs a byte-by-byte comparison against the expected static bytes to guard against collision false-positives.<br><br>` +
      `If it matches, it consumes the key, advances the reader pointer, and returns the slot index (<strong>${slot}</strong>) to jump directly to the correct branch inside the <code>when</code> block.`,
    'dbg.branch.title': (slot: number) => `Jump to Branch ${slot} (when dispatch)`,
    'dbg.branch.desc': 'The <code>when</code> dispatcher jumps directly to the correct index block. <strong>Zero iterations</strong> through lists or dictionaries.',
    'dbg.read.title': (field: string, val: any) => `Read Value: ${field} = ${val}`,
    'dbg.read.desc': (field: string, val: any) => `Bytes are decoded to native type and assigned to the Stack variable: <code>${field}Value = ${val}</code>. No intermediate String objects for primitive types.`,
    'dbg.mask.title': (field: string, bit: number) => `mask0 = mask0 or MASK_${field.toUpperCase()} (bit ${bit})`,
    'dbg.mask.desc': (field: string, bit: number) => `<strong>Pillar 2 in action:</strong> Bit <code>${bit}</code> is set to record that <code>"${field}"</code> was successfully read.<br><code>mask0 = mask0 or (1L shl ${bit})</code>`,
    'dbg.skip.title': () => `Unknown Field → skipValue()`,
    'dbg.skip.desc': (field: string) => `Field <code>"${field}"</code> doesn't belong to this DTO. Ghost skips its value without decoding it. No exception thrown — lenient by design.`,
    'dbg.endloop.title': 'End of Object → break',
    'dbg.endloop.desc': 'The parser detects <code>}</code>. <code>selectNameAndConsume()</code> returns <strong>-1</strong>, breaking the <code>while</code> loop.',
    'dbg.endobject.title': 'endObject() — Object Closing',
    'dbg.endobject.desc': 'Decrements the recursion depth level and validates brace balance.',
    'dbg.validate.title': 'validateRequiredFields(mask0)',
    'dbg.validate.desc': (req: string) => `<strong>O(1) Validation:</strong> <code>(mask0 and ${req}L) == ${req}L</code>. One AND operation. Zero loops. Zero instantiations!`,
    'dbg.return.title': 'Deserialization Successful! — return BenchUser(...)',
    'dbg.return.desc': 'The DTO\'s native constructor is called with Stack variables. <strong>Heap generated: Zero HashMaps. Zero iterators. Zero garbage.</strong>',
    'dbg.reset.title': 'Debugger Reset',
    'dbg.reset.desc': 'Click <strong>"Next Step ⏵"</strong> to start again.',

    // KeyMatcher
    'km.len.check': (cl: number, tl: number) => `Check lengths: candidate(${cl}) == expected(${tl})`,
    'km.len.pass': 'Lengths match. Proceeding to compare bytes.',
    'km.len.fail': 'Instant fail. Different lengths → cannot be a match.',
    'km.block.title': (i: number) => `4-byte block [${i}–${i+3}]`,
    'km.byte.check': (i: number, c: number, e: number, cc: string, ec: string) => `  Byte[${i}]: '${cc}'(${c}) == '${ec}'(${e})`,
    'km.byte.pass': 'Byte matches ✓',
    'km.byte.fail': 'Byte differs ✗ — immediate fail',
    'km.block.pass': 'All 4 block comparisons pass ✓',
    'km.remain.title': (i: number) => `Remaining byte [${i}]`,

    // Dispatch
    'dispatch.if': (cond: string) => `if (${cond}) {`,
    'dispatch.else': 'else {',
    'dispatch.matched': '← ACTIVE BRANCH',

    // Serializer
    'ser.begin': 'writer.beginObject()',
    'ser.begin.desc': 'Writes the opening brace <code>{</code> to the ByteArray output.',
    'ser.comma': 'writer.writeComma()',
    'ser.comma.desc': 'Writes the separator <code>,</code> directly to the buffer.',
    'ser.header': (f: string) => `writer.writeNameRaw(H_${f.toUpperCase()})`,
    'ser.header.desc': (f: string) => `Injects pre-encoded bytes of <code>H_${f.toUpperCase()}</code>. No hot-path UTF-8 encoding.`,
    'ser.value': (f: string) => `writes <code>${f}</code> directly to the ByteArray. No intermediate Strings.`,
    'ser.value.desc': (v: string) => `Writes <code>${v}</code> directly to the ByteArray. No intermediate Strings.`,
    'ser.end': 'writer.endObject()',
    'ser.end.desc': 'Inserts <code>}</code> into the buffer.',
  }
};

export function translate(key: string, lang: Lang, ...args: any[]): string {
  const strings = TRANSLATIONS[lang];
  const val = strings?.[key];
  if (val === undefined) {
    console.warn(`[GhostI18n] Missing key: "${key}" (lang: "${lang}")`);
    return key;
  }
  return typeof val === 'function' ? val(...args) : val;
}
