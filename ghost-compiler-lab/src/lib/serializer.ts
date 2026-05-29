/**
 * Ghost Compiler Lab — Serialization Module (TypeScript)
 */

import { FieldMetadata } from "./compiler";

export interface SerializerStep {
  type: 'structure' | 'comma' | 'header' | 'value';
  callKey: string;
  callArgs: any[];
  descKey: string;
  descArgs: any[];
  chunk: string;
}

export interface SerializerResult {
  output: string;
  steps: SerializerStep[];
}

export function defaultInputValue(field: FieldMetadata): string {
  if (field.type === 'Int' || field.type === 'Long') return '42';
  if (field.type === 'Double' || field.type === 'Float') return '99.9';
  if (field.name === 'email') return 'info@ghost.com';
  if (field.name === 'name') return 'Ghost';
  return 'value';
}

export function toJsonValue(type: string, raw: any): string {
  if (type === 'String') return `"${raw}"`;
  if (type === 'Boolean') return raw ? 'true' : 'false';
  return String(raw);
}

/**
 * Simulates serialization for the fields with given values.
 */
export function simulateSerialization(fields: FieldMetadata[], values: Record<string, any>): SerializerResult {
  const steps: SerializerStep[] = [];
  let output = '';

  // beginObject
  steps.push({
    type: 'structure',
    callKey: 'ser.begin',
    callArgs: [],
    descKey: 'ser.begin.desc',
    descArgs: [],
    chunk: '{',
  });
  output += '{';

  fields.forEach((field, idx) => {
    const rawVal = values[field.name];
    const jsonVal = toJsonValue(field.type, rawVal);

    // Comma separator
    if (idx > 0) {
      steps.push({
        type: 'comma',
        callKey: 'ser.comma',
        callArgs: [],
        descKey: 'ser.comma.desc',
        descArgs: [],
        chunk: ',',
      });
      output += ',';
    }

    // writeNameRaw
    steps.push({
      type: 'header',
      callKey: 'ser.header',
      callArgs: [field.name],
      descKey: 'ser.header.desc',
      descArgs: [field.name],
      chunk: `"${field.name}":`,
    });
    output += `"${field.name}":`;

    // value
    steps.push({
      type: 'value',
      callKey: 'ser.value',
      callArgs: [field.name],
      descKey: 'ser.value.desc',
      descArgs: [jsonVal],
      chunk: jsonVal,
    });
    output += jsonVal;
  });

  // endObject
  steps.push({
    type: 'structure',
    callKey: 'ser.end',
    callArgs: [],
    descKey: 'ser.end.desc',
    descArgs: [],
    chunk: '}',
  });
  output += '}';

  return { output, steps };
}

/**
 * Returns the byte representation of the serialized output string.
 */
export function getByteRepresentation(output: string, mode: 'hex' | 'ascii'): { length: number; content: string } {
  if (!output) return { length: 0, content: '' };

  const bytes = new TextEncoder().encode(output);

  if (mode === 'hex') {
    return {
      length: bytes.length,
      content: Array.from(bytes)
        .map(b => b.toString(16).padStart(2, '0').toUpperCase())
        .join(' '),
    };
  }

  return { length: bytes.length, content: output };
}
