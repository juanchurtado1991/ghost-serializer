/**
 * Ghost Compiler Lab — Key Matching Simulation Module (TypeScript)
 */

export interface KeyMatchStep {
  type: 'length' | 'block4' | 'remainder' | 'byte-in-block';
  checkKey?: string;
  checkArgs?: any[];
  passKey?: string;
  passArgs?: any[];
  pass?: boolean;
  titleKey?: string;
  titleArgs?: any[];
  subSteps?: KeyMatchStep[];
  allPass?: boolean;
}

export interface KeyMatchResult {
  matches: boolean;
  steps: KeyMatchStep[];
}

/**
 * Simulates verifyKeyMatch() with a visual step trace.
 */
export function simulateKeyMatch(candidate: string, expected: string): KeyMatchResult {
  const encoder = new TextEncoder();
  const candBytes = encoder.encode(candidate);
  const expBytes = encoder.encode(expected);
  const steps: KeyMatchStep[] = [];
  let matches = false;

  // ── Step 1: Length check ──
  const lenMatch = candBytes.length === expBytes.length;
  steps.push({
    type: 'length',
    checkKey: 'km.len.check',
    checkArgs: [candBytes.length, expBytes.length],
    passKey: lenMatch ? 'km.len.pass' : 'km.len.fail',
    passArgs: [],
    pass: lenMatch,
  });

  if (!lenMatch) {
    return { matches: false, steps };
  }

  const len = candBytes.length;
  let i = 0;
  let earlyFail = false;

  // ── Step 2: 4-byte unrolled blocks ──
  while (i + 3 < len) {
    const blockSteps: KeyMatchStep[] = [];

    // 4 individual byte comparisons per block
    for (let j = 0; j < 4; j++) {
      const cb = candBytes[i + j];
      const eb = expBytes[i + j];
      const cc = String.fromCharCode(cb);
      const ec = String.fromCharCode(eb);
      const pass = cb === eb;

      blockSteps.push({
        type: 'byte-in-block',
        checkKey: 'km.byte.check',
        checkArgs: [i + j, cb, eb, cc, ec],
        passKey: pass ? 'km.byte.pass' : 'km.byte.fail',
        passArgs: [],
        pass,
      });

      if (!pass) {
        earlyFail = true;
        break;
      }
    }

    steps.push({
      type: 'block4',
      titleKey: 'km.block.title',
      titleArgs: [i],
      subSteps: blockSteps,
      allPass: blockSteps.every(s => s.pass),
      passKey: 'km.block.pass',
      passArgs: [],
    });

    if (earlyFail) {
      break;
    }
    i += 4;
  }

  // ── Step 3: Remainder bytes ──
  if (!earlyFail) {
    while (i < len) {
      const cb = candBytes[i];
      const eb = expBytes[i];
      const cc = String.fromCharCode(cb);
      const ec = String.fromCharCode(eb);
      const pass = cb === eb;

      steps.push({
        type: 'remainder',
        titleKey: 'km.remain.title',
        titleArgs: [i],
        checkKey: 'km.byte.check',
        checkArgs: [i, cb, eb, cc, ec],
        passKey: pass ? 'km.byte.pass' : 'km.byte.fail',
        passArgs: [],
        pass,
      });

      if (!pass) {
        earlyFail = true;
        break;
      }
      i++;
    }
  }

  matches = !earlyFail;
  return { matches, steps };
}
