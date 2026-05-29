"use client";

import React, { useState, useEffect } from "react";
import { KeyMatchStep, simulateKeyMatch } from "../lib/keymatcher";
import { FieldMetadata } from "../lib/compiler";

interface Step3KeyMatchProps {
  compiledFields: FieldMetadata[];
  t: (key: string, ...args: any[]) => string;
  renderHTML: (text: string) => React.ReactNode;
  onNextStep: () => void;
}

export default function Step3KeyMatch({
  compiledFields,
  t,
  renderHTML,
  onNextStep,
}: Step3KeyMatchProps) {
  const [candInput, setCandInput] = useState<string>("nane");
  const [expInput, setExpInput] = useState<string>("name");
  const [keyMatchSteps, setKeyMatchSteps] = useState<KeyMatchStep[]>([]);
  const [keyMatchResult, setKeyMatchResult] = useState<boolean | null>(null);
  const [activeFieldIdx, setActiveFieldIdx] = useState<number>(0);

  useEffect(() => {
    if (compiledFields && compiledFields.length > 0) {
      const fName = compiledFields[activeFieldIdx]?.name || "name";
      setExpInput(fName);
      // Let's set candInput to something slightly wrong by default to show it failing, or exactly right.
      // E.g., change the last character if it's long enough, else just "a".
      const cand = fName.length > 1 ? fName.substring(0, fName.length - 1) + "x" : "x";
      setCandInput(cand);
      setKeyMatchSteps([]);
      setKeyMatchResult(null);
    }
  }, [compiledFields, activeFieldIdx]);

  const handleRunKeyMatch = () => {
    const res = simulateKeyMatch(candInput, expInput);
    setKeyMatchSteps(res.steps);
    setKeyMatchResult(res.matches);
  };

  return (
    <section id="step-3" className="lab-section">
      <div className="pillar-badge bg-pink-500/10 border-pink-500/30 text-pink-400">
        <span className="pillar-icon">🔍</span>
        <span>{t("p3.badge")}</span>
      </div>
      <div className="concept-banner border-pink-500/30 bg-pink-950/30">
        <div className="concept-title text-pink-400">{t("p3.concept.title")}</div>
        <div className="concept-body">{renderHTML(t("p3.concept.body"))}</div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Left Panel */}
        <div className="lg:col-span-5 flex flex-col gap-4">
          {/* Field Selector */}
          {compiledFields && compiledFields.length > 0 && (
            <div className="glass-card rounded-xl border border-slate-800 overflow-hidden">
              <div className="panel-header-sm bg-slate-900/80 border-b border-slate-800 px-4 py-2 text-xs font-bold text-slate-400">
                {t("p3.field_selector.title")}
              </div>
              <div className="p-3 flex flex-wrap gap-2 bg-slate-950">
                {compiledFields.map((field, idx) => (
                  <button
                    key={field.name}
                    className={`px-3 py-1.5 text-xs font-mono rounded border transition-colors ${
                      activeFieldIdx === idx
                        ? "bg-pink-500/20 border-pink-500 text-pink-300"
                        : "bg-slate-900 border-slate-800 text-slate-500 hover:text-slate-300 hover:border-slate-600"
                    }`}
                    onClick={() => setActiveFieldIdx(idx)}
                  >
                    {field.name}
                  </button>
                ))}
              </div>
            </div>
          )}

          <div className="glass-card rounded-xl border border-slate-800 overflow-hidden">
            <div className="panel-header">verifyKeyMatch() — Simulator</div>
            <div className="p-5 flex flex-col gap-4">
              <div>
                <label className="text-xs font-bold text-slate-400 block mb-1.5">
                  {t("p3.expected.label")}
                </label>
                <input
                  type="text"
                  className="w-full bg-slate-950 border border-slate-800 text-sm font-mono text-indigo-300 p-2.5 rounded focus:outline-none focus:border-indigo-500 transition-colors"
                  value={expInput}
                  onChange={(e) => setExpInput(e.target.value)}
                />
              </div>
              <div>
                <label className="text-xs font-bold text-slate-400 block mb-1.5">
                  {t("p3.candidate.label")}
                </label>
                <input
                  type="text"
                  className="w-full bg-slate-950 border border-slate-800 text-sm font-mono text-pink-300 p-2.5 rounded focus:outline-none focus:border-pink-500 transition-colors"
                  value={candInput}
                  onChange={(e) => setCandInput(e.target.value)}
                />
                <p className="text-[10px] text-slate-600 mt-1.5">{t("p3.hint")}</p>
              </div>
              <button
                onClick={handleRunKeyMatch}
                className="btn-pink w-full py-3"
              >
                {t("btn.run.keymatch")}
              </button>
            </div>
          </div>

          {keyMatchResult !== null && (
            <div
              className={`glass-card rounded-xl border overflow-hidden ${
                keyMatchResult ? "border-emerald-500/50" : "border-red-500/50"
              }`}
            >
              <div className="p-5 text-center">
                <div className="text-5xl mb-2">{keyMatchResult ? "🎉" : "❌"}</div>
                <div className={`font-bold text-xl ${keyMatchResult ? "text-emerald-400" : "text-red-400"}`}>
                  {keyMatchResult ? t("p3.result.match") : t("p3.result.fail")}
                </div>
                <div className="text-xs text-slate-400 mt-1.5">
                  {keyMatchResult ? t("p3.result.match.sub") : t("p3.result.fail.sub")}
                </div>
              </div>
            </div>
          )}
        </div>

        {/* Right Panel */}
        <div className="lg:col-span-7">
          <div className="glass-card rounded-xl border border-slate-800 overflow-hidden flex flex-col min-h-[420px]">
            <div className="panel-header">
              <span>{t("p3.trace.title")}</span>
              <span className={`badge-neutral ${keyMatchResult !== null ? (keyMatchResult ? "badge-pass" : "badge-fail") : ""}`}>
                {t("btn.run.keymatch.badge")}
              </span>
            </div>
            <div className="p-4 flex-grow overflow-y-auto space-y-3">
              {keyMatchSteps.length === 0 ? (
                <p className="text-slate-600 font-mono text-xs">{t("p3.trace.initial")}</p>
              ) : (
                keyMatchSteps.map((step, idx) => {
                  const isPass = step.pass || step.allPass;
                  return (
                    <div
                      key={idx}
                      className={`keymatch-step ${isPass ? "pass" : "fail"}`}
                    >
                      <div className="keymatch-step-header">
                        <span>
                          {step.type === "length" && t(step.checkKey!, ...step.checkArgs!)}
                          {step.type === "block4" && t(step.titleKey!, ...step.titleArgs!)}
                          {step.type === "remainder" && t(step.titleKey!, ...step.titleArgs!)}
                        </span>
                        <span className="font-mono">
                          {isPass ? "PASS ✓" : "FAIL ✗"}
                        </span>
                      </div>
                      <div className="keymatch-step-desc">
                        {step.type === "length" && t(step.passKey!)}
                        {step.type === "block4" && (
                          <div className="space-y-1.5 mt-2">
                            {step.subSteps?.map((sub, sIdx) => (
                              <div key={sIdx} className="flex justify-between opacity-80 pl-2 border-l border-indigo-500/20">
                                <span>{t(sub.checkKey!, ...sub.checkArgs!)}</span>
                                <span className={sub.pass ? "text-emerald-400" : "text-red-400"}>
                                  {sub.pass ? "✓" : "✗"}
                                </span>
                              </div>
                            ))}
                            <div className="text-[10px] font-bold mt-1 text-slate-400">{t(step.passKey!)}</div>
                          </div>
                        )}
                        {step.type === "remainder" && (
                          <div className="flex justify-between pl-2 border-l border-indigo-500/20 mt-1">
                            <span>{t(step.checkKey!, ...step.checkArgs!)}</span>
                            <span>{t(step.passKey!)}</span>
                          </div>
                        )}
                      </div>
                    </div>
                  );
                })
              )}
            </div>
          </div>
        </div>
      </div>

      {keyMatchResult !== null && (
        <div id="cta-step3" className="cta-next">
          <p className="cta-text">{renderHTML(t("p3.cta.text"))}</p>
          <button className="btn-next" onClick={onNextStep}>
            {t("btn.next.p4")}
          </button>
        </div>
      )}
    </section>
  );
}
