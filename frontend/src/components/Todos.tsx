import { useState } from 'react';
import { useApp } from '../AppContext';
import { historyItems, todos, type DoneItem, type TodoItem } from '../lib/logic';
import { relDone } from '../lib/dates';
import { useReveal } from '../lib/hooks';
import { useToggleStep } from './Journey';

/** To-dos from key dates, reminders and journeys; ticked items move to a "Done" list and can be reopened. */
export function Todos() {
  const app = useApp();
  const { t, calendar, doneIds, reminders, journeys, journeyStates, history } = app;
  const toggleStep = useToggleStep();
  const [ref] = useReveal<HTMLElement>();
  const [leaving, setLeaving] = useState<string | null>(null);
  const [historyOpen, setHistoryOpen] = useState(false);

  const setReminderDone = (id: string, done: boolean) => {
    const r = reminders.find((x) => x.id === id);
    if (!r) return;
    app.setReminders(reminders.map((x) => (x.id === id ? { ...x, done } : x)));
    app.recordDone(`rem:${id}`, done, { what: r.title, sub: t('rem.sub') });
  };
  const tick = (item: TodoItem) => {
    if (item.kind === 'cal') {
      const e = calendar.find((x) => x.id === item.ref);
      app.setDone(item.ref, true);
      app.recordDone(item.id, true, { what: item.what, sub: e ? e.title.split(',')[0] : '' });
    } else if (item.kind === 'rem') {
      setReminderDone(item.ref, true);
    } else {
      const j = journeys.find((x) => x.id === item.journey);
      if (j) toggleStep(j, item.ref, true);
    }
  };
  const reopen = (h: DoneItem) => {
    if (h.key.startsWith('rem:')) setReminderDone(h.key.slice(4), false);
    else if (h.key.startsWith('cal:')) { app.setDone(h.key.slice(4), false); app.recordDone(h.key, false); }
    else {
      const j = journeys.find((x) => x.id === h.journey);
      const stepId = h.key.split(':')[2];
      if (j) toggleStep(j, stepId, false);
      else if (h.journey) { app.saveJourney(h.journey, { done: (journeyStates[h.journey]?.done || []).filter((id) => id !== stepId) }); app.recordDone(h.key, false); }
    }
  };

  const items = todos(t, calendar, doneIds, reminders, journeys, journeyStates).filter((i) => !i.done);
  const done = historyItems(history, journeys, journeyStates, calendar, doneIds);
  return (
    <section className="card" aria-labelledby="todoTitle" ref={ref}>
      <h2 id="todoTitle">{t('todo.title')}</h2>
      <ul className="todo" id="todo">
        {!items.length && <li className="empty">{done.length ? t('todo.allDone') : t('todo.empty')}</li>}
        {items.map((item) => (
          <li key={item.id} className={leaving === item.id ? 'leaving' : ''}>
            <label>
              <input type="checkbox" checked={leaving === item.id} onChange={() => {
                // Cross it out first, then let it move to the "Done" list.
                setLeaving(item.id);
                setTimeout(() => { tick(item); setLeaving(null); setHistoryOpen(true); }, 550);
              }} />
              <div><span className="what">{item.what}</span><span className={`due${item.soon ? ' soon' : ''}`}>{item.due}</span></div>
            </label>
          </li>
        ))}
      </ul>
      {done.length > 0 && (
        <details className="history" open={historyOpen} onToggle={(e) => setHistoryOpen((e.target as HTMLDetailsElement).open)}>
          <summary>{t('todo.history', done.length)}</summary>
          <ul className="todo">
            {done.map((h) => (
              <li key={h.key} className="done">
                <label>
                  <input type="checkbox" checked title={t('todo.reopen')} aria-label={`${t('todo.reopen')}: ${h.what}`} onChange={() => reopen(h)} />
                  <div><span className="what">{h.what}</span><span className="due">{[h.sub, h.at ? t('todo.doneOn', relDone(t, h.at)) : null].filter(Boolean).join(' · ')}</span></div>
                </label>
              </li>
            ))}
          </ul>
        </details>
      )}
    </section>
  );
}
