import { useEffect, useState } from 'react';
import { useApp } from '../AppContext';
import { DAY, LONG, fmt, parseDay, today } from '../lib/dates';
import { useCountUp, useReveal } from '../lib/hooks';

/** This semester: lecture weeks as a bar, breaks hatched, a pin for today. */
export function SemesterCard() {
  const { t, semester: sem } = useApp();
  const [ref, seen] = useReveal<HTMLElement>();
  const [filled, setFilled] = useState(false);
  useEffect(() => { if (seen) { const f = requestAnimationFrame(() => setFilled(true)); return () => cancelAnimationFrame(f); } }, [seen]);

  const start = sem ? parseDay(sem.lectureStart) : null;
  const end = sem ? parseDay(sem.lectureEnd) : null;
  const now = today();
  const span = start && end ? (end.getTime() - start.getTime()) / DAY + 1 : 1;
  const weeks = Math.ceil(span / 7);
  const before = start ? now < start : false;
  const value = !start ? 0 : before ? Math.round((start.getTime() - now.getTime()) / DAY) : Math.min(weeks, Math.floor((now.getTime() - start.getTime()) / DAY / 7) + 1);
  const big = useCountUp(value, seen);

  if (sem === null) return <section ref={ref} hidden />;
  if (!sem || !start || !end) {
    return <section className="card sem-card" id="semCard" ref={ref}><h2>{t('sem.title')}</h2><div id="semBody"><div className="empty">{t('next.loading')}</div></div></section>;
  }
  const at = (d: Date) => Math.max(0, Math.min(100, ((d.getTime() - start.getTime()) / DAY) / span * 100));
  const pct = at(now);
  const onBreak = sem.breaks.find((b) => now >= parseDay(b.start) && now <= parseDay(b.end));
  let note = '';
  if (now > end) note = t('sem.done');
  else if (onBreak) note = t('sem.onBreak', onBreak.title, fmt(t, new Date(parseDay(onBreak.end).getTime() + DAY), LONG));
  else if (now >= start) note = t('sem.daysLeft', Math.round((end.getTime() - now.getTime()) / DAY));
  return (
    <section className={`card sem-card${seen ? ' in' : ''}`} id="semCard" aria-labelledby="semTitle" ref={ref}>
      <h2 id="semTitle">{t('sem.title')}</h2>
      <div id="semBody">
        <div className="sem-top">
          <span className="sem-big">{big}</span>
          <span className="sem-of">{before ? <><b>{sem.title}</b>{t('sem.startsIn')}</> : <><b>{t('sem.week')}</b>{t('sem.of', weeks)}</>}</span>
        </div>
        <div className="sem-track">
          {sem.breaks.map((b) => {
            const left = at(parseDay(b.start));
            return <i key={b.start} className="sem-break" title={b.title} style={{ left: `${left}%`, width: `${at(parseDay(b.end)) - left}%` }} />;
          })}
          <i className="sem-fill" style={{ width: filled ? `${pct}%` : 0 }} />
          {now >= start && now <= end && (
            // Near either end the label sits beside the line instead of centred on it.
            <span className={`sem-pin${pct < 8 ? ' start' : pct > 92 ? ' end' : ''}`} style={{ left: `${pct}%` }}>{t('sem.today')}</span>
          )}
        </div>
        <div className="sem-ends"><span>{fmt(t, start, LONG)}</span><span>{fmt(t, end, LONG)}</span></div>
        <p className="sem-note">{note}{sem.sourceUrl && <span className="src"> · <a href={sem.sourceUrl} target="_blank" rel="noopener">{t('sem.source')}</a></span>}</p>
      </div>
    </section>
  );
}
