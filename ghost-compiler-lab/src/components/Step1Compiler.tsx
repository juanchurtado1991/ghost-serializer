"use client";

import React, { useState, useEffect } from "react";
import {
  FieldMetadata,
  PackedField,
  TraceInfo,
  parseFields,
  packFieldName,
  findPerfectHash,
} from "../lib/compiler";

interface Step1CompilerProps {
  t: (key: string, ...args: any[]) => string;
  renderHTML: (text: string) => React.ReactNode;
  onCompilationSuccess: (
    fields: FieldMetadata[],
    trace: Record<number, TraceInfo>,
    dispatch: number[],
    multiplier: number,
    shift: number
  ) => void;
  onNextStep: () => void;
}

export default function Step1Compiler({
  t,
  renderHTML,
  onCompilationSuccess,
  onNextStep,
}: Step1CompilerProps) {
  const [dtoInput, setDtoInput] = useState<string>(
    `@GhostSerialization\ndata class BenchUser(\n    val id: Long,\n    val name: String,\n    val score: Double = 99.9,\n    val isActive: Boolean = true\n)`
  );
  const [compilerLogs, setCompilerLogs] = useState<
    Array<{ text: string; type: "system" | "info" | "success" | "error" }>
  >([]);
  const [isCompiling, setIsCompiling] = useState<boolean>(false);
  const [compiledFields, setCompiledFields] = useState<FieldMetadata[]>([]);
  const [compiledTrace, setCompiledTrace] = useState<Record<number, TraceInfo>>({});
  const [dispatchTable, setDispatchTable] = useState<number[]>(new Array(1024).fill(-1));
  const [multiplier, setMultiplier] = useState<number>(31);
  const [shift, setShift] = useState<number>(0);

  // Sub-step flow navigation
  const [activeSubStep, setActiveSubStep] = useState<number>(1);

  // Byte packing animation state
  const [currentPackingKey, setCurrentPackingKey] = useState<string>("—");
  const [packingInt32, setPackingInt32] = useState<number>(0);
  const [packingBytes, setPackingBytes] = useState<number[]>([0, 0, 0, 0]);

  // Hover/select math slots
  const [hoveredSlot, setHoveredSlot] = useState<number | null>(null);
  const [selectedSlot, setSelectedSlot] = useState<number | null>(null);

  const delay = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

  const handleCompile = async () => {
    setIsCompiling(true);
    setCompilerLogs([]);
    setDispatchTable(new Array(1024).fill(-1));
    setCompiledFields([]);
    setCompiledTrace({});
    setHoveredSlot(null);
    setSelectedSlot(null);
    setCurrentPackingKey("—");
    setPackingInt32(0);
    setPackingBytes([0, 0, 0, 0]);

    const addLog = (text: string, type: "system" | "info" | "success" | "error") => {
      setCompilerLogs((prev) => [...prev, { text, type }]);
    };

    addLog(t("log.start"), "system");
    await delay(250);

    const parsed = parseFields(dtoInput);
    if (parsed.length === 0) {
      addLog(t("log.error.nofields"), "error");
      setIsCompiling(false);
      return;
    }

    addLog(t("log.parsed", parsed.length), "success");
    parsed.forEach((f) => {
      addLog(t("log.field", f.name, f.type + (f.isNullable ? "?" : ""), f.hasDefault), "info");
    });
    await delay(200);

    const packedFields: PackedField[] = [];
    for (const field of parsed) {
      const { key, bytes, packStr } = packFieldName(field.name);
      packedFields.push({ name: field.name, key, len: field.name.length, packStr });

      setCurrentPackingKey(`"${field.name}"`);
      setPackingInt32(key);
      setPackingBytes(bytes);
      addLog(t("log.pack.step", field.name, packStr, key), "info");

      await delay(160);
    }

    addLog(t("log.hash.searching"), "system");
    await delay(350);

    const result = findPerfectHash(packedFields);

    if (result.trials && result.trials.length > 0) {
      for (const trial of result.trials) {
        addLog(t("log.hash.trial", trial.m, trial.s, trial.collisionSlot), "info");
        await delay(120);
      }
    }

    if (result.found) {
      setMultiplier(result.m);
      setShift(result.s);
      setDispatchTable(result.dispatch);
      setCompiledTrace(result.traceData);
      setCompiledFields(parsed);

      addLog(t("log.hash.found", result.m, result.s), "success");
      onCompilationSuccess(parsed, result.traceData, result.dispatch, result.m, result.s);
      
      // Auto-progress to sub-step 2 once compile finishes
      setActiveSubStep(2);
    } else {
      addLog(t("log.hash.fail"), "error");
    }
    setIsCompiling(false);
  };

  useEffect(() => {
    handleCompile();
  }, []);

  return (
    <section id="step-1" className="lab-section">
      <div className="pillar-badge bg-sky-500/10 border-sky-500/30 text-sky-400">
        <span className="pillar-icon">⚙️</span>
        <span>{t("p1.badge")}</span>
      </div>
      <div className="concept-banner border-sky-500/30 bg-sky-950/30">
        <div className="concept-title text-sky-400">{t("p1.concept.title")}</div>
        <div className="concept-body">{renderHTML(t("p1.concept.body"))}</div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-stretch">
        {/* Left Panel */}
        <div className="lg:col-span-5 left-compiler-panel flex flex-col gap-4">
          <div className="glass-card rounded-xl border border-slate-800 overflow-hidden flex flex-col">
            <div className="panel-header">
              <div className="flex items-center gap-2">
                <span className="w-2.5 h-2.5 rounded-full bg-yellow-400 animate-pulse"></span>
                <span>{t("p1.input.label")}</span>
              </div>
              <span className="text-xs text-slate-500 font-mono">@GhostSerialization</span>
            </div>
            <div className="p-4 flex-grow">
              <textarea
                id="dto-input"
                className="w-full bg-slate-950 text-sky-300 font-mono text-xs p-3 rounded-lg border border-slate-900 focus:outline-none focus:border-slate-800 h-48 resize-none leading-relaxed"
                spellCheck="false"
                value={dtoInput}
                onChange={(e) => setDtoInput(e.target.value)}
              ></textarea>
            </div>
            <div className="panel-footer">
              <button
                id="btn-compile"
                className="btn-primary w-full"
                onClick={handleCompile}
                disabled={isCompiling}
              >
                <svg
                  className="w-4 h-4 flex-shrink-0"
                  fill="none"
                  viewBox="0 0 24 24"
                  stroke="currentColor"
                  strokeWidth="2"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    d="M13 10V3L4 14h7v7l9-11h-7z"
                  />
                </svg>
                <span>{t("btn.compile")}</span>
              </button>
            </div>
          </div>

          {/* Terminal */}
          <div className="terminal-card" role="log" aria-live="polite">
            <div className="terminal-header">
              <span className="font-mono text-xs text-slate-400">{t("p1.terminal.title")}</span>
              <span className="text-[10px] text-emerald-500 font-mono">{t("p1.terminal.ready")}</span>
            </div>
            <div id="terminal" className="terminal-body h-48">
              {compilerLogs.length === 0 ? (
                <div className="text-slate-600">{t("p1.terminal.waiting")}</div>
              ) : (
                compilerLogs.map((log, idx) => (
                  <div
                    key={idx}
                    className={`log-line ${
                      log.type === "success"
                        ? "text-emerald-400"
                        : log.type === "error"
                        ? "text-red-400"
                        : log.type === "info"
                        ? "text-sky-300"
                        : "text-slate-400"
                    }`}
                  >
                    {renderHTML(log.text)}
                  </div>
                ))
              )}
            </div>
          </div>
        </div>

        {/* Right Panel */}
        <div className="lg:col-span-7 flex flex-col gap-4">
          {/* Sub-step Selector Tabs */}
          <div className="flex gap-1.5 bg-slate-900/60 p-1.5 rounded-xl border border-slate-800/80">
            {[1, 2, 3].map((sub) => (
              <button
                key={sub}
                disabled={compiledFields.length === 0}
                className={`flex-1 py-2 text-xs font-bold rounded-lg transition-all cursor-pointer ${
                  activeSubStep === sub
                    ? "bg-sky-500 text-slate-950 shadow-md font-extrabold"
                    : "text-slate-400 hover:text-white hover:bg-slate-850 disabled:opacity-30 disabled:cursor-not-allowed"
                }`}
                onClick={() => setActiveSubStep(sub)}
              >
                {t(`p1.substep${sub}`)}
              </button>
            ))}
          </div>

          {/* Sub-step 1: Byte Packing */}
          {activeSubStep === 1 && (
            <div className="glass-card rounded-xl border border-slate-800 overflow-hidden flex flex-col flex-grow">
              <div className="panel-header flex justify-between items-center">
                <span>{t("p1.pack.title")}</span>
                {compiledFields.length > 0 && !isCompiling && (
                  <div className="flex gap-1.5">
                    {compiledFields.map((field) => (
                      <button
                        key={field.name}
                        className={`px-2 py-0.5 text-[10px] font-mono rounded border transition-colors ${
                          currentPackingKey === `"${field.name}"`
                            ? "bg-sky-500/20 border-sky-500 text-sky-300"
                            : "bg-slate-900 border-slate-800 text-slate-500 hover:text-slate-300 hover:border-slate-600"
                        }`}
                        onClick={() => {
                          const { key, bytes } = packFieldName(field.name);
                          setCurrentPackingKey(`"${field.name}"`);
                          setPackingInt32(key);
                          setPackingBytes(bytes);
                        }}
                      >
                        {field.name}
                      </button>
                    ))}
                  </div>
                )}
              </div>
              <div className="p-5 flex flex-col justify-center flex-grow">
                <div className="flex justify-between items-center mb-4">
                  <p className="text-xs text-slate-400">
                    <span>{t("p1.pack.field")}</span>
                    <span className="text-sky-400 font-bold font-mono ml-1">
                      {currentPackingKey}
                    </span>
                  </p>
                  <div className="text-right">
                    <span className="text-[10px] text-slate-500 block">{t("p1.pack.int32")}</span>
                    <span className="font-mono text-lg font-bold text-indigo-400">
                      {packingInt32}
                    </span>
                  </div>
                </div>

                {/* Byte Labels */}
                <div className="grid grid-cols-4 gap-2 mb-1.5">
                  <span className="text-[10px] text-slate-600 font-mono text-center">
                    {t("p1.pack.byte3")}
                  </span>
                  <span className="text-[10px] text-slate-600 font-mono text-center">
                    {t("p1.pack.byte2")}
                  </span>
                  <span className="text-[10px] text-slate-600 font-mono text-center">
                    {t("p1.pack.byte1")}
                  </span>
                  <span className="text-[10px] text-slate-600 font-mono text-center">
                    {t("p1.pack.byte0")}
                  </span>
                </div>

                {/* Byte Display */}
                <div className="grid grid-cols-4 gap-3 mb-5">
                  {[3, 2, 1, 0].map((idx) => {
                    const val = packingBytes[idx];
                    const nameChar = currentPackingKey !== "—" ? currentPackingKey[idx + 1] : "";
                    return (
                      <div
                        key={idx}
                        className={`byte-box py-4 ${val ? "active text-indigo-300" : "text-slate-600"}`}
                      >
                        {val ? `'${nameChar}'\n${val}` : "0"}
                      </div>
                    );
                  })}
                </div>

                {/* Formula */}
                <div className="p-3 bg-black/40 rounded-lg border border-slate-900 text-xs font-mono text-slate-400">
                  <span className="text-slate-600">{t("p1.pack.formula.label")}</span>
                  <br />
                  hash ={" "}
                  <span className="text-sky-300">
                    ((key × <span className="text-yellow-400 font-bold">{multiplier}</span>{" "}
                    + len) &gt;&gt;{" "}
                    <span className="text-pink-400 font-bold">{shift}</span>) &amp; 1023
                  </span>
                </div>

                {/* Walkthrough details */}
                <div className="mt-4 p-4 bg-slate-950/80 rounded-xl border border-slate-900 space-y-3 text-xs flex-grow">
                  <div className="font-bold text-sky-400 font-mono">Byte Packing Walkthrough:</div>
                  <div className="space-y-1.5 font-mono text-slate-300">
                    <div>1. Nombre del campo: <span className="text-white font-bold">"{currentPackingKey.replace(/"/g, '')}"</span> (longitud = {currentPackingKey !== "—" ? currentPackingKey.length - 2 : 0})</div>
                    <div>2. Conversión ASCII a Int32:</div>
                    <div className="pl-3 space-y-1">
                      {packingBytes.map((b, idx) => {
                        const char = currentPackingKey !== "—" ? currentPackingKey[idx + 1] : "";
                        if (!char) return null;
                        const shiftBits = idx * 8;
                        const shiftVal = b << shiftBits;
                        return (
                          <div key={idx} className="text-slate-400">
                            Byte {idx}: '{char}' ({b}) {shiftBits > 0 ? `shl ${shiftBits}` : ''} = <span className="text-indigo-400">{shiftVal}</span>
                          </div>
                        );
                      })}
                    </div>
                    <div className="pt-1.5 border-t border-slate-900">
                      3. Valor final empaquetado (Int32): <span className="text-emerald-400 font-bold">{packingInt32}</span>
                    </div>
                  </div>
                  <div className="text-[11px] text-slate-500 leading-relaxed pt-2 border-t border-slate-900/60">
                    ℹ️ En Kotlin, el compilador KSP asocia este número único al campo:
                    <br />
                    <code className="text-pink-400 font-bold font-mono">val H_{currentPackingKey.replace(/"/g, '').toUpperCase()} = {packingInt32}</code>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* Sub-step 2: Dispatch Grid */}
          {activeSubStep === 2 && (
            <div className="glass-card rounded-xl border border-slate-800 overflow-hidden flex flex-col flex-grow">
              <div className="panel-header">
                <span>{t("p1.grid.title")}</span>
                {compiledFields.length > 0 && (
                  <span className="text-[10px] text-sky-400 animate-pulse font-bold">
                    {t("p1.grid.hint")}
                  </span>
                )}
              </div>
              <div className="p-4 flex flex-col gap-4 flex-grow justify-between">
                <div>
                  <p className="text-xs text-slate-500 mb-3">{renderHTML(t("p1.grid.desc"))}</p>

                  <div
                    id="dispatch-grid"
                    className="grid gap-[1px] bg-slate-950 p-2 rounded-lg border border-slate-900 w-[260px] h-[260px] mx-auto"
                    style={{ gridTemplateColumns: "repeat(32, 1fr)" }}
                  >
                    {dispatchTable.map((fieldIdx, slot) => {
                      const isOccupied = fieldIdx !== -1;
                      const info = isOccupied ? compiledTrace[slot] : null;

                      return (
                        <div
                          key={slot}
                          className={`hash-slot aspect-square rounded-[1px] cursor-pointer transition-colors ${
                            isOccupied
                              ? "slot-occupied bg-emerald-400 hover:bg-emerald-300"
                              : "slot-empty bg-slate-800/40 hover:bg-slate-700/50"
                          }`}
                          title={info ? `Slot ${slot}: "${info.name}"` : `Slot ${slot}`}
                          onMouseEnter={() => {
                            if (info) setHoveredSlot(slot);
                          }}
                          onMouseLeave={() => setHoveredSlot(null)}
                          onClick={() => {
                            if (info) {
                              setSelectedSlot(slot === selectedSlot ? null : slot);
                            }
                          }}
                        ></div>
                      );
                    })}
                  </div>
                </div>

                {/* Math Explanation HUD */}
                <div className="bg-slate-950 border border-slate-900 rounded-lg p-4 min-h-[160px] flex flex-col justify-center">
                  {hoveredSlot === null && selectedSlot === null ? (
                    <div className="text-center text-slate-600 text-xs p-4">
                      {t("p1.grid.empty")}
                    </div>
                  ) : (() => {
                    const slot = hoveredSlot !== null ? hoveredSlot : selectedSlot!;
                    const info = compiledTrace[slot];
                    if (!info) return null;

                    return (
                      <div className="flex flex-col">
                        <div className="flex justify-between items-center mb-3">
                          <h4 className="text-green-400 font-bold text-sm">
                            <span>{t("p1.math.field.prefix")}</span>
                            {` "${info.name}" `}
                            <span>{t("p1.math.slot.prefix")}</span>
                            {` ${slot}`}
                          </h4>
                          <button
                            onClick={() => {
                              setHoveredSlot(null);
                              setSelectedSlot(null);
                            }}
                            className="text-slate-500 hover:text-white text-xs px-2 py-0.5 rounded bg-slate-800 hover:bg-slate-700 transition-colors"
                          >
                            {t("p1.math.close")}
                          </button>
                        </div>
                        <div className="space-y-1.5 font-mono text-[11px]">
                          <div className="step-math">
                            <span className="step-label-math">{t("p1.math.step1")}</span>
                            <span className="text-sky-300">
                              {`"${info.name}" → [${info.name
                                .split("")
                                .slice(0, 4)
                                .map((c) => c.charCodeAt(0))
                                .join(", ")}]`}
                            </span>
                          </div>
                          <div className="step-math">
                            <span className="step-label-math">{t("p1.math.step2")}</span>
                            <span className="text-yellow-300">
                              {`${info.key} × ${info.m} = ${info.keyM}`}
                            </span>
                          </div>
                          <div className="step-math">
                            <span className="step-label-math">{t("p1.math.step3")}</span>
                            <span className="text-slate-300">
                              {`${info.keyM} + ${info.len} = ${info.added}`}
                            </span>
                          </div>
                          <div className="step-math">
                            <span className="step-label-math">{t("p1.math.step4")}</span>
                            <span className="text-pink-300">
                              {`${info.added} >> ${info.s} = ${info.shifted}`}
                            </span>
                          </div>
                          <div className="step-math">
                            <span className="step-label-math">{t("p1.math.step5")}</span>
                            <span className="text-emerald-400 font-bold">
                              {`${info.shifted} & 1023 = ${slot} ✓`}
                            </span>
                          </div>
                        </div>
                      </div>
                    );
                  })()}
                </div>
              </div>
            </div>
          )}

          {/* Sub-step 3: Generated Code */}
          {activeSubStep === 3 && (
            <div className="glass-card rounded-xl border border-slate-800 overflow-hidden flex flex-col flex-grow">
              <div className="panel-header">
                <span>{t("p1.code.title")}</span>
              </div>
              <div className="p-4 flex flex-col flex-grow">
                <pre className="bg-[#1e1e1e] p-4 rounded-lg border border-slate-800 text-[11px] sm:text-xs font-mono overflow-auto text-slate-300 leading-relaxed flex-grow">
                  <code>
                    {`// JsonReaderOptions — KSP Generated\n`}
                    {`val OPTIONS = JsonReaderOptions(\n`}
                    {`    multiplier = ${multiplier},\n`}
                    {`    shift      = ${shift},\n`}
                    {`    dispatch   = IntArray(1024).apply {\n`}
                    {dispatchTable
                      .map((v, i) => (v !== -1 ? { slot: i, fieldIdx: v } : null))
                      .filter((item): item is { slot: number; fieldIdx: number } => item !== null)
                      .sort((a, b) => a.fieldIdx - b.fieldIdx)
                      .map((item) => `        this[${item.slot}] = ${item.fieldIdx}`)
                      .slice(0, 6)
                      .join("\n")}
                    {"\n        // ...\n    }\n)"}
                  </code>
                </pre>
              </div>
            </div>
          )}
        </div>
      </div>

      {compiledFields.length > 0 && (
        <div id="cta-step1" className="cta-next">
          <p className="cta-text">{renderHTML(t("p1.cta.text"))}</p>
          <button className="btn-next" onClick={onNextStep}>
            {t("btn.next.p2")}
          </button>
        </div>
      )}
    </section>
  );
}
