import { useEffect, useRef, useState } from 'react';
import { useApp } from '../AppContext';
import { downloadIcs, journeyProgress, offeredJourney } from '../lib/logic';
import { DAY, SHORT, WEEKDAY, fmt, parseDay, relDone, today } from '../lib/dates';
import { useDialog, useReveal } from '../lib/hooks';
import type { Journey } from '../lib/types';
import { CheckIcon, CloseIcon } from './Icons';

/** Ticks a journey step on or off and records it for the "Done" list. */
export function useToggleStep() {
  const { journeyStates, saveJourney, recordDone } = useApp();
  return (j: Journey, stepId: string, checked: boolean) => {
    const done = new Set(journeyProgress(j, journeyStates).done);
    if (checked) done.add(stepId); else done.delete(stepId);
    saveJourney(j.id, { done: [...done] });
    const step = j.steps.find((s) => s.id === stepId);
    recordDone(`j:${j.id}:${stepId}`, checked, { what: step ? step.title : stepId, sub: j.title, journey: j.id });
  };
}

function remindAt(daysAhead: number): Date {
  const d = today();
  d.setDate(d.getDate() + daysAhead);
  d.setHours(9, 0, 0, 0);
  return d;
}

/** The assistant offers to go through a checklist together ("Your first weeks"). */
export function JourneyNudge() {
  const { t, journeys, journeyStates, saveJourney, journeyConfirm, setJourneyConfirm, openJourney } = useApp();
  const [ref] = useReveal<HTMLElement>();
  const [menu, setMenu] = useState(false);
  const remind = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!menu) return;
    const close = (e: MouseEvent) => { if (remind.current && !remind.current.contains(e.target as Node)) setMenu(false); };
    document.addEventListener('click', close);
    return () => document.removeEventListener('click', close);
  }, [menu]);

  const j = journeyConfirm ? journeys.find((x) => x.id === journeyConfirm.id) : offeredJourney(journeys, journeyStates);
  if (!j) return <section ref={ref} hidden />;
  const { state: s, count } = journeyProgress(j, journeyStates);
  const choose = (choice: '1' | '3' | 'ics') => {
    const when = remindAt(choice === '3' ? 3 : 1);
    saveJourney(j.id, { remindAt: when.getTime(), snoozeUntil: null });
    if (choice === 'ics') downloadIcs(t, j, when);
    const label = fmt(t, when, WEEKDAY);
    setJourneyConfirm({ id: j.id, text: choice === 'ics' ? t('j.icsSaved', label) : t('j.reminderSet', label) });
    setMenu(false);
  };
  return (
    <section className="nudge" id="journey" aria-labelledby="jTitle" ref={ref}>
      <div className="nudge-ic" aria-hidden="true"><CheckIcon /></div>
      <div className="nudge-body">
        <p className="kicker">{s.remindAt && s.remindAt <= Date.now() ? t('j.reminderKicker') : t('j.kicker')}</p>
        <h2 id="jTitle">{j.title}</h2>
        <p className="prompt">{j.prompt}</p>
        <div className="jprogress"><i style={{ width: `${Math.round(count / j.steps.length * 100)}%` }} /></div>
        <p className="jcount">{t('j.progress', count, j.steps.length)}</p>
        {!journeyConfirm && (
          <div className="nudge-actions">
            <button className="btn primary" type="button" onClick={() => openJourney(j.id)}>{count > 0 ? t('j.continue') : t('j.start')}</button>
            <div className="remind" ref={remind}>
              <button className="btn" type="button" aria-haspopup="true" aria-expanded={menu} onClick={() => setMenu(!menu)}>{t('j.remind')}</button>
              {menu && (
                <div className="menu">
                  <button type="button" onClick={() => choose('1')}>{t('j.remindTomorrow')}</button>
                  <button type="button" onClick={() => choose('3')}>{t('j.remind3')}</button>
                  <button type="button" onClick={() => choose('ics')}>{t('j.ics')}</button>
                </div>
              )}
            </div>
            <button className="plain-btn" type="button" onClick={() => saveJourney(j.id, { snoozeUntil: Date.now() + 7 * DAY })}>{t('j.later')}</button>
          </div>
        )}
        {journeyConfirm && <p className="confirm">{journeyConfirm.text}</p>}
      </div>
    </section>
  );
}

/** The checklist itself, with links to the official pages and "ask the assistant" per step. */
export function JourneyDialog() {
  const { t, journeys, journeyStates, history, journeyOpen, openJourney, openChat } = useApp();
  const toggleStep = useToggleStep();
  const ref = useDialog(Boolean(journeyOpen), () => openJourney(null));
  const j = journeys.find((x) => x.id === journeyOpen);
  const progress = j ? journeyProgress(j, journeyStates) : null;
  return (
    <dialog className="wide" aria-labelledby="jdTitle" ref={ref}>
      {j && progress && (
        <div className="dlg">
          <div className="jd-head">
            <h2 id="jdTitle">{j.title}</h2>
            <button className="icon-btn" type="button" aria-label={t('aria.close')} onClick={() => openJourney(null)}><CloseIcon /></button>
          </div>
          <div className="jprogress"><i style={{ width: `${Math.round(progress.count / j.steps.length * 100)}%` }} /></div>
          <p className="jcount">{t('j.progress', progress.count, j.steps.length)}</p>
          <ol className="steps">
            {j.steps.map((step) => {
              const done = progress.done.has(step.id);
              const record = history[`j:${j.id}:${step.id}`];
              return (
                <li key={step.id} className={done ? 'done' : ''}>
                  <input type="checkbox" checked={done} aria-label={step.title} onChange={(e) => toggleStep(j, step.id, e.target.checked)} />
                  <div>
                    <span className="s-title">{step.title}</span>
                    <p className="s-detail">{step.detail}</p>
                    <div className="s-actions">
                      {/* The place where the step is done comes first; the page explaining it next to it. */}
                      {step.actionLink && <a className="s-go" href={step.actionLink} target="_blank" rel="noopener">{`${step.actionLabel || t('j.go')} ↗`}</a>}
                      {step.link && <a href={step.link} target="_blank" rel="noopener">{t(step.actionLink ? 'j.info' : 'j.open')}</a>}
                      {step.ask && <button type="button" onClick={() => { openJourney(null); openChat(step.ask as string); }}>{t('j.ask')}</button>}
                      {step.verifiedOn && <span className="s-checked">{t('j.checked', fmt(t, parseDay(step.verifiedOn), SHORT))}</span>}
                      {done && record && record.at > 0 && <span className="s-checked">✓ {t('todo.doneOn', relDone(t, record.at))}</span>}
                    </div>
                  </div>
                </li>
              );
            })}
          </ol>
          <div className="row"><button className="btn primary" type="button" onClick={() => openJourney(null)}>{t('j.close')}</button></div>
        </div>
      )}
    </dialog>
  );
}
