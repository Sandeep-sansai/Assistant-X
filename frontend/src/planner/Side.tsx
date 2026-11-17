import { useApp } from '../AppContext';
import { requirementTitle, shortRequirement, uiTitle } from './names';
import { usePlanner } from './PlannerContext';
import { hardClashes } from './timetable';

/** Lines for the "Check" card: the planner's warnings plus timetable clashes per semester. */
export function useIssues() {
  const app = useApp();
  const { t, planResult, catalogue, timetable, manualSlots } = app;
  if (!planResult || !catalogue) return { warnings: [] as string[], count: 0 };
  const titleOf = (code: string) => uiTitle(app, (catalogue.programme.modules[code] || { title: code }).title);
  const warnings = planResult.warnings.map((w) => {
    const p = w.params;
    switch (w.type) {
      case 'WRONG_TERM': return t('plan.w.wrongTerm', titleOf(p[0]), p[1], t(p[2] === 'WINTER' ? 'plan.inWinter' : 'plan.inSummer'));
      case 'UNKNOWN_TERM': return t('plan.w.unknownTerm', titleOf(p[0]));
      case 'HEAVY_SEMESTER': return t('plan.w.heavy', p[0], p[1]);
      case 'THESIS_TOO_EARLY': return t('plan.w.thesisEarly', p[0], p[1], p[2]);
      case 'REQUIREMENT_UNMET': return t('plan.w.unmet', requirementTitle(app, planResult, p[0]) || p[0], p[1]);
      case 'NO_ORAL_EXAM': return t('plan.w.noOral');
      case 'NOT_OFFERED_RECENTLY': return t('plan.w.notRecently', titleOf(p[0]));
      default: return w.type;
    }
  });
  for (const s of planResult.semesters) {
    const n = hardClashes(timetable, manualSlots, s);
    if (n) warnings.push(t('wk.semClashes', s.number, n));
  }
  return { warnings, count: warnings.length };
}

export function Side({ error }: { error: string | null }) {
  const app = useApp();
  const { t, planResult, plan, catalogue } = app;
  const p = usePlanner();
  const { warnings, count } = useIssues();
  if (!planResult || !plan || !catalogue) return <aside className="pl-side">{error && <ul className="pl-warnings"><li className="warn">{error}</li></ul>}</aside>;
  // Areas that still need credits first; complete ones are a quiet list below.
  const ordered = [...planResult.requirements].sort((a, b) => Number(a.met) - Number(b.met));
  return (
    <aside className="pl-side">
      <section className="card"><h3>{t('plan.requirements')}</h3>
        <ul className="pl-reqs">
          {ordered.map((q) => (
            <li key={q.code} className={q.met ? 'met' : ''}>
              <div className="rq-top"><span className="rq-name">{shortRequirement(app, q.title)}</span><b>{`${q.planned}/${q.required}`}</b></div>
              <div className="rq-bar"><i style={{ width: `${Math.min(100, Math.round(q.planned / q.required * 100))}%` }} /></div>
            </li>
          ))}
        </ul>
      </section>
      <section className="card"><h3>{t('plan.check')}</h3>
        <ul className="pl-warnings">
          {error && <li className="warn">{error}</li>}
          {!count && !error && <li className="ok">✓ {t('plan.allGood')}</li>}
          {warnings.map((w, i) => <li key={i} className="warn">{w}</li>)}
          {planResult.notes.map((n, i) => <li key={`n${i}`} className="note">{n.type === 'MENTORING' ? t('plan.n.mentoring') : t('plan.n.thesis', n.params[0], n.params[1])}</li>)}
        </ul>
      </section>
      {plan.completed.length > 0 && (
        <section className="card"><h3>{t('plan.done')}</h3>
          <ul className="pl-done">
            {plan.completed.map((code) => {
              const m = catalogue.programme.modules[code];
              return <li key={code}><span>✓ {m ? uiTitle(app, m.title) : code}</span><button className="link-like" type="button" onClick={() => p.reopenCompleted(code)}>{t('plan.undo')}</button></li>;
            })}
          </ul>
        </section>
      )}
    </aside>
  );
}
