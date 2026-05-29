/**
 * Ghost Compiler Lab — Constructor Dispatch Simulation Module (TypeScript)
 */

import { FieldMetadata } from "./compiler";

export interface DispatchBranch {
  index: number;
  constName: string;
  condition: string;
  code: string;
  isMatched: boolean;
  isElse: boolean;
}

export interface DispatchResult {
  branches: DispatchBranch[];
  triggeredIndex: number;
}

interface Combo {
  bitMask: number;
  fields: FieldMetadata[];
}

function getCombinations(defaultFields: FieldMetadata[]): Combo[] {
  const n = defaultFields.length;
  const combos: Combo[] = [];

  // Generate 2^n - 1 combinations
  for (let bits = (1 << n) - 1; bits >= 1; bits--) {
    const fields = defaultFields.filter((_, i) => (bits >> i) & 1);
    combos.push({ bitMask: bits, fields });
  }

  // Sort descending by complexity (most defaults present first)
  combos.sort((a, b) => b.fields.length - a.fields.length);
  return combos;
}

/**
 * Simulates the dispatch decision given the current field selection.
 */
export function simulateDispatch(allFields: FieldMetadata[], presentFieldNames: string[]): DispatchResult {
  const defaultFields = allFields.filter(f => f.hasDefault);
  const requiredFields = allFields.filter(f => !f.hasDefault);
  const combos = getCombinations(defaultFields);
  const branches: DispatchBranch[] = [];

  combos.forEach((combo, idx) => {
    const constName = 'MASK_OPTS_' + combo.fields.map(f => f.name.toUpperCase()).sort().join('_');
    const condition = `(mask0 and ${constName}) == ${constName}`;

    // Branch matches when ALL optional fields in this combo are present
    const isMatched = combo.fields.every(f => presentFieldNames.includes(f.name));

    const constructorArgs = [
      ...requiredFields.map(f => `${f.name} = ${f.name}Value`),
      ...combo.fields.map(f => `${f.name} = ${f.name}Value`),
    ].join(',\n    ');

    branches.push({
      index: idx,
      constName,
      condition,
      code: `return BenchUser(\n    ${constructorArgs}\n)`,
      isMatched,
      isElse: false,
    });
  });

  // Fallback branch (no optional fields received)
  const fallbackArgs = requiredFields.map(f => `${f.name} = ${f.name}Value`).join(',\n    ');
  const fallbackCode = `return BenchUser(\n    ${fallbackArgs || '/* no required fields */'}\n)`;
  const fallbackMatch = !branches.some(b => b.isMatched);

  branches.push({
    index: branches.length,
    constName: 'FALLBACK',
    condition: 'else',
    code: fallbackCode,
    isMatched: fallbackMatch,
    isElse: true,
  });

  const triggeredIndex = branches.findIndex(b => b.isMatched);
  return { branches, triggeredIndex };
}
