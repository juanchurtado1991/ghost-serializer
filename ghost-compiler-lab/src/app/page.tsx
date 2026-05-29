"use client";

import React, { useState } from "react";
import { Lang, translate, SUPPORTED_LANGS } from "../lib/i18n";
import { FieldMetadata, TraceInfo } from "../lib/compiler";

import Step1Compiler from "../components/Step1Compiler";
import Step2Bitmask from "../components/Step2Bitmask";
import Step3KeyMatch from "../components/Step3KeyMatch";
import Step4Dispatch from "../components/Step4Dispatch";
import Step5Serializer from "../components/Step5Serializer";

const renderHTML = (text: string) => {
  return <span dangerouslySetInnerHTML={{ __html: text }} />;
};

export default function Home() {
  const [lang, setLang] = useState<Lang>("en");
  const [activeStep, setActiveStep] = useState<number>(1);

  // Shared compilation outputs needed by other steps
  const [compiledFields, setCompiledFields] = useState<FieldMetadata[]>([]);
  const [compiledTrace, setCompiledTrace] = useState<Record<number, TraceInfo>>({});
  const [dispatchTable, setDispatchTable] = useState<number[]>(new Array(1024).fill(-1));
  const [multiplier, setMultiplier] = useState<number>(31);
  const [shift, setShift] = useState<number>(0);

  const t = (key: string, ...args: any[]) => translate(key, lang, ...args);

  const handleCompilationSuccess = (
    fields: FieldMetadata[],
    trace: Record<number, TraceInfo>,
    dispatch: number[],
    m: number,
    s: number
  ) => {
    setCompiledFields(fields);
    setCompiledTrace(trace);
    setDispatchTable(dispatch);
    setMultiplier(m);
    setShift(s);
  };

  return (
    <div className="min-h-screen flex flex-col">
      {/* Header */}
      <header className="text-center pt-4 pb-2 px-4">
        <div className="flex items-center justify-center gap-4 mb-2">
          <div className="inline-flex items-center gap-2 px-3 py-1 bg-slate-900 border border-slate-700 rounded-full text-xs text-sky-400 font-bold tracking-wider uppercase">
            <span className="w-2 h-2 rounded-full bg-sky-400 animate-pulse"></span>
            <span>{t("header.badge")}</span>
          </div>
          {/* Language Switcher */}
          <div className="lang-toggle" role="group" aria-label="Language selector">
            {SUPPORTED_LANGS.map((l) => (
              <button
                key={l}
                className={`lang-btn ${lang === l ? "active" : ""}`}
                onClick={() => setLang(l)}
              >
                {l.toUpperCase()}
              </button>
            ))}
          </div>
        </div>

        <h1 className="text-3xl sm:text-4xl font-black tracking-tight text-transparent bg-clip-text bg-gradient-to-r from-sky-400 via-indigo-400 to-purple-500 mb-1">
          👻 Ghost Compiler Lab
        </h1>
        <p className="text-slate-400 text-xs sm:text-sm max-w-2xl mx-auto leading-relaxed">
          {renderHTML(t("header.tagline"))}
        </p>

        {/* Guided Navigation */}
        <nav className="flex items-center justify-center gap-2 mt-4 flex-wrap" aria-label="Lab steps">
          {[1, 2, 3, 4, 5].map((step, idx) => (
            <React.Fragment key={step}>
              {idx > 0 && <div className="step-arrow" aria-hidden="true">→</div>}
              <button
                className={`pillar-step ${activeStep === step ? "active" : ""} ${
                  activeStep > step ? "done" : ""
                }`}
                onClick={() => setActiveStep(step)}
              >
                <div className="step-num">{step}</div>
                <div className="step-label">{t(`nav.step${step}`)}</div>
              </button>
            </React.Fragment>
          ))}
        </nav>
      </header>

      {/* Main Content */}
      <main className="max-w-7xl mx-auto px-4 pb-20 w-full flex-grow">
        {activeStep === 1 && (
          <Step1Compiler
            t={t}
            renderHTML={renderHTML}
            onCompilationSuccess={handleCompilationSuccess}
            onNextStep={() => setActiveStep(2)}
          />
        )}

        {activeStep === 2 && (
          <Step2Bitmask
            compiledFields={compiledFields}
            compiledTrace={compiledTrace}
            t={t}
            renderHTML={renderHTML}
            onNextStep={() => setActiveStep(3)}
          />
        )}

        {activeStep === 3 && (
          <Step3KeyMatch
            compiledFields={compiledFields}
            t={t}
            renderHTML={renderHTML}
            onNextStep={() => setActiveStep(4)}
          />
        )}

        {activeStep === 4 && (
          <Step4Dispatch
            compiledFields={compiledFields}
            t={t}
            renderHTML={renderHTML}
            onNextStep={() => setActiveStep(5)}
          />
        )}

        {activeStep === 5 && (
          <Step5Serializer
            compiledFields={compiledFields}
            t={t}
            renderHTML={renderHTML}
          />
        )}
      </main>

      {/* Footer */}
      <footer className="text-center py-8 text-xs text-slate-600 border-t border-slate-800/50 mt-auto">
        <p>{t("footer.text")}</p>
      </footer>
    </div>
  );
}
