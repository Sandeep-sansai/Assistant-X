import { useState } from 'react';
import { useApp } from '../AppContext';
import { parseTermId, termLabel } from '../lib/dates';
import { SemesterHead } from './Board';
import { CatalogueList, catalogueGroups } from './Catalogue';
import { ModuleCard } from './ModuleCard';
import { usePlanner } from './PlannerContext';

/** The semester tabs shared by the step and week views. */
export function SemesterTabs({ label }: { label: (i: number) => string }) {
  const { t, planResult } = useApp();
  const p = usePlanner();
  if (!planResult) return null;
  return (
    <div className="pl-tabs" role="tablist">
      {planResult.semesters.map((x, i) => {
        const tm = parseTermId(x.term);
        return (
          <button key={x.term} type="button" role="tab" className="pl-tab" aria-selected={i === p.step} onClick={() => p.setStep(i)}>
            <b>{label(i)}</b><span>{tm ? termLabel(t, tm) : ''}</span>
            {p.view === 'step' && <div className="pl-load"><i className={x.credits > 33 ? 'heavy' : undefined} style={{ width: `${Math.min(100, Math.round(x.credits / 30 * 100))}%` }} /></div>}
          </button>
        );
      })}
    </div>
  );
}

/** One semester at a time: its modules on the left, what is taught that term on the right. */
export function StepView() {
  const app = useApp();
  const { t, planResult } = app;
  const p = usePlanner();
  const [query, setQuery] = useState('');
  const [onlyTerm, setOnlyTerm] = useState(true);
  if (!planResult) return null;
  const index = Math.max(0, Math.min(p.step, planResult.semesters.length - 1));
  const s = planResult.semesters[index];
  const term = parseTermId(s.term);
  const add = (code: string) => p.update((plan) => ({ ...plan, semesters: plan.semesters.map((x, i) => (i === index ? [...x, code] : x)) }));
  const opts = { winter: s.winter, term: s.term, query, onlyTerm };
  let groups = catalogueGroups(app, { ...opts, onlyMissing: true });
  // Every area already has its credits: offer the alternatives, to swap a module for another one.
  const swap = !groups.length;
  if (swap) groups = catalogueGroups(app, { ...opts, onlyMissing: false });
  return (
    <div className="pl-step" id="plStep">
      <SemesterTabs label={(i) => `${t('plan.semester', planResult.semesters[i].number)} · ${planResult.semesters[i].credits} CP`} />
      <div className="pl-step-body">
        <section className="pl-pane">
          <SemesterHead s={s} as="h3" />
          <div className="pl-cards">
            {s.modules.map((m, n) => <ModuleCard key={m.code} m={m} index={index} winter={s.winter} delay={p.animate ? n * 30 : undefined} />)}
            {!s.modules.length && <p className="empty">{t('plan.emptySem')}</p>}
          </div>
          <div className="pl-step-nav">
            <button className="btn" type="button" disabled={index === 0} onClick={() => p.setStep(index - 1)}>{t('plan.prev')}</button>
            <button className="btn" type="button" disabled={index === planResult.semesters.length - 1} onClick={() => p.setStep(index + 1)}>{t('plan.next')}</button>
          </div>
        </section>
        <section className="pl-pane pl-avail">
          <h3>{t('plan.availableIn', term ? termLabel(t, term) : '')}</h3>
          <div className="pl-avail-tools">
            <input className="pk-search" type="search" placeholder={t('plan.search')} value={query} onChange={(e) => setQuery(e.target.value)} />
            <label className="pk-filter"><input type="checkbox" checked={onlyTerm} onChange={(e) => setOnlyTerm(e.target.checked)} /><span>{t('plan.onlyThisTerm')}</span></label>
          </div>
          <div className="pk-list">
            {swap && groups.length > 0 && <p className="pl-hint">{t('plan.swapHint')}</p>}
            <CatalogueList groups={groups} onAdd={add} />
            {!groups.length && <p className="empty">{t('plan.stepNone')}</p>}
          </div>
        </section>
      </div>
    </div>
  );
}
