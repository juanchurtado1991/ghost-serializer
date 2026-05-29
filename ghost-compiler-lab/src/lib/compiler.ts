/**
 * Ghost Compiler Lab — KSP Compiler Simulation Module (TypeScript)
 */

export interface FieldMetadata {
  name: string;
  type: string;
  isNullable: boolean;
  hasDefault: boolean;
}

export interface PackedField {
  name: string;
  key: number;
  len: number;
  packStr: string;
}

export interface TraceInfo {
  name: string;
  key: number;
  packStr: string;
  len: number;
  m: number;
  s: number;
  keyM: number;
  added: number;
  shifted: number;
}

export interface HashResult {
  found: boolean;
  m: number;
  s: number;
  dispatch: number[];
  traceData: Record<number, TraceInfo>;
  trials?: Array<{ m: number; s: number; collisionSlot: number }>;
}

const HASH_TABLE_SIZE = 1024;
const HASH_TABLE_MASK = HASH_TABLE_SIZE - 1; // 0x3FF
const HASH_MULTIPLIER_START = 31;
const HASH_MULTIPLIER_LIMIT = 2000;
const HASH_MULTIPLIER_STEP = 2;
const HASH_SHIFT_LIMIT = 16;
const BYTE_MASK = 0xFF;

/**
 * Parses a Kotlin data class string to extract field metadata.
 */
export function parseFields(dtoText: string): FieldMetadata[] {
  const results: FieldMetadata[] = [];
  const pattern = /val\s+(\w+)\s*:\s*([\w<>?]+)(\s*=\s*[^\n,)]+)?/g;
  let match;
  while ((match = pattern.exec(dtoText)) !== null) {
    results.push({
      name: match[1],
      type: match[2].replace('?', ''),
      isNullable: match[2].endsWith('?'),
      hasDefault: match[3] !== undefined,
    });
  }
  return results;
}

/**
 * Packs the first 4 bytes of a field name into a little-endian Int32.
 */
export function packFieldName(name: string): { key: number; bytes: number[]; packStr: string } {
  const bytes = [0, 0, 0, 0];
  const parts: string[] = [];
  let key = 0;

  for (let i = 0; i < 4; i++) {
    if (i < name.length) {
      bytes[i] = name.charCodeAt(i) & BYTE_MASK;
      key |= (bytes[i] << (i * 8));
      const shift = i > 0 ? ` shl ${i * 8}` : '';
      parts.push(`'${name[i]}'(${bytes[i]}${shift})`);
    }
  }

  key = key | 0; // Treat as signed 32-bit int

  return { key, bytes, packStr: parts.join(' + ') };
}

/**
 * Search for a (multiplier, shift) pair that maps all field names to unique slots.
 */
export function findPerfectHash(packedFields: PackedField[]): HashResult {
  const trials: Array<{ m: number; s: number; collisionSlot: number }> = [];
  for (let m = HASH_MULTIPLIER_START; m <= HASH_MULTIPLIER_LIMIT; m += HASH_MULTIPLIER_STEP) {
    for (let s = 0; s <= HASH_SHIFT_LIMIT; s++) {
      const tempDispatch = new Array<number>(HASH_TABLE_SIZE).fill(-1);
      const tempTrace: Record<number, TraceInfo> = {};
      let hasCollision = false;
      let collisionSlot = -1;

      for (let i = 0; i < packedFields.length; i++) {
        const { key, len, name, packStr } = packedFields[i];
        const keyM = Math.imul(key, m);
        const added = (keyM + len) | 0;
        const slot = (added >> s) & HASH_TABLE_MASK;

        if (tempDispatch[slot] !== -1) {
          hasCollision = true;
          collisionSlot = slot;
          break;
        }

        tempDispatch[slot] = i;
        tempTrace[slot] = {
          name,
          key,
          packStr,
          len,
          m,
          s,
          keyM,
          added,
          shifted: added >> s,
        };
      }

      if (hasCollision) {
        if (trials.length < 5) {
          trials.push({ m, s, collisionSlot });
        }
      } else {
        return { found: true, m, s, dispatch: tempDispatch, traceData: tempTrace, trials };
      }
    }
  }

  return {
    found: false,
    m: HASH_MULTIPLIER_START,
    s: 0,
    dispatch: new Array<number>(HASH_TABLE_SIZE).fill(-1),
    traceData: {},
    trials,
  };
}
