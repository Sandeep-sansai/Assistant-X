import { useApp } from '../AppContext';
import type { Planned } from '../lib/types';
import { OFFERED_BADGE, TERM_LETTER, requirementTitle, uiTitle } from './names';
import { usePlanner } from './PlannerContext';

/** One planned module: name (links to its official page), W/S badge, credits, area, actions on hover. */
export function ModuleCard({ m, index, winter, delay }: { m: Planned; index: number; winter: boolean; delay?: number }) {
  const app = useApp();
  const { t, plan, planResult } = app;
  const p = usePlanner();
  const wrongTerm = (m.offered === 'WINTER' && !winter) || (m.offered === 'SUMMER' && winter);
  const kind = m.thesis ? ' thesis' : (m.requirement && m.requirement.startsWith('8.')) ? ' focus' : '';
  const title = uiTitle(app, m.title);
  const action = (label: string, tip: string, fn: () => void) => (
    <button type="button" title={tip} aria-label={`${tip}: ${title}`} onClick={fn}>{label}</button>
  );
  return (
    <article className={`pl-card${kind}${wrongTerm ? ' bad' : ''}${delay !== undefined ? ' enter' : ''}`} draggable tabIndex={0}
      style={delay !== undefined ? { animationDelay: `${delay}ms` } : undefined}
      onDragStart={(e) => { e.dataTransfer.setData('text/plain', m.code); e.currentTarget.classList.add('dragging'); }}
      onDragEnd={(e) => e.currentTarget.classList.remove('dragging')}>
      <div className="pl-card-top">
        <a className="pl-title" href={m.url} target="_blank" rel="noopener" title={t('plan.officialPage')}>{title}</a>
        <span className={`term term-${m.offered.toLowerCase()}`} title={t(OFFERED_BADGE[m.offered] || 'plan.unknown')}>{TERM_LETTER[m.offered] || '?'}</span>
      </div>
      <div className="pl-meta">
        <span>{m.credits} CP</span>
        {m.requirement && !m.thesis && <><span>·</span><span className="req">{requirementTitle(app, planResult, m.requirement)}</span></>}
        {m.oralExam && <span className="oral" title={t('plan.oral')}>🎤</span>}
      </div>
      <div className="pl-actions">
        {index > 0 && action('←', t('plan.moveEarlier'), () => p.moveModule(m.code, index - 1))}
        {plan && index < plan.semesters.length - 1 && action('→', t('plan.moveLater'), () => p.moveModule(m.code, index + 1))}
        {!m.thesis && action('✓', t('plan.markDone'), () => p.markCompleted(m.code))}
        {action('✕', t('plan.remove'), () => p.removeModule(m.code))}
      </div>
    </article>
  );
}
