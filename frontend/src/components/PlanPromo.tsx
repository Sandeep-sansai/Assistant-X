import { useApp } from '../AppContext';
import { useReveal } from '../lib/hooks';
import { focusShort } from '../planner/names';
import type { PlanResult } from '../lib/types';
import { Ring } from './Ring';

const DEMO_PLAN = [[6, 6, 6, 6, 6], [6, 6, 6, 4, 8], [6, 6, 6, 6, 6], [30]];

/** A mini plan: one column per semester, a block per module, and a ring counting the credits. */
function PromoVisual({ result }: { result: PlanResult | null }) {
  const semesters = result
    ? result.semesters.map((s) => s.modules.map((m) => ({ credits: m.credits, kind: m.thesis ? 'thesis' : (m.requirement && m.requirement.startsWith('8.')) ? 'focus' : 'core' })))
    : DEMO_PLAN.map((col, i) => col.map((c, j) => ({ credits: c, kind: i === DEMO_PLAN.length - 1 ? 'thesis' : j < 2 ? 'focus' : 'core' })));
  let delay = 0;
  return (
    <div className="promo-visual" aria-hidden="true">
      <Ring value={result ? result.credits : 120} total={result ? result.requiredCredits : 120} className="promo-ring" />
      <div className="mini-board">
        {semesters.map((col, i) => (
          <div className="mini-wrap" key={i}>
            <div className="mini-col">
              {col.map((m, j) => { const d = delay; delay += 60; return <i key={j} className={`mini-block ${m.kind}`} style={{ flexGrow: m.credits, animationDelay: `${d}ms` }} />; })}
            </div>
            <span className="mini-label">S{i + 1}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

/** The study planner's promo on the start page; shows the student's own plan once there is one. */
export function PlanPromo() {
  const app = useApp();
  const { t, programmes, myProgramme: prog, plan, planResult: r } = app;
  const [ref] = useReveal<HTMLElement>();
  const imported = programmes.filter((x) => x.imported);
  if (!imported.length) return <section ref={ref} hidden />;
  const hasPlan = Boolean(prog && plan && plan.programme === prog.id && r);
  let sub: string;
  if (!prog) sub = t('promo.notYours', imported.map((x) => x.title).join(', '));
  else if (hasPlan && r && plan) sub = `${t('promo.subPlan', r.credits, r.requiredCredits, r.semesters.length, focusShort(app, plan.focus).name)} ${r.warnings.length ? t('plan.issues', r.warnings.length) : t('plan.allGood')}`;
  else sub = t('promo.subNone', prog.totalCredits);
  return (
    <section className="promo" id="planCard" aria-labelledby="pcTitle" ref={ref}>
      <div className="promo-text">
        <p className="kicker"><span className="new-badge">{t('promo.new')}</span> <span>{t('plan.kicker')}</span></p>
        <h2 id="pcTitle" dangerouslySetInnerHTML={{ __html: t('promo.title') }} />
        <p className="promo-sub">{sub}</p>
        <ul className="promo-points"><li>{t('promo.p1')}</li><li>{t('promo.p2')}</li><li>{t('promo.p3')}</li></ul>
        <div className="promo-actions">
          <button className="btn primary" type="button" onClick={() => (prog ? app.openPlanner() : app.openProfile(true))}>
            {!prog ? t('promo.setProgramme') : hasPlan ? t('plan.open') : t('plan.start')}
          </button>
          {prog && <button className="btn ghost" type="button" onClick={() => app.openChat(t('promo.askText'))}>{t('promo.ask')}</button>}
        </div>
      </div>
      <PromoVisual result={hasPlan ? r : null} />
    </section>
  );
}
