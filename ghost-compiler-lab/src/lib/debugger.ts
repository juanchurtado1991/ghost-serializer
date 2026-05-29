/**
 * Ghost Compiler Lab — Bitmask Debugger Module (TypeScript)
 */

import { FieldMetadata } from "./compiler";

export interface DebuggerStep {
  titleKey: string;
  titleParams: string[];
  descKey: string;
  descParams: string[];
  line: number;
  cursor: [number, number];
  colorClass: string;
  action?: (state: DebuggerState) => DebuggerState;
}

export interface DebuggerState {
  variables: Record<string, string>;
  mask0: bigint;
  currentStepIndex: number;
  completed: boolean;
}

export function defaultLiteral(type: string): string {
  switch (type) {
    case 'Int': return '0';
    case 'Long': return '0L';
    case 'Double': return '0.0';
    case 'Float': return '0.0f';
    case 'Boolean': return 'false';
    default: return 'null';
  }
}

export function readMethod(type: string): string {
  switch (type) {
    case 'Int': return 'nextInt()';
    case 'Long': return 'nextLong()';
    case 'Double': return 'nextDouble()';
    case 'Float': return 'nextFloat()';
    case 'Boolean': return 'nextBoolean()';
    default: return 'nextString()';
  }
}

/**
 * Generates the lines of Kotlin code to display in the IDE view.
 */
export function generateKotlinCodeLines(fields: FieldMetadata[], traceData: Record<string, any>): string[] {
  const lines: string[] = [];

  lines.push(`override fun deserialize(reader: GhostJsonFlatReader): BenchUser {`);
  lines.push(`  // Stack pre-allocation — zero Heap objects`);

  fields.forEach(f => {
    const init = defaultLiteral(f.type);
    lines.push(`  var ${f.name}Value: ${f.type}${f.isNullable ? '?' : ''} = ${init}`);
  });

  lines.push(`  var mask0 = 0L`);
  lines.push(``);
  lines.push(`  reader.beginObject()`);
  lines.push(`  while (true) {`);
  lines.push(`    val index = reader.selectNameAndConsume(OPTIONS)`);
  lines.push(`    when (index) {`);

  fields.forEach(f => {
    const slot = Object.keys(traceData).find(k => traceData[k].name === f.name) ?? '?';
    const method = readMethod(f.type);
    lines.push(`      ${slot} -> { // "${f.name}"`);
    lines.push(`        ${f.name}Value = reader.${method}`);
    lines.push(`        mask0 = mask0 or MASK_${f.name.toUpperCase()}`);
    lines.push(`      }`);
  });

  lines.push(`      -1 -> break`);
  lines.push(`      else -> reader.skipValue()`);
  lines.push(`    } // end when`);
  lines.push(`  } // end while`);
  lines.push(`  reader.endObject()`);
  lines.push(``);
  lines.push(`  validateRequiredFields(mask0)`);

  const args = fields.map(f => `${f.name} = ${f.name}Value${!f.isNullable && f.type === 'String' ? '!!' : ''}`).join(', ');
  lines.push(`  return BenchUser(${args})`);
  lines.push(`}`);

  return lines;
}

/**
 * Builds the array of steps for the debugger based on parsed JSON data class attributes.
 */
