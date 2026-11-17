import { useState } from 'react';
import { useApp } from '../AppContext';
import { parseTermId, termLabel } from '../lib/dates';
import type { PlanSemester } from '../lib/types';
import { ModuleCard } from './ModuleCard';
import { usePlanner } from './PlannerContext';

export function SemesterHead({ s, as: Tag }: { s: PlanSemester; as: 'div' | 'h3' }) {
  const { t } = useApp();
  const term = parseTermId(s.term);
  const heavy = s.credits > 33 ? 'heavy' : undefined;
  return (
    <>
      <Tag className="pl-col-term">{term ? termLabel(t, term) : ''}</Tag>
      <div className="pl-col-sub"><span>{t('plan.semester', s.number)}</span><b className={heavy}>{s.credits} CP</b></div>
      <div className="pl-load"><i className={heavy} style={{ width: `${Math.min(100, Math.round(s.credits / 30 * 100))}%` }} /></div>
    </>
  );
}

/** Overview: semesters wrap onto new rows; modules can be dragged between them. */
export function Board() {
  const { t, planResult } = useApp();
  const p = usePlanner();
  const [drop, setDrop] = useState<number | null>(null);
  if (!planResult) return null;
  let order = 0;
  return (
    <div className="pl-board" id="plBoard">
      {planResult.semesters.map((s, index) => (
        <section key={s.term} className={`pl-col${drop === index ? ' drop' : ''}`}
          onDragOver={(e) => { e.preventDefault(); setDrop(index); }} onDragLeave={() => setDrop(null)}
          onDrop={(e) => { e.preventDefault(); setDrop(null); const code = e.dataTransfer.getData('text/plain'); if (code) p.moveModule(code, index); }}>
          <header className="pl-col-head" title={t('plan.openSemester')} onClick={() => { p.setStep(index); p.setView('step'); }}>
            <SemesterHead s={s} as="div" />
          </header>
          <div className="pl-cards">
            {s.modules.map((m) => <ModuleCard key={m.code} m={m} index={index} winter={s.winter} delay={p.animate ? (order++) * 30 : undefined} />)}
          </div>
          <button className="pl-add" type="button" onClick={() => p.openPicker(index)}>{t('plan.add')}</button>
        </section>
      ))}
    </div>
  );
}
