"use client";

import React, { useState, useEffect } from "react";
import { FieldMetadata } from "../lib/compiler";
import {
  simulateSerialization,
  getByteRepresentation,
  defaultInputValue,
  SerializerStep,
} from "../lib/serializer";

interface Step5SerializerProps {
  compiledFields: FieldMetadata[];
  t: (key: string, ...args: any[]) => string;
  renderHTML: (text: string) => React.ReactNode;
}

export default function Step5Serializer({
  compiledFields,
  t,
  renderHTML,
}: Step5SerializerProps) {
  const [serializerVals, setSerializerVals] = useState<Record<string, any>>({});
  const [serializerSteps, setSerializerSteps] = useState<SerializerStep[]>([]);
  const [serializerOutput, setSerializerOutput] = useState<string>("");
  const [bufferMode, setBufferMode] = useState<"hex" | "ascii">("hex");

  // Setup default serializer input fields
  useEffect(() => {
    const vals: Record<string, any> = {};
    compiledFields.forEach((f) => {
      if (f.type === "Boolean") {
        vals[f.name] = true;
      } else if (
        f.type === "Int" ||
        f.type === "Long" ||
        f.type === "Double" ||
        f.type === "Float"
      ) {
        vals[f.name] = f.type === "Int" || f.type === "Long" ? 42 : 99.9;
      } else {
        vals[f.name] = defaultInputValue(f);
      }
    });
    setSerializerVals(vals);
    setSerializerSteps([]);
    setSerializerOutput("");
  }, [compiledFields]);

  const handleRunSerialize = () => {
    const res = simulateSerialization(compiledFields, serializerVals);
    setSerializerOutput(res.output);
    setSerializerSteps(res.steps);
  };

  const byteRepresentation = getByteRepresentation(serializerOutput, bufferMode);

  return (
    <section id="step-5" className="lab-section">
      <div className="pillar-badge bg-purple-500/10 border-purple-500/30 text-purple-400">
        <span className="pillar-icon">📤</span>
        <span>{t("p5.badge")}</span>
      </div>
      <div className="concept-banner border-purple-500/30 bg-purple-950/20">
        <div className="concept-title text-purple-400">{t("p5.concept.title")}</div>
        <div className="concept-body">{renderHTML(t("p5.concept.body"))}</div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Left Panel */}
        <div className="lg:col-span-5 flex flex-col gap-4">
          <div className="glass-card rounded-xl border border-slate-800 overflow-hidden">
            <div className="panel-header">{t("p5.values.title")}</div>
            <div className="p-4 flex flex-col gap-3">
              {compiledFields.map((f) => {
                const isBool = f.type === "Boolean";
                return (
                  <div key={f.name} className="flex flex-col gap-1.5">
                    <label className="text-xs font-bold text-slate-400">
                      {f.name}: {f.type}
                      {f.isNullable ? "?" : ""}
                    </label>
                    {isBool ? (
                      <input
                        type="checkbox"
                        checked={serializerVals[f.name] ?? false}
                        onChange={(e) =>
                          setSerializerVals((prev) => ({ ...prev, [f.name]: e.target.checked }))
                        }
                        className="w-4 h-4 rounded accent-indigo-500 cursor-pointer"
                      />
                    ) : (
                      <input
                        type={
                          f.type === "Int" ||
                          f.type === "Long" ||
                          f.type === "Double" ||
                          f.type === "Float"
                            ? "number"
                            : "text"
                        }
                        className="bg-slate-950 border border-slate-800 text-xs font-mono text-indigo-300 p-2 rounded focus:outline-none focus:border-indigo-500 transition-colors"
                        value={serializerVals[f.name] ?? ""}
                        onChange={(e) =>
                          setSerializerVals((prev) => ({ ...prev, [f.name]: e.target.value }))
                        }
                      />
                    )}
                  </div>
                );
              })}
            </div>
            <div className="panel-footer">
              <button
                onClick={handleRunSerialize}
                className="btn-purple w-full py-3"
              >
                {t("btn.serialize")}
              </button>
            </div>
          </div>

          <div className="glass-card rounded-xl border border-slate-800 overflow-hidden">
            <div className="panel-header">{t("p5.headers.title")}</div>
            <div className="p-4 font-mono text-xs text-slate-400 space-y-1.5">
              {compiledFields.map((f) => {
                const constName = `H_${f.name.toUpperCase()}`;
                return (
                  <div key={f.name} className="flex justify-between border-b border-slate-900 pb-1.5 gap-2">
                    <span className="text-pink-400 font-mono">{constName}</span>
                    <span className="text-slate-500 font-mono">
                      {`= "\\"${f.name}\\\":".encodeUtf8()`}
                    </span>
                  </div>
                );
              })}
            </div>
          </div>
        </div>

        {/* Right Panel */}
        <div className="lg:col-span-7 flex flex-col gap-4">
          <div className="glass-card rounded-xl border border-slate-800 overflow-hidden flex-grow flex flex-col">
            <div className="panel-header">
              <span>{t("p5.emission.title")}</span>
              <span className="text-xs text-purple-400 font-mono font-bold">
                {byteRepresentation.length} bytes
              </span>
            </div>
            <div className="p-4 flex-grow flex flex-col gap-4 justify-between">
              <div className="overflow-y-auto space-y-2 max-h-[260px] flex-grow">
                {serializerSteps.length === 0 ? (
                  <p className="text-slate-600 font-mono text-xs">{t("p5.emission.initial")}</p>
                ) : (
                  serializerSteps.map((step, idx) => (
                    <div
                      key={idx}
                      className="p-2 bg-slate-950/80 border border-slate-900 rounded font-mono text-xs flex flex-col gap-1 border-l-2 border-l-purple-500"
                    >
                      <div className="flex justify-between text-[10px] text-slate-500 font-bold uppercase">
                        <span>{t(step.callKey, ...step.callArgs)}</span>
                        <span className="text-purple-400">{`"${step.chunk}"`}</span>
                      </div>
                      <div className="text-[10px] text-slate-400 leading-relaxed pl-1.5 border-l border-slate-800">
                        {renderHTML(t(step.descKey, ...step.descArgs))}
                      </div>
                    </div>
                  ))
                )}
              </div>

              {/* Buffer Output */}
              <div className="bg-slate-950 border border-slate-900 rounded-lg p-4 mt-4">
                <div className="flex justify-between items-center mb-2">
                  <span className="text-xs font-bold text-slate-300">{t("p5.buffer.title")}</span>
                  <div className="flex gap-2" role="group" aria-label="Buffer display mode">
                    <button
                      onClick={() => setBufferMode("hex")}
                      className={`buf-btn ${bufferMode === "hex" ? "active" : ""}`}
                    >
                      HEX
                    </button>
                    <button
                      onClick={() => setBufferMode("ascii")}
                      className={`buf-btn ${bufferMode === "ascii" ? "active" : ""}`}
                    >
                      ASCII
                    </button>
                  </div>
                </div>
                <div className="p-3 bg-black/60 rounded font-mono text-xs break-all tracking-wider text-emerald-400 min-h-[3rem]">
                  {byteRepresentation.content || "[Vacío]"}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Final Summary Card */}
      <div className="mt-10 p-6 rounded-2xl border border-slate-700/50 bg-gradient-to-br from-slate-900/80 to-slate-950/80">
        <h2 className="text-2xl font-black text-white mb-6 text-center">{t("summary.title")}</h2>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <div className="summary-card border-sky-500/30 bg-sky-950/20 text-center">
            <div className="text-sky-400 text-3xl mb-2">⚙️</div>
            <div className="font-bold text-white text-sm mb-2">{t("summary.p1.title")}</div>
            <div className="text-slate-400 text-xs leading-relaxed">{t("summary.p1.desc")}</div>
          </div>
          <div className="summary-card border-emerald-500/30 bg-emerald-950/20 text-center">
            <div className="text-emerald-400 text-3xl mb-2">🔢</div>
            <div className="font-bold text-white text-sm mb-2">{t("summary.p2.title")}</div>
            <div className="text-slate-400 text-xs leading-relaxed">{t("summary.p2.desc")}</div>
          </div>
          <div className="summary-card border-pink-500/30 bg-pink-950/20 text-center">
            <div className="text-pink-400 text-3xl mb-2">🔍</div>
            <div className="font-bold text-white text-sm mb-2">{t("summary.p3.title")}</div>
            <div className="text-slate-400 text-xs leading-relaxed">{t("summary.p3.desc")}</div>
          </div>
          <div className="summary-card border-yellow-500/30 bg-yellow-950/20 text-center">
            <div className="text-yellow-400 text-3xl mb-2">🏗️</div>
            <div className="font-bold text-white text-sm mb-2">{t("summary.p4.title")}</div>
            <div className="text-slate-400 text-xs leading-relaxed">{t("summary.p4.desc")}</div>
          </div>
        </div>
      </div>
    </section>
  );
}
