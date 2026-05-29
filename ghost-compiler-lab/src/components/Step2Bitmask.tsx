"use client";

import React, { useState, useEffect } from "react";
import { FieldMetadata, TraceInfo } from "../lib/compiler";
import {
  DebuggerStep,
  DebuggerState,
  buildDebuggerSteps,
  generateKotlinCodeLines,
  defaultLiteral,
} from "../lib/debugger";

interface Step2BitmaskProps {
  compiledFields: FieldMetadata[];
  compiledTrace: Record<number, TraceInfo>;
  t: (key: string, ...args: any[]) => string;
  renderHTML: (text: string) => React.ReactNode;
  onNextStep: () => void;
}

export default function Step2Bitmask({
  compiledFields,
  compiledTrace,
  t,
  renderHTML,
  onNextStep,
}: Step2BitmaskProps) {
  const [debuggerJsonStr, setDebuggerJsonStr] = useState<string>(
    '{"id":42,"name":"Ghost","score":99.9,"isActive":true}'
  );
  const [debuggerState, setDebuggerState] = useState<DebuggerState>({
    variables: {},
    mask0: 0n,
    currentStepIndex: 0,
    completed: false,
  });
  const [debuggerSteps, setDebuggerSteps] = useState<DebuggerStep[]>([]);
  const [kotlinCodeLines, setKotlinCodeLines] = useState<string[]>([]);

  // Initialize/reset the debugger state
  const handleResetDebugger = () => {
    if (compiledFields.length === 0) return;

    const initialVars: Record<string, string> = {};
    compiledFields.forEach((f) => {
      initialVars[f.name] = defaultLiteral(f.type);
    });

    const codeLines = generateKotlinCodeLines(compiledFields, compiledTrace);
    setKotlinCodeLines(codeLines);

    const steps = buildDebuggerSteps(debuggerJsonStr, compiledFields, compiledTrace, codeLines);
    setDebuggerSteps(steps);

    setDebuggerState({
      variables: initialVars,
      mask0: 0n,
      currentStepIndex: 0,
      completed: false,
    });
  };

  useEffect(() => {
    handleResetDebugger();
  }, [compiledFields, debuggerJsonStr]);

  const handleDebuggerNextStep = () => {
    if (debuggerState.currentStepIndex >= debuggerSteps.length) return;

    const nextStep = debuggerSteps[debuggerState.currentStepIndex];
    let nextState = {
      ...debuggerState,
      currentStepIndex: debuggerState.currentStepIndex + 1,
    };

    if (nextStep.action) {
      nextState = nextStep.action(nextState);
    }

    setDebuggerState(nextState);
  };

  const activeDebuggerStep =
    debuggerState.currentStepIndex > 0
      ? debuggerSteps[debuggerState.currentStepIndex - 1]
      : null;

  return (
    <section id="step-2" className="lab-section">
      <div className="pillar-badge bg-emerald-500/10 border-emerald-500/30 text-emerald-400">
        <span className="pillar-icon">🔢</span>
        <span>{t("p2.badge")}</span>
      </div>
      <div className="concept-banner border-emerald-500/30 bg-emerald-950/30">
        <div className="concept-title text-emerald-400">{t("p2.concept.title")}</div>
        <div className="concept-body">{renderHTML(t("p2.concept.body"))}</div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Left Panel — Code Viewer */}
        <div className="lg:col-span-6">
          <div className="glass-card rounded-xl border border-slate-800 overflow-hidden flex flex-col min-h-[560px]">
            <div className="panel-header">
              <div className="flex items-center gap-2">
                <span className="w-2.5 h-2.5 rounded-full bg-emerald-500"></span>
                <span className="font-mono text-sky-400 text-sm">{t("p2.code.title")}</span>
              </div>
              <div className="flex gap-2">
                <button
                  onClick={handleResetDebugger}
                  className="btn-secondary text-xs py-1 px-3"
                >
                  {t("btn.restart")}
                </button>
                <button
                  onClick={handleDebuggerNextStep}
                  disabled={
                    debuggerState.currentStepIndex >= debuggerSteps.length ||
                    compiledFields.length === 0
                  }
                  className="btn-emerald text-xs py-1.5 px-4"
                >
                  {t("btn.next.step")}
                </button>
              </div>
            </div>
            <div className="overflow-y-auto bg-[#1a1c23] flex-grow text-[11px] font-mono text-[#d4d4d4] p-4 leading-relaxed">
              {kotlinCodeLines.map((line, idx) => {
                const isActive =
                  activeDebuggerStep !== null && activeDebuggerStep.line === idx;
                return (
                  <span
                    key={idx}
                    className={`code-line ${isActive ? "active-code-line text-white font-bold" : ""}`}
                  >
                    {line || "\n"}
                  </span>
                );
              })}
            </div>
          </div>
        </div>

        {/* Right Panel — State details */}
        <div className="lg:col-span-6 flex flex-col gap-4">
          {/* JSON Stream */}
          <div className="glass-card rounded-xl border border-slate-800 overflow-hidden">
            <div className="panel-header">
              <div className="flex items-center gap-1.5">
                <span className="w-1.5 h-1.5 bg-yellow-400 rounded-full"></span>
                <span>{t("p2.json.title")}</span>
              </div>
              <span className="text-slate-500 text-xs">
                <span>{t("p2.json.pos")}</span>
                <span className="text-sky-400 font-bold ml-1">
                  {activeDebuggerStep && activeDebuggerStep.cursor[0] !== -1
                    ? activeDebuggerStep.cursor[0]
                    : 0}
                </span>
              </span>
            </div>
            <div className="p-3">
              <input
                id="custom-json-input"
                type="text"
                className="w-full bg-slate-900 border border-slate-800 text-xs font-mono text-emerald-400 p-2 rounded focus:outline-none focus:border-slate-700 mb-2"
                value={debuggerJsonStr}
                onChange={(e) => setDebuggerJsonStr(e.target.value)}
              />
              <div className="p-2 font-mono text-sm tracking-widest break-all leading-loose bg-black/50 border border-slate-900 rounded min-h-[3rem]">
                {debuggerJsonStr.split("").map((char, index) => {
                  let cls = "cursor-default";
                  if (activeDebuggerStep) {
                    const [start, end] = activeDebuggerStep.cursor;
                    if (index >= start && index <= end) {
                      cls = "cursor-active";
                    } else if (index < start) {
                      cls = "cursor-consumed";
                    }
                  }
                  return (
                    <span key={index} className={`json-char ${cls}`}>
                      {char}
                    </span>
                  );
                })}
              </div>
            </div>
          </div>

          {/* Variables & Mask */}
          <div className="grid grid-cols-2 gap-3">
            <div className="glass-card rounded-xl border border-slate-800 overflow-hidden flex flex-col">
              <div className="panel-header-sm">{t("p2.stack.title")}</div>
              <div className="p-3 font-mono text-xs flex flex-col gap-2 bg-slate-950/40 min-h-[90px] overflow-y-auto flex-grow">
                {compiledFields.map((f) => (
                  <div key={f.name} className="flex justify-between border-b border-slate-900/50 pb-0.5">
                    <span className="text-slate-500">{f.name}Value:</span>
                    <span className="text-sky-300">{debuggerState.variables[f.name] ?? "—"}</span>
                  </div>
                ))}
              </div>
            </div>

            <div className="glass-card rounded-xl border border-slate-800 overflow-hidden flex flex-col">
              <div className="panel-header-sm flex justify-between">
                <span>{t("p2.bitmask.title")}</span>
                <span className="text-yellow-400 font-mono font-bold">
                  {debuggerState.mask0.toString()}L
                </span>
              </div>
              <div className="p-3 font-mono text-xs flex flex-col justify-center gap-1.5 bg-slate-950/40 min-h-[90px] flex-grow">
                <div className="flex justify-end gap-1 flex-wrap text-slate-600 font-bold">
                  {/* Binary bitmask blocks */}
                  {(() => {
                    const binaryStr = debuggerState.mask0.toString(2).padStart(16, "0");
                    const chunks = binaryStr.match(/.{1,4}/g) || [];
                    return chunks.map((chunk, cIdx) => (
                      <span key={cIdx} className="text-slate-400">
                        {chunk}
                      </span>
                    ));
                  })()}
                </div>
                <div className="text-[10px] flex flex-wrap gap-1.5 justify-end mt-1">
                  {compiledFields.map((f, fIdx) => {
                    const isSet = (debuggerState.mask0 & (1n << BigInt(fIdx))) !== 0n;
                    return (
                      <span
                        key={f.name}
                        className={`px-1 rounded ${
                          isSet ? "bg-emerald-500/20 text-emerald-400 font-bold" : "text-slate-600"
                        }`}
                      >
                        {f.name.substring(0, 3)}
                      </span>
                    );
                  })}
                </div>
              </div>
            </div>
          </div>

          {/* Step HUD */}
          <div className="bg-indigo-950/40 rounded-xl border border-indigo-500/30 overflow-hidden flex-grow flex flex-col min-h-[140px]">
            <div className="flex items-center gap-2 px-4 py-2.5 bg-indigo-950/70 border-b border-indigo-500/20">
              <svg
                xmlns="http://www.w3.org/2000/svg"
                width="14"
                height="14"
                viewBox="0 0 24 24"
                fill="none"
                stroke="#818cf8"
                strokeWidth="2.5"
              >
                <circle cx="12" cy="12" r="10" />
                <line x1="12" y1="16" x2="12" y2="12" />
                <line x1="12" y1="8" x2="12.01" y2="8" />
              </svg>
              <span className="text-xs font-black text-indigo-300 tracking-wider uppercase">
                {activeDebuggerStep ? t(activeDebuggerStep.titleKey, ...activeDebuggerStep.titleParams) : t("p2.step.initial")}
              </span>
            </div>
            <div className="p-4 text-sm text-indigo-200 leading-relaxed font-medium overflow-y-auto flex-grow">
              {activeDebuggerStep ? renderHTML(t(activeDebuggerStep.descKey, ...activeDebuggerStep.descParams)) : renderHTML(t("p2.step.initial.desc"))}
            </div>
          </div>
        </div>
      </div>

      {debuggerState.completed && (
        <div id="cta-step2" className="cta-next">
          <p className="cta-text">{renderHTML(t("p2.cta.text"))}</p>
          <button className="btn-next" onClick={onNextStep}>
            {t("btn.next.p3")}
          </button>
        </div>
      )}
    </section>
  );
}
