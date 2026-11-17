import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useApp } from '../AppContext';
import { postJson } from '../lib/api';
import { LONG, fmt, parseTermId, termFromIndex, termId, termIndex, termLabel, termOf } from '../lib/dates';
import { KEYS, store } from '../lib/store';
import type { Plan, PlanResult, ProgrammeInfo } from '../lib/types';
import { CloseIcon } from '../components/Icons';
import { Ring } from '../components/Ring';
import { Board } from './Board';
import { focusShort, uiTitle } from './names';
import { Picker, type PickRequest } from './Picker';
import { PlannerCtx, type PlanForm, type PlannerApi, type View } from './PlannerContext';
import { Side, useIssues } from './Side';
import { StepView } from './StepView';
import { WeekView } from './WeekView';

/**
 * The study planner: plans the 120 credits from the official regulations, lets the student edit the
 * plan in three views and re-checks every change on the server.
 */
export function Planner() {
  const app = useApp();
  const { t, plannerRequest, closePlanner, programmes, myProgramme, plan, setPlan, planResult, setPlanResult, catalogue, profile } = app;
  const [prog, setProg] = useState<ProgrammeInfo | null>(null);
  const [form, setForm] = useState<PlanForm>({ focus: '', start: '', semesters: 4, max: null });
  const [view, setViewState] = useState<View>(() => store.get<View>(KEYS.planView, 'board'));
  const setView = useCallback((v: View) => { setViewState(v); store.set(KEYS.planView, v); }, []);
  const [step, setStep] = useState(0);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [animate, setAnimate] = useState(false);
  const [pick, setPick] = useState<PickRequest | null>(null);
  const root = useRef<HTMLElement>(null);
  const open = Boolean(plannerRequest && prog && catalogue);

  // Only the newest request may update the page; quick clicks on the controls must not show an older answer.
  const seq = useRef(0);
  const show = useCallback((result: PlanResult, withAnimation = false) => { setPlanResult(result); setError(null); setAnimate(withAnimation); }, [setPlanResult]);

  const recheck = useCallback(async (p: Plan) => {
    const n = ++seq.current;
    setPlan(p);
    try {
      const result = await postJson<PlanResult>('/api/planner/check', {
        programme: p.programme, focus: p.focus, start: p.start, semesters: p.semesters, completed: p.completed, maxCreditsPerSemester: p.max,
      });
      if (n === seq.current) show(result);
    } catch (err) {
      if (n === seq.current) setError(t('chat.error', (err as Error).message));
    } finally {
      if (n === seq.current) setBusy(false);
    }
  }, [setPlan, show, t]);

  /**
   * Plans the remaining credits. {@code keep} asks the planner to keep the modules already chosen
   * where they still fit (used when only the number of semesters or the start changes).
   */
  const generate = useCallback(async (f: PlanForm, programme: string, keep: boolean) => {
    const n = ++seq.current;
    const same = plan && plan.programme === programme;
    const completed = same && plan ? plan.completed : [];
    const preferred = keep && same && plan && plan.focus === f.focus ? plan.semesters.flat() : [];
    setBusy(true);
    try {
      const result = await postJson<PlanResult>('/api/planner/plan', {
        programme, focus: f.focus, start: f.start, semesters: f.semesters, completed, preferred, maxCreditsPerSemester: f.max,
      });
      if (n !== seq.current) return null;
      setPlan({ programme, focus: f.focus, start: f.start, max: f.max, completed, manual: false, semesters: result.semesters.map((s) => s.modules.map((m) => m.code)) });
      show(result, true);
      return result;
    } catch (err) {
      if (n === seq.current) setError(t('chat.error', (err as Error).message));
      return null;
    } finally {
      if (n === seq.current) setBusy(false);
    }
  }, [plan, setPlan, show, t]);

  // Opening: from the promo card, or from the chat with preset focus/semesters/start, or {view: 'week'}.
  useEffect(() => {
    if (!plannerRequest) { setProg(null); return; }
    const raw = plannerRequest.params || null;
    const params = raw && (raw.focus || raw.semesters || raw.start) ? raw : null;
    if (raw && raw.view) setView(raw.view as View);
    const target = (params && programmes.find((x) => x.id === params.programme)) || myProgramme;
    if (!target) { closePlanner(); return; }
    let live = true;
    (async () => {
      let cat;
      try { cat = await app.ensureCatalogue(target.id, true); } catch { closePlanner(); return; }
      app.loadTimetable(target.id);
      if (!live) return;
      const saved = plan && plan.programme === target.id ? plan : null;
      const profileStart = (profile && profile.start) || termId(termOf(new Date()));
      const f: PlanForm = {
        focus: (params && params.focus) || (saved && saved.focus) || target.focusOptions[0].code,
        start: (params && params.start) || (saved && saved.start) || profileStart,
        semesters: Number((params && params.semesters) || (saved ? saved.semesters.length : cat.programme.regularSemesters)),
        max: saved && saved.max ? saved.max : null,
      };
      setForm(f);
      setProg(target);
      setStep(0);
      setSettingsOpen(!saved && !params);
      if (root.current) root.current.scrollTop = 0;
      const result = params || !saved ? await generate(f, target.id, false) : (await recheck(saved), true);
      if (live && result) setSettingsOpen(false);
    })();
    return () => { live = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [plannerRequest]);

  useEffect(() => {
    document.body.classList.toggle('planner-open', open);
    if (!open) return;
    const esc = (e: KeyboardEvent) => { if (e.key === 'Escape' && !pick) closePlanner(); };
    document.addEventListener('keydown', esc);
    return () => document.removeEventListener('keydown', esc);
  }, [open, pick, closePlanner]);

  const thesisCode = useMemo(() => {
    if (!catalogue) return null;
    const p = catalogue.programme;
    const m = Object.values(p.modules).find((x) => x.credits === p.thesisCredits);
    return m ? m.code : null;
  }, [catalogue]);

  /**
   * Focus, first semester, number of semesters or credit limit changed. A generated plan is planned
   * again (keeping the chosen modules where possible); a plan the student builds keeps its modules.
   */
  const settingsChanged = (f: PlanForm, what: 'focus' | 'start' | 'semesters' | 'max') => {
    setForm(f);
    if (!prog) return;
    if (!plan || !plan.manual) { generate(f, prog.id, what !== 'focus'); return; }
    const semesters = plan.semesters.map((s) => s.filter((c) => c !== thesisCode));
    while (semesters.length > f.semesters) {
      const removed = semesters.pop() as string[];
      if (semesters.length) semesters[semesters.length - 1].push(...removed);
    }
    while (semesters.length < f.semesters) semesters.push([]);
    if (thesisCode) semesters[semesters.length - 1].push(thesisCode);
    // Modules from removed semesters land in the last study semester, before the thesis.
    if (semesters.length > 1 && thesisCode) {
      const others = semesters[semesters.length - 1].filter((c) => c !== thesisCode);
      if (others.length) { semesters[semesters.length - 2].push(...others); semesters[semesters.length - 1] = [thesisCode]; }
    }
    setStep((s) => Math.min(s, semesters.length - 1));
    recheck({ ...plan, focus: f.focus, start: f.start, max: f.max, semesters });
  };

  /** An empty plan the student fills semester by semester; only the thesis is placed, in the last one. */
  const buildMyself = () => {
    if (!prog) return;
    const completed = plan && plan.programme === prog.id ? plan.completed : [];
    const semesters: string[][] = Array.from({ length: form.semesters }, () => []);
    if (thesisCode) semesters[semesters.length - 1].push(thesisCode);
    setStep(0);
    setView('step');
    recheck({ programme: prog.id, focus: form.focus, start: form.start, max: form.max, completed, manual: true, semesters });
  };

  const api: PlannerApi | null = prog && plan ? {
    prog, form, view, setView, step, setStep, animate,
    update: (change) => recheck(change(plan)),
    moveModule: (code, index) => recheck({ ...plan, semesters: plan.semesters.map((s, i) => [...s.filter((c) => c !== code), ...(i === index ? [code] : [])]) }),
    removeModule: (code) => recheck({ ...plan, semesters: plan.semesters.map((s) => s.filter((c) => c !== code)) }),
    markCompleted: (code) => recheck({ ...plan, semesters: plan.semesters.map((s) => s.filter((c) => c !== code)), completed: plan.completed.includes(code) ? plan.completed : [...plan.completed, code] }),
    reopenCompleted: (code) => {
      const at = Math.min(step, plan.semesters.length - 1);
      recheck({ ...plan, completed: plan.completed.filter((c) => c !== code), semesters: plan.semesters.map((s, i) => (i === at ? [...s, code] : s)) });
    },
    openPicker: (index, replace) => setPick({ index, replace: replace || null }),
    name: (code) => { const m = catalogue && catalogue.programme.modules[code]; return m ? uiTitle(app, m.title) : code; },
  } : null;

  const issues = useIssues();
  if (!open || !prog || !catalogue) return null;

  const now = termIndex(termOf(new Date()));
  const starts = Array.from({ length: 11 }, (_, k) => termFromIndex(now + 2 - k));
  const f = focusShort(app, form.focus);
  const focusOption = prog.focusOptions.find((x) => x.code === form.focus);
  const startTerm = parseTermId(form.start);
  const regular = catalogue.programme.regularSemesters;

  return (
    <section className="planner" id="planner" aria-labelledby="plTitle" ref={root}>
      <header className="pl-bar">
        <div className="wrap pl-bar-in">
          <div className="pl-titles"><p className="kicker">{t('plan.kicker')}</p><h2 id="plTitle">{prog.title}</h2></div>
          <div className="pl-views" role="tablist" aria-label={t('plan.view')}>
            {([['board', '▦', 'plan.viewBoard'], ['step', '☰', 'plan.viewStep'], ['week', '◷', 'plan.viewWeek']] as const).map(([v, icon, key]) => (
              <button key={v} type="button" role="tab" aria-selected={view === v} onClick={() => setView(v)}><span aria-hidden="true">{icon}</span> <span>{t(key)}</span></button>
            ))}
          </div>
          <div className="pl-status" aria-live="polite">
            {planResult && <div className="ring"><Ring value={planResult.credits} total={planResult.requiredCredits} /></div>}
            {planResult && <span className={`status-pill ${issues.count ? 'warn' : 'ok'}`}>{issues.count ? `⚠ ${t('plan.statusIssues', issues.count)}` : `✓ ${t('plan.statusOk')}`}</span>}
          </div>
          <button className="icon-btn" type="button" aria-label={t('aria.close')} onClick={closePlanner}><CloseIcon /></button>
        </div>
      </header>
      <div className={`wrap pl-setup${settingsOpen ? '' : ' collapsed'}`} id="plSetup">
        <div className="pl-summary">
          <p><b>{`${f.icon} ${f.abbr || f.name}`}</b>{` · ${t('plan.sumStart', startTerm ? termLabel(t, startTerm) : '')} · ${t('plan.sumSemesters', form.semesters)}`}{form.max ? ` · ${t('plan.sumMax', form.max)}` : ''}</p>
          <button className="btn" type="button" aria-expanded={settingsOpen} onClick={() => setSettingsOpen(true)}><span aria-hidden="true">⚙</span> <span>{t('plan.change')}</span></button>
        </div>
        <div className="pl-settings">
          <span className="pl-label">{t('plan.focus')}</span>
          <div className="pl-focus" role="radiogroup" aria-label={t('plan.focus')}>
            {prog.focusOptions.map((o) => {
              const info = focusShort(app, o.code);
              return (
                <button key={o.code} type="button" role="radio" aria-checked={form.focus === o.code} className="focus-chip" title={info.name}
                  onClick={() => { if (form.focus !== o.code) settingsChanged({ ...form, focus: o.code }, 'focus'); }}>
                  <span className="fc-icon">{info.icon}</span><span className="fc-abbr">{info.abbr || info.name}</span>
                </button>
              );
            })}
          </div>
          <p className="pl-focus-name">{focusOption ? `${f.name} · ${focusOption.credits} CP` : ''}</p>
          <div className="pl-controls">
            <label className="field"><span className="pl-label">{t('plan.startTerm')}</span>
              <select value={form.start} onChange={(e) => settingsChanged({ ...form, start: e.target.value }, 'start')}>
                {starts.map((term) => <option key={termId(term)} value={termId(term)}>{termLabel(t, term)}</option>)}
              </select>
            </label>
            <div className="field"><span className="pl-label">{t('plan.semesters')}</span>
              <div className="seg-count" role="radiogroup" aria-label={t('plan.semesters')}>
                {[2, 3, 4, 5, 6, 7, 8].map((n) => (
                  <button key={n} type="button" role="radio" aria-checked={form.semesters === n} className={n === regular ? 'regular' : ''} title={n === regular ? t('plan.standard') : undefined}
                    onClick={() => { if (form.semesters !== n) settingsChanged({ ...form, semesters: n }, 'semesters'); }}>{n}</button>
                ))}
              </div>
            </div>
            <label className="field"><span className="pl-label">{t('plan.max')}</span>
              <select value={form.max ? String(form.max) : ''} onChange={(e) => settingsChanged({ ...form, max: e.target.value ? Number(e.target.value) : null }, 'max')}>
                <option value="">{t('plan.maxAuto')}</option>
                {[18, 24, 30, 36].map((n) => <option key={n} value={n}>{`${n} CP`}</option>)}
              </select>
            </label>
            <div className="pl-go">
              <button className="btn" type="button" onClick={buildMyself}>{t('plan.build')}</button>
              <button className="btn primary plan-go" type="button" disabled={busy} onClick={async () => { await generate(form, prog.id, false); setSettingsOpen(false); }}>
                <span aria-hidden="true">✦</span> <span>{t(busy ? 'plan.planning' : 'plan.generate')}</span>
              </button>
            </div>
          </div>
        </div>
        <p className="pl-source"><a href={prog.sourceUrl} target="_blank" rel="noopener">{t('plan.source')}</a>{prog.fetchedAt ? ` · ${t('plan.imported', fmt(t, new Date(prog.fetchedAt), LONG))}` : ''}</p>
      </div>
      <div className={`wrap pl-main${busy ? ' busy' : ''}`} id="plMain">
        <div className="pl-work">
          {api && planResult && (
            <PlannerCtx.Provider value={api}>
              {view === 'board' && <Board />}
              {view === 'step' && <StepView />}
              {view === 'week' && <WeekView />}
            </PlannerCtx.Provider>
          )}
        </div>
        {api ? <PlannerCtx.Provider value={api}><Side error={error} /></PlannerCtx.Provider> : <aside className="pl-side" />}
      </div>
      {api && (
        <Picker request={pick} onClose={() => setPick(null)} name={api.name} onAdd={(index, code, replace) => {
          setPick(null);
          const base = replace ? plan!.semesters.map((s) => s.filter((c) => c !== replace)) : plan!.semesters;
          recheck({ ...plan!, semesters: base.map((s, i) => (i === index ? [...s, code] : s)) });
        }} />
      )}
    </section>
  );
}
