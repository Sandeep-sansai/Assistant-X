import { useEffect, useRef, useState } from 'react';
import { useApp } from '../AppContext';
import { postNoContent } from '../lib/api';
import { SHORT, fmt, parseDay, semesterOf, termFromIndex, termId, termIndex, termLabel, termOf } from '../lib/dates';
import { useDialog } from '../lib/hooks';
import { mineItems } from '../lib/logic';
import type { Feed, Profile } from '../lib/types';
import { CloseIcon } from './Icons';

/** Department, programme and study start; shows a live preview of the feed for that choice. */
export function ProfileDialog() {
  const { t, departments, profile, saveProfile, profileOpen, openProfile, loadFeed, lang } = useApp();
  const [draft, setDraft] = useState<Profile>({ department: null, programme: null, start: null });
  const result = useRef<'save' | 'skip' | null>(null);
  const ref = useDialog(profileOpen, () => {
    if (result.current === 'save') saveProfile({ department: draft.department || null, programme: (draft.programme || '').trim() || null, start: draft.start || null });
    else if (!profile) saveProfile({ department: null, programme: null, start: null });
    result.current = null;
    openProfile(false);
  });
  useEffect(() => { if (profileOpen) setDraft(profile || { department: null, programme: null, start: null }); }, [profileOpen, profile]);

  // Live preview of what the feed would show; a newer choice wins over a slower answer.
  const cache = useRef(new Map<string, Feed>());
  useEffect(() => { cache.current.clear(); }, [lang]);
  const [preview, setPreview] = useState<Feed | null>(null);
  useEffect(() => {
    let live = true;
    setPreview(null);
    const dept = draft.department;
    if (!dept) return;
    const cached = cache.current.get(dept);
    if (cached) { setPreview(cached); return; }
    loadFeed(dept).then((f) => { if (f) cache.current.set(dept, f); if (live && f) setPreview(f); });
    return () => { live = false; };
  }, [draft.department, loadFeed]);

  const now = termIndex(termOf(new Date()));
  const starts = Array.from({ length: 14 }, (_, k) => termFromIndex(now + 1 - k));
  const sem = semesterOf(draft);
  const dept = departments.find((d) => d.id === draft.department);
  const items = preview && draft.department ? mineItems([...preview.events, ...preview.news], preview.departmentCovered, draft).slice(0, 4) : [];
  return (
    <dialog className="wide" aria-labelledby="profileTitle" ref={ref}>
      <form method="dialog" className="dlg" onSubmit={(e) => { result.current = ((e.nativeEvent as SubmitEvent).submitter as HTMLButtonElement | null)?.value === 'save' ? 'save' : 'skip'; }}>
        <h2 id="profileTitle">{t('profile.title')}</h2>
        <p>{t('profile.intro')}</p>
        <label className="field"><span>{t('profile.dept')}</span>
          <select value={draft.department || ''} onChange={(e) => setDraft({ ...draft, department: e.target.value || null })}>
            <option value="">{t('profile.deptNone')}</option>
            {departments.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
          </select>
        </label>
        <label className="field"><span>{t('profile.prog')}</span>
          <input list="pProgList" maxLength={100} placeholder={t('profile.progPh')} value={draft.programme || ''} onChange={(e) => setDraft({ ...draft, programme: e.target.value })} />
          <datalist id="pProgList">{(dept ? dept.programmes : []).map((p) => <option key={p} value={p} />)}</datalist>
        </label>
        <label className="field"><span>{t('profile.start')}</span>
          <select value={draft.start || ''} onChange={(e) => setDraft({ ...draft, start: e.target.value || null })}>
            <option value="">{t('profile.startNone')}</option>
            {starts.map((term) => <option key={termId(term)} value={termId(term)}>{termLabel(t, term)}</option>)}
          </select>
          <span className="field-hint">{sem === null ? '' : sem > 0 ? t('profile.semNow', sem) : t('profile.semFuture')}</span>
        </label>
        <div className="preview" aria-live="polite"><b>{t('preview.title')}</b>
          <ul>
            {!draft.department ? <li className="muted">{t('preview.empty')}</li>
              : preview && !items.length ? <li className="muted">{t('preview.none')}</li>
              : items.map(({ item, why }) => (
                <li key={item.url}><time>{fmt(t, parseDay(item.date), SHORT)}</time><span>{item.title}</span>{why.map((w) => <span key={w} className="pill why">{t(`why.${w}`)}</span>)}</li>
              ))}
          </ul>
        </div>
        <p className="privacy">{t('profile.privacy')}</p>
        <div className="row">
          <button className="btn" value="skip" type="submit">{t('profile.skip')}</button>
          <button className="btn primary" value="save" type="submit">{t('profile.save')}</button>
        </div>
      </form>
    </dialog>
  );
}

/** Anonymous feedback about the app from testers. */
export function FeedbackDialog() {
  const { t, lang, feedbackOpen, openFeedback } = useApp();
  const ref = useDialog(feedbackOpen, () => openFeedback(false));
  const [text, setText] = useState('');
  const [status, setStatus] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  useEffect(() => { if (feedbackOpen) { setStatus(null); setSending(false); } }, [feedbackOpen]);
  return (
    <dialog aria-labelledby="fbTitle" ref={ref}>
      <form className="dlg" id="feedbackForm" onSubmit={async (e) => {
        e.preventDefault();
        const message = text.trim();
        if (!message) return;
        setSending(true);
        try {
          await postNoContent('/api/site-feedback', { message, lang });
          setText('');
          setStatus(t('fb.thanks'));
          setTimeout(() => openFeedback(false), 1400);
        } catch {
          setStatus(t('fb.error'));
          setSending(false);
        }
      }}>
        <div className="jd-head"><h2 id="fbTitle">{t('fb.title')}</h2><button className="icon-btn" type="button" aria-label={t('aria.close')} onClick={() => openFeedback(false)}><CloseIcon /></button></div>
        <p className="fb-intro">{t('fb.intro')}</p>
        <label className="sr-only" htmlFor="fbText">{t('fb.title')}</label>
        <textarea id="fbText" rows={5} maxLength={2000} required placeholder={t('fb.placeholder')} value={text} onChange={(e) => setText(e.target.value)} autoFocus />
        <p className="fb-note">{t('fb.note')}</p>
        {status && <p className="fb-status">{status}</p>}
        <div className="row"><button className="btn primary" type="submit" disabled={sending}>{t('fb.send')}</button></div>
      </form>
    </dialog>
  );
}

export function Footer() {
  const { t, openFeedback } = useApp();
  return (
    <footer>
      <p>{t('footer')}</p>
      <p className="foot-row"><span>{t('footer.made')}</span> <button className="link-like" type="button" onClick={() => openFeedback(true)}>{t('fb.open')}</button></p>
    </footer>
  );
}
