import { useEffect, useRef, useState } from 'react';
import { useApp } from '../AppContext';
import { DAY, countdown, fmt, termOf, termStartDate, today } from '../lib/dates';

/**
 * Opening animation: a tear-off calendar flips from the first day of the current semester to today
 * (so the month changes later in the semester), then shows the next key date. Click or any key skips it.
 */
export function Splash({ onDone }: { onDone: () => void }) {
  const { t, calendar } = useApp();
  const active = document.documentElement.classList.contains('splash');
  const [dates] = useState(() => {
    const now = today();
    const start = termStartDate(termOf(now));
    const span = Math.round((now.getTime() - start.getTime()) / DAY);
    // At most 14 flips, evenly spread over the semester so far, always ending on today.
    const steps = Math.min(span, 14);
    return Array.from({ length: steps + 1 }, (_, i) => { const d = new Date(start); d.setDate(d.getDate() + (steps === 0 ? 0 : Math.round(span * i / steps))); return d; });
  });
  const [index, setIndex] = useState(0);
  const [flipKey, setFlipKey] = useState(0);
  const [arrived, setArrived] = useState(false);
  const [leaving, setLeaving] = useState(false);
  const calRef = useRef(calendar);
  calRef.current = calendar;
  const finished = useRef(false);

  const finish = () => {
    if (finished.current) return;
    finished.current = true;
    setLeaving(true);
    setTimeout(() => { document.documentElement.classList.remove('splash'); onDone(); }, 450);
  };

  useEffect(() => {
    if (!active) { onDone(); return; }
    const timers: number[] = [];
    let i = 0;
    const flip = () => {
      if (i < dates.length - 1) {
        i += 1;
        setIndex(i);
        setFlipKey((k) => k + 1);
        timers.push(window.setTimeout(flip, 75));
      } else {
        setArrived(true);
        timers.push(window.setTimeout(finish, 1700));
      }
    };
    timers.push(window.setTimeout(flip, 650));
    const key = () => finish();
    document.addEventListener('keydown', key, { once: true });
    return () => { timers.forEach(clearTimeout); document.removeEventListener('keydown', key); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (!active) return null;
  const d = dates[index];
  const next = arrived ? calRef.current[0] : null;
  return (
    <div id="splash" aria-hidden="true" className={leaving ? 'leave' : ''} onClick={finish}>
      <div className={`sp-stage${arrived ? ' sp-go' : ''}`}>
        <div className={`sp-cal${arrived ? ' today' : ''}`}>
          <div className="sp-rings"><i /><i /></div>
          <div className="sp-top">{fmt(t, d, { month: 'short' }).replace('.', '').toUpperCase()}</div>
          <div className={`sp-day${flipKey ? ' flip' : ''}`} key={flipKey}>{d.getDate()}</div>
        </div>
        <div className="sp-pill p1">📅 <span>{t('splash.events')}</span></div>
        <div className="sp-pill p2">⏰ <span>{t('splash.deadlines')}</span></div>
        <div className="sp-pill p3">💬 <span>{t('splash.answers')}</span></div>
        <div className="sp-brand"><b>Study Assistant</b><span>{t('splash.tag')}</span><br /><p className="sp-next">{next ? `${next.title} · ${countdown(t, next)}` : ''}</p></div>
      </div>
    </div>
  );
}
