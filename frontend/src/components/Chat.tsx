import { useCallback, useEffect, useRef, useState } from 'react';
import { useApp } from '../AppContext';
import { postJson } from '../lib/api';
import { LONG, TIME, WEEKDAY, fmt, parseDay, parseTermId, semesterOf, termIndex, termOf } from '../lib/dates';
import { useMediaQuery } from '../lib/hooks';
import { downloadIcs } from '../lib/logic';
import { canSpeak, speak, stopSpeaking, useSpeaking } from '../lib/speech';
import type { Reply, ReplyAction } from '../lib/types';
import { AskForm } from './AskForm';
import { ChipList } from './Hero';
import { useProfileParts } from './Header';
import { CloseIcon, LogoIcon } from './Icons';

const AGENT_ICONS: Record<string, string> = { concierge: '💬', regulations: '📚', deadlines: '⏰', onboarding: '🧭', campus: '🎉', planner: '🗺️', timetable: '🗓️' };

/** Escape first, then add a little formatting; the answer is model output, never trusted HTML. */
function formatAnswer(text: string): string {
  const escaped = text.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c] as string);
  return escaped.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/\[(\d+(?:\s*,\s*\d+)*)\]/g, (_, nums: string) => nums.split(',').map((n) => `<span class="cite">${n.trim()}</span>`).join(' '));
}

type Message = { id: string; kind: 'q'; text: string } | { id: string; kind: 'a'; reply: Reply } | { id: string; kind: 'pending' } | { id: string; kind: 'error'; text: string };

