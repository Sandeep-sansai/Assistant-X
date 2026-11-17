import { useApp } from '../AppContext';
import { DAY, LONG, SHORT, fmt, parseDay, today, type Upcoming } from '../lib/dates';
import { useCountUp, useReveal } from '../lib/hooks';

function DaysLeft({ value, run }: { value: number; run: boolean }) {
  return <b>{useCountUp(value, run, 600)}</b>;
}

function NextCard({ e, seen }: { e: Upcoming; seen: boolean }) {
  const { t } = useApp();
  const dates = e.end ? `${fmt(t, parseDay(e.start), LONG)} – ${fmt(t, parseDay(e.end), LONG)}` : fmt(t, parseDay(e.start), LONG);
  let progress = null;
  if (e.status === 'ONGOING' && e.end) {
    const total = (parseDay(e.end).getTime() - parseDay(e.start).getTime()) / DAY;
    const done = (today().getTime() - parseDay(e.start).getTime()) / DAY;
    progress = <div className="progress"><i style={{ width: `${Math.max(3, Math.min(100, Math.round(done / total * 100)))}%` }} /></div>;
  }
  return (
    <article className={`card${e.daysLeft <= 7 ? ' urgent' : ''}`}>
      <div className="count">
        {e.daysLeft === 0 ? <b>{t('next.today')}</b> : <><DaysLeft value={e.daysLeft} run={seen} /><span>{e.status === 'ONGOING' ? t('next.left', e.daysLeft) : t('next.toGo', e.daysLeft)}</span></>}
      </div>
      <div className="title">{e.title}</div>
      <div className="dates">{dates}</div>
      {progress}
      {e.sourceUrl && (
        <div className="src">
          <a href={e.sourceUrl} target="_blank" rel="noopener">{t('next.source')}</a>
          {e.verifiedOn && ` · ${t('next.checked', fmt(t, parseDay(e.verifiedOn), SHORT))}`}
        </div>
      )}
    </article>
  );
}

/** Key dates: pale blue boxes, the nearest ones lime like the university's deadline boxes. */
export function UpNext() {
  const { t, calendar, calendarOk } = useApp();
  const [ref, seen] = useReveal<HTMLDivElement>();
  return (
    <section aria-labelledby="nextTitle">
      <h2 id="nextTitle"><span>{t('next.title')}</span> <span className="hint">{t('next.hint')}</span></h2>
      <div className="next" id="next" ref={ref}>
        {!calendarOk ? <div className="card empty">{t('next.error')}</div>
          : !calendar.length ? <div className="card empty">{t('next.empty')}</div>
          : calendar.slice(0, 6).map((e) => <NextCard key={e.id} e={e} seen={seen} />)}
      </div>
    </section>
  );
}
