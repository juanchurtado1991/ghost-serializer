"use client";

import React, { useState, useEffect } from "react";
import { FieldMetadata } from "../lib/compiler";
import { simulateDispatch, DispatchBranch } from "../lib/dispatch";

interface Step4DispatchProps {
  compiledFields: FieldMetadata[];
  t: (key: string, ...args: any[]) => string;
  renderHTML: (text: string) => React.ReactNode;
  onNextStep: () => void;
}

export default function Step4Dispatch({
  compiledFields,
  t,
  renderHTML,
  onNextStep,
}: Step4DispatchProps) {
  const [presentFields, setPresentFields] = useState<string[]>([]);
  const [dispatchBranches, setDispatchBranches] = useState<DispatchBranch[]>([]);

  // Automatically compute branches when compiledFields or presentFields change
  useEffect(() => {
    if (compiledFields.length === 0) return;

    // Required fields are always present
    const reqFields = compiledFields.filter((f) => !f.hasDefault).map((f) => f.name);
    const updatedPresent = Array.from(new Set([...reqFields, ...presentFields]));

    const res = simulateDispatch(compiledFields, updatedPresent);
    setDispatchBranches(res.branches);
  }, [compiledFields, presentFields]);

  const toggleFieldPresence = (fieldName: string) => {
    const field = compiledFields.find((f) => f.name === fieldName);
    if (!field || !field.hasDefault) return; // Cannot toggle required fields

    setPresentFields((prev) =>
      prev.includes(fieldName) ? prev.filter((n) => n !== fieldName) : [...prev, fieldName]
    );
  };

  const activeBranchIndex = dispatchBranches.findIndex((b) => b.isMatched);

  // Compute live dispatch mask
  const getDispatchMask = () => {
    let mask = 0n;
    compiledFields.forEach((f, idx) => {
      const isPresent = !f.hasDefault || presentFields.includes(f.name);
      if (isPresent) {
        mask |= 1n << BigInt(idx);
      }
    });
    return mask.toString() + "L";
  };

  return (
    <section id="step-4" className="lab-section">
      <div className="pillar-badge bg-yellow-500/10 border-yellow-500/30 text-yellow-400">
        <span className="pillar-icon">🏗️</span>
        <span>{t("p4.badge")}</span>
      </div>
      <div className="concept-banner border-yellow-500/30 bg-yellow-950/20">
        <div className="concept-title text-yellow-400">{t("p4.concept.title")}</div>
        <div className="concept-body">{renderHTML(t("p4.concept.body"))}</div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Left Panel */}
        <div className="lg:col-span-5">
          <div className="glass-card rounded-xl border border-slate-800 overflow-hidden">
            <div className="panel-header">{t("p4.fields.title")}</div>
            <div className="p-4 flex flex-col gap-3">
              {compiledFields.map((f) => {
                const isRequired = !f.hasDefault;
                const isChecked = isRequired || presentFields.includes(f.name);

                return (
                  <div
                    key={f.name}
                    onClick={() => !isRequired && toggleFieldPresence(f.name)}
                    className={`dispatch-check-item ${isChecked ? "checked" : ""} ${
                      isRequired ? "required" : ""
                    }`}
                  >
                    <div className="flex flex-col">
                      <span className="text-xs font-bold text-white">{f.name}</span>
                      <span className="text-[10px] text-slate-500 font-mono">
                        {isRequired ? t("p4.field.required") : t("p4.field.optional")}
                      </span>
                    </div>
                    <input
                      type="checkbox"
                      checked={isChecked}
                      disabled={isRequired}
                      readOnly
                      className="w-4 h-4 rounded text-sky-500 accent-sky-500 focus:ring-0 cursor-pointer"
                    />
                  </div>
                );
              })}
            </div>
            <div className="panel-footer text-center">
              <span className="text-xs text-slate-500 font-mono">{t("p4.mask.label")}</span>
              <span className="text-yellow-400 font-mono font-bold ml-1">
                {getDispatchMask()}
              </span>
            </div>
          </div>
        </div>

        {/* Right Panel */}
        <div className="lg:col-span-7">
          <div className="glass-card rounded-xl border border-slate-800 overflow-hidden">
            <div className="panel-header">{t("p4.branches.title")}</div>
            <div className="p-4 flex flex-col gap-3 overflow-y-auto max-h-[500px]">
              {dispatchBranches.map((branch, idx) => {
                const isTriggered = idx === activeBranchIndex;
                return (
                  <div
                    key={idx}
                    className={`dispatch-branch ${isTriggered ? "matched" : "unmatched"}`}
                  >
                    <div className="flex justify-between items-center mb-1.5 font-bold">
                      <span className={isTriggered ? "text-sky-300" : "text-slate-600"}>
                        {branch.isElse
                          ? "else ->"
                          : `if ((${branch.condition})) ->`}
                      </span>
                      {isTriggered && (
                        <span className="text-[9px] bg-sky-500/20 text-sky-400 px-2 py-0.5 rounded-full font-bold">
                          {t("p4.branch.matched")}
                        </span>
                      )}
                    </div>
                    <code className="text-[10px] leading-relaxed block">{branch.code}</code>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      </div>

      <div id="cta-step4" className="cta-next">
        <p className="cta-text">{renderHTML(t("p4.cta.text"))}</p>
        <button className="btn-next" onClick={onNextStep}>
          {t("btn.next.p5")}
        </button>
      </div>
    </section>
  );
}
