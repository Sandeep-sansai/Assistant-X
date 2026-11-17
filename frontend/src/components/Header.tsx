import { useEffect, useMemo, useRef, useState } from 'react';
import { useApp } from '../AppContext';
import { notifications, todos } from '../lib/logic';
import { semesterOf } from '../lib/dates';
import { BellIcon, LogoIcon } from './Icons';

/** "FB 16" from "FB 16 Elektrotechnik/Informatik". */
export function useDeptShort() {
  const { departments } = useApp();
  return (id: string | null | undefined) => {
    const d = departments.find((x) => x.id === id);
    return d ? d.name.split(' ').slice(0, 2).join(' ') : '';
  };
}

/** "FB 16 · Computer Science · Sem 1" for the profile chip and the chat. */
export function useProfileParts(): string[] {
  const { profile, t } = useApp();
  const deptShort = useDeptShort();
  if (!profile) return [];
  const parts: string[] = [];
  if (profile.department) parts.push(deptShort(profile.department));
  if (profile.programme) parts.push(profile.programme.replace(/ ?\([^)]*\)/, '').trim());
  const sem = semesterOf(profile);
  if (sem && sem > 0) parts.push(t('sem.short', sem));
  return parts;
}

export function Header() {
  const app = useApp();
  const { t, lang, setLang, profile, openProfile } = app;
  const parts = useProfileParts();
  const deptShort = useDeptShort();
  const [open, setOpen] = useState(false);
  // Reminders can fall due while the page is open.
  const [, tick] = useState(0);
  useEffect(() => { const id = setInterval(() => tick((n) => n + 1), 60000); return () => clearInterval(id); }, []);

  const items = useMemo(() => notifications(t, {
    journeys: app.journeys, states: app.journeyStates, reminders: app.reminders, feed: app.feed, previousVisit: app.previousVisit,
    todoItems: todos(t, app.calendar, app.doneIds, app.reminders, app.journeys, app.journeyStates),
  }), [t, app.journeys, app.journeyStates, app.reminders, app.feed, app.previousVisit, app.calendar, app.doneIds]);
  const unread = items.filter((n) => !app.read.has(n.id)).length;

  const pop = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!open) return;
    const close = (e: MouseEvent) => { if (pop.current && !pop.current.contains(e.target as Node)) setOpen(false); };
    const esc = (e: KeyboardEvent) => { if (e.key === 'Escape') setOpen(false); };
    document.addEventListener('click', close);
    document.addEventListener('keydown', esc);
    return () => { document.removeEventListener('click', close); document.removeEventListener('keydown', esc); };
  }, [open]);

  return (
    <>
      <header>
        <div className="wrap bar">
          <div className="brand">
            <div className="logo" aria-hidden="true"><LogoIcon /></div>
            <div className="brand-name">Study Assistant <span className="beta">beta</span><small>{t('brand.sub')}</small></div>
          </div>
          <div className="spacer" />
          <div className="lang" role="group" aria-label={t('aria.lang')}>
            {(['en', 'de'] as const).map((l) => (
              <button key={l} aria-pressed={lang === l} onClick={() => setLang(l)}>{l.toUpperCase()}</button>
            ))}
          </div>
          <button className="icon-btn" id="bell" aria-label={t('aria.bell')} aria-expanded={open} onClick={(e) => { e.stopPropagation(); setOpen(!open); }}>
            <BellIcon />
            {unread > 0 && <span className="badge" id="badge">{unread}</span>}
          </button>
          <button className="profile-chip" id="profileChip" aria-label={t('aria.profile')} onClick={() => openProfile(true)}>
            <span className="avatar" id="avatar">{profile && profile.department ? (deptShort(profile.department).replace(/\D/g, '') || '★') : '?'}</span>
            <span className="txt" id="profileText">{parts.length ? parts.join(' · ') : t('profile.setup')}</span>
          </button>
        </div>
      </header>
      {open && (
        <div className="card popover" id="notifications" role="dialog" aria-label={t('notif.title')} ref={pop}>
          <div className="pop-head"><b>{t('notif.title')}</b><button className="link-btn" onClick={() => app.markRead(items.map((n) => n.id))}>{t('notif.markRead')}</button></div>
          <div id="notifList">
            {!items.length && <div className="empty">{t('notif.empty')}</div>}
            {items.map((n) => {
              const cls = `notif${app.read.has(n.id) ? '' : ' unread'}`;
              const body = <><span className="ic">{n.icon}</span><div className="txt">{n.text}<small>{n.sub}</small></div></>;
              const onClick = () => {
                app.markRead([n.id]);
                if (n.journey) { setOpen(false); app.openJourney(n.journey); }
              };
              return n.url
                ? <a key={n.id} className={cls} href={n.url} target="_blank" rel="noopener" onClick={onClick}>{body}</a>
                : <button key={n.id} type="button" className={cls} onClick={onClick}>{body}</button>;
            })}
          </div>
        </div>
      )}
    </>
  );
}