export function buildDebuggerSteps(
  jsonStr: string,
  fields: FieldMetadata[],
  traceData: Record<string, any>,
  kotlinCodeLines: string[]
): DebuggerStep[] {
  const steps: DebuggerStep[] = [];
  let parsed: Record<string, any> = {};
  try {
    parsed = JSON.parse(jsonStr);
  } catch (_) {
    // Return empty steps if JSON is malformed
    return [];
  }

  const findLineIdx = (search: string, startFrom = 0): number => {
    for (let i = startFrom; i < kotlinCodeLines.length; i++) {
      if (kotlinCodeLines[i].includes(search)) {
        return i;
      }
    }
    return -1;
  };

  const preallocLine = findLineIdx('// Stack pre-allocation');
  const beginObjLine = findLineIdx('reader.beginObject()');
  const whileLine = findLineIdx('while (true)');
  const endLoopLine = findLineIdx('-1 -> break');
  const skipLine = findLineIdx('else -> reader.skipValue()');
  const endObjLine = findLineIdx('reader.endObject()');
  const validateLine = findLineIdx('validateRequiredFields(mask0)');
  const returnLine = findLineIdx('return BenchUser');

  // Step 1: Pre-allocation
  steps.push({
    titleKey: 'dbg.prealloc.title',
    titleParams: [],
    descKey: 'dbg.prealloc.desc',
    descParams: [],
    line: preallocLine !== -1 ? preallocLine : 1,
    cursor: [-1, -1],
    colorClass: '',
  });

  // Step 2: beginObject()
  steps.push({
    titleKey: 'dbg.begin.title',
    titleParams: [],
    descKey: 'dbg.begin.desc',
    descParams: [],
    line: beginObjLine !== -1 ? beginObjLine : 5,
    cursor: [0, 0],
    colorClass: 'cursor-active',
  });

  // Step 3: Loop start
  steps.push({
    titleKey: 'dbg.loop.title',
    titleParams: [],
    descKey: 'dbg.loop.desc',
    descParams: [],
    line: whileLine !== -1 ? whileLine : 6,
    cursor: [-1, -1],
    colorClass: '',
  });

  const jsonKeys = Object.keys(parsed);
  jsonKeys.forEach(key => {
    const rawValue = parsed[key];
    const keyStart = jsonStr.indexOf(`"${key}"`);
    const keyEnd = keyStart + key.length + 1;
    const fieldIdx = fields.findIndex(f => f.name === key);
    const field = fields[fieldIdx];
    const slot = Object.keys(traceData).find(k => traceData[k].name === key) ?? '?';

    // selectNameAndConsume
    const selectLine = findLineIdx('selectNameAndConsume(OPTIONS)');
    steps.push({
      titleKey: 'dbg.select.title',
      titleParams: [key],
      descKey: 'dbg.select.desc',
      descParams: [key, String(slot)],
      line: selectLine !== -1 ? selectLine : 7,
      cursor: [keyStart, keyEnd],
      colorClass: 'cursor-active',
    });

    if (field !== undefined) {
      const branchLine = findLineIdx(`${slot} -> {`);

      // Branch match jump
      steps.push({
        titleKey: 'dbg.branch.title',
        titleParams: [String(slot)],
        descKey: 'dbg.branch.desc',
        descParams: [],
        line: branchLine !== -1 ? branchLine : 8,
        cursor: [keyStart, keyEnd],
        colorClass: 'cursor-active',
      });

      // Read value
      const valStart = jsonStr.indexOf(JSON.stringify(rawValue), keyEnd);
      const valEnd = valStart + JSON.stringify(rawValue).length - 1;
      const valStr = String(rawValue);

      steps.push({
        titleKey: 'dbg.read.title',
        titleParams: [key, valStr],
        descKey: 'dbg.read.desc',
        descParams: [key, valStr],
        line: branchLine !== -1 ? branchLine + 1 : 9,
        cursor: [valStart, valEnd],
        colorClass: 'cursor-active',
        action: (state) => ({
          ...state,
          variables: {
            ...state.variables,
            [key]: valStr,
          },
        }),
      });

      // Bitmask OR operation
      const bitIndex = BigInt(fieldIdx);
      steps.push({
        titleKey: 'dbg.mask.title',
        titleParams: [key, String(fieldIdx)],
        descKey: 'dbg.mask.desc',
        descParams: [key, String(fieldIdx)],
        line: branchLine !== -1 ? branchLine + 2 : 10,
        cursor: [valStart, valEnd],
        colorClass: 'cursor-active',
        action: (state) => ({
          ...state,
          mask0: state.mask0 | (1n << bitIndex),
        }),
      });
    } else {
      // Skip value if unknown
      steps.push({
        titleKey: 'dbg.skip.title',
        titleParams: [],
        descKey: 'dbg.skip.desc',
        descParams: [key],
        line: skipLine !== -1 ? skipLine : 11,
        cursor: [keyStart, keyEnd],
        colorClass: 'cursor-active',
      });
    }
  });

  // End of loop step
  const closingBrace = jsonStr.lastIndexOf('}');
  steps.push({
    titleKey: 'dbg.endloop.title',
    titleParams: [],
    descKey: 'dbg.endloop.desc',
    descParams: [],
    line: endLoopLine !== -1 ? endLoopLine : 12,
    cursor: [closingBrace, closingBrace],
    colorClass: 'cursor-active',
  });

  // endObject()
  steps.push({
    titleKey: 'dbg.endobject.title',
    titleParams: [],
    descKey: 'dbg.endobject.desc',
    descParams: [],
    line: endObjLine !== -1 ? endObjLine : 13,
    cursor: [closingBrace, closingBrace],
    colorClass: '',
  });

  // validateRequiredFields
  let reqMask = 0n;
  fields.forEach((f, idx) => {
    if (!f.isNullable && !f.hasDefault) {
      reqMask |= (1n << BigInt(idx));
    }
  });

  steps.push({
    titleKey: 'dbg.validate.title',
    titleParams: [],
    descKey: 'dbg.validate.desc',
    descParams: [reqMask.toString()],
    line: validateLine !== -1 ? validateLine : 14,
    cursor: [closingBrace, closingBrace],
    colorClass: '',
  });

  // Return DTO
  steps.push({
    titleKey: 'dbg.return.title',
    titleParams: [],
    descKey: 'dbg.return.desc',
    descParams: [],
    line: returnLine !== -1 ? returnLine : 15,
    cursor: [closingBrace, closingBrace],
    colorClass: '',
    action: (state) => ({
      ...state,
      completed: true,
    }),
  });

  return steps;
}