/** A one-tap action an agent suggested. Nothing happens until the student taps it. */
function ActionButton({ a, onAsk }: { a: ReplyAction; onAsk: (q: string) => void }) {
  const app = useApp();
  const { t } = app;
  const [doneLabel, setDoneLabel] = useState<string | null>(null);
  return (
    <button type="button" className={a.type === 'ASK' ? 'act ask' : 'act'} disabled={Boolean(doneLabel)} onClick={() => {
      if (a.type === 'REMINDER' && a.at) {
        const id = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`;
        app.setReminders([...app.reminders, { id, title: a.title || a.label, at: a.at, done: false }]);
        setDoneLabel(t('rem.saved', `${fmt(t, new Date(a.at), WEEKDAY)}, ${fmt(t, new Date(a.at), TIME)}`));
      } else if (a.type === 'CALENDAR' && a.at) {
        downloadIcs(t, { id: 'reminder', title: a.title || a.label }, new Date(a.at));
        setDoneLabel(t('rem.icsSaved'));
      } else if (a.type === 'OPEN_JOURNEY' && a.journeyId) {
        app.openJourney(a.journeyId);
      } else if (a.type === 'OPEN_PLANNER') {
        app.openPlanner(a.params || null);
      } else if (a.type === 'ASK') {
        onAsk(a.label);
      }
    }}>{doneLabel || a.label}</button>
  );
}

function ReplyView({ id, reply, onAsk }: { id: string; reply: Reply; onAsk: (q: string) => void }) {
  const { t, lang } = useApp();
  const speaking = useSpeaking();
  const [rated, setRated] = useState(false);
  return (
    <div className="msg a">
      <div className="agent-tag">{`${AGENT_ICONS[reply.agent] || '💬'} ${t(`agent.${reply.agent}`) || reply.agent}`}</div>
      <div dangerouslySetInnerHTML={{ __html: formatAnswer(reply.text) }} />
      {reply.cards && reply.cards.length > 0 && (
        <ul className="reply-cards">
          {reply.cards.map((c, i) => {
            const d = c.date ? parseDay(c.date) : null;
            return (
              <li key={i}>
                {d && <div className="date-tile"><b>{d.getDate()}</b><small>{fmt(t, d, { month: 'short' }).replace('.', '')}</small></div>}
                <div>
                  {c.url ? <a className="c-title" href={c.url} target="_blank" rel="noopener">{c.title}</a> : <span className="c-title">{c.title}</span>}
                  {c.detail && <span className="c-detail">{c.detail}</span>}
                </div>
              </li>
            );
          })}
        </ul>
      )}
      {reply.actions && reply.actions.length > 0 && (
        <div className="reply-actions">{reply.actions.map((a, i) => <ActionButton key={i} a={a} onAsk={onAsk} />)}</div>
      )}
      {reply.sources && reply.sources.length > 0 && (
        <ul className="sources">
          {reply.sources.map((s) => (
            <li key={s.number}>{`[${s.number}] `}<a href={s.url} target="_blank" rel="noopener">{s.title}</a>{` · ${t('chat.retrieved', fmt(t, new Date(s.retrievedAt), LONG))}`}</li>
          ))}
        </ul>
      )}
      <div className="fb">
        {reply.questionId && ([['👍', 1], ['👎', -1]] as const).map(([label, rating]) => (
          <button key={rating} type="button" disabled={rated} title={rating > 0 ? t('chat.helpful') : t('chat.notHelpful')} onClick={async () => {
            setRated(true);
            await fetch(`/api/questions/${reply.questionId}/feedback`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ rating }) });
          }}>{label}</button>
        ))}
        {canSpeak && (
          <button type="button" className="speak" onClick={() => (speaking === id ? stopSpeaking() : speak(reply.text, id, lang))}>
            {speaking === id ? `⏹ ${t('voice.stopRead')}` : `🔊 ${t('voice.read')}`}
          </button>
        )}
      </div>
    </div>
  );
}

/** The chat: docked on the right on wide screens, a sliding panel with a floating button on narrow ones. */
export function Chat() {
  const app = useApp();
  const { t, chatRequest } = app;
  const docked = useMediaQuery('(min-width: 1100px)');
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState<Message[]>([]);
  const [busy, setBusy] = useState(false);
  const busyRef = useRef(false);
  // The last turns of the chat go with each message, so follow-ups like "and when is that?" work.
  const history = useRef<{ question: string; answer: string }[]>([]);
  const log = useRef<HTMLDivElement>(null);
  const parts = useProfileParts();
  const seq = useRef(0);

  // Everything the agents may know about the student; until there are accounts it travels with each message.
  const appRef = useRef(app);
  appRef.current = app;
  const ask = useCallback((message: string): Promise<Reply> => {
    const a = appRef.current;
    const p = a.profile || { department: null, programme: null, start: null };
    const sem = semesterOf(p);
    const journeyDone: Record<string, string[]> = {};
    for (const [id, js] of Object.entries(a.journeyStates)) journeyDone[id] = js.done || [];
    let planned: string[] = [];
    if (a.plan && a.plan.start) {
      const start = parseTermId(a.plan.start);
      const index = start ? termIndex(termOf(new Date())) - termIndex(start) : -1;
      if (index >= 0 && index < a.plan.semesters.length) planned = a.plan.semesters[index].slice(0, 40);
    }
    return postJson<Reply>('/api/assistant', {
      message, language: a.lang, journeyDone,
      profile: { department: p.department || null, programme: p.programme || null, semester: sem && sem > 0 ? sem : null, start: p.start || null },
      reminders: a.reminders.slice(-100).map((r) => ({ title: r.title, at: r.at, done: r.done })),
      planFocus: (a.plan && a.plan.focus) || null, completedModules: (a.plan && a.plan.completed) || [],
      plannedModules: planned, history: history.current.slice(-3),
    });
  }, []);

  const scrollToEnd = () => requestAnimationFrame(() => { if (log.current) log.current.scrollTop = log.current.scrollHeight; });

  const send = useCallback(async (question: string, voice = false) => {
    if (busyRef.current) return;
    busyRef.current = true;
    setBusy(true);
    const pendingId = `p${++seq.current}`;
    setMessages((m) => [...m, { id: `q${seq.current}`, kind: 'q', text: question }, { id: pendingId, kind: 'pending' }]);
    scrollToEnd();
    try {
      const reply = await ask(question);
      history.current.push({ question, answer: String(reply.text || '').slice(0, 1500) });
      if (history.current.length > 6) history.current.shift();
      const id = `a${seq.current}`;
      setMessages((m) => m.map((x) => (x.id === pendingId ? { id, kind: 'a', reply } : x)));
      // Asked by voice, answered by voice.
      if (voice) speak(reply.text, id, appRef.current.lang);
    } catch (err) {
      setMessages((m) => m.map((x) => (x.id === pendingId ? { id: pendingId, kind: 'error', text: t('chat.error', (err as Error).message) } : x)));
    } finally {
      busyRef.current = false;
      setBusy(false);
      scrollToEnd();
    }
  }, [ask, t]);

  /** The first time the chat shows, the assistant introduces itself. */
  const introduced = useRef(false);
  const introduce = useCallback(async () => {
    if (introduced.current) return;
    introduced.current = true;
    try {
      const reply = await ask(appRef.current.lang === 'de' ? 'Hilfe' : 'help');
      setMessages((m) => [{ id: 'intro', kind: 'a', reply }, ...m]);
    } catch { /* the chat still works */ }
  }, [ask]);

  useEffect(() => { if (docked) { setOpen(false); introduce(); } }, [docked, introduce]);
  useEffect(() => {
    if (!chatRequest) return;
    if (!docked) setOpen(true);
    if (chatRequest.question) send(chatRequest.question, chatRequest.voice);
    else introduce();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [chatRequest]);

  const close = () => { stopSpeaking(); if (!docked) setOpen(false); };
  useEffect(() => {
    const key = (e: KeyboardEvent) => { if (e.key === 'Escape') close(); };
    document.addEventListener('keydown', key);
    return () => document.removeEventListener('keydown', key);
  });

  const visible = docked || open;
  return (
    <>
      <button className="chat-fab" id="chatFab" type="button" aria-label={t('aria.chat')} onClick={() => app.openChat()}><span aria-hidden="true">💬</span> <span>{t('hero.ask')}</span></button>
      <div className={`scrim${open ? ' open' : ''}`} id="scrim" onClick={close} />
      <aside className={`drawer${open ? ' open' : ''}`} id="drawer" aria-label={t('aria.chat')} aria-hidden={!visible}>
        <div className="drawer-head">
          <div className="logo" aria-hidden="true"><LogoIcon /></div>
          <div className="chat-who"><b>{t('chat.title')}</b><small><span className="online-dot" aria-hidden="true" /><span>{t('chat.online')}</span></small></div>
          <div className="spacer" />
          <button className="icon-btn" id="closeChat" aria-label={t('aria.closeChat')} onClick={close}><CloseIcon /></button>
        </div>
        <div id="log" aria-live="polite" ref={log}>
          {messages.map((m) => m.kind === 'q' ? <div key={m.id} className="msg q">{m.text}</div>
            : m.kind === 'pending' ? <div key={m.id} className="msg a pending">{t('chat.looking')}<span className="typing"><i /><i /><i /></span></div>
            : m.kind === 'error' ? <div key={m.id} className="msg a">{m.text}</div>
            : <ReplyView key={m.id} id={m.id} reply={m.reply} onAsk={(q) => send(q)} />)}
        </div>
        <p className="context">{parts.length ? t('chat.context', parts.join(' · ')) : ''}</p>
        <ChipList className="chat-suggest" />
        <AskForm id="chatAsk" placeholderKey="chat.placeholder" disabled={busy} onAsk={(q, voice) => send(q, voice)} />
      </aside>
    </>
  );
}
