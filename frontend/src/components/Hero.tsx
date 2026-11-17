import { useMemo } from 'react';
import { useApp } from '../AppContext';
import { chips } from '../lib/logic';
import { semesterOf } from '../lib/dates';
import { AskForm } from './AskForm';
import { useDeptShort } from './Header';

/** Suggested questions: shown under the hero box on narrow screens and above the chat input. */
export function useChips() {
  const { t, journeys, journeyStates, calendar } = useApp();
  return useMemo(() => chips(t, journeys, journeyStates, calendar), [t, journeys, journeyStates, calendar]);
}

export function ChipList({ className, id }: { className: string; id?: string }) {
  const { t, openChat } = useApp();
  const list = useChips();
  return (
    <div className={className} id={id} aria-label={t('aria.chips')}>
      {list.map((c) => (
        <button key={c.text} type="button" className={`chip${c.soon ? ' soon' : ''}`} onClick={() => openChat(c.text)}>
          {c.tag && <span className="tag">{c.tag}</span>}{c.text}
        </button>
      ))}
    </div>
  );
}

export function Hero() {
  const { t, profile, openChat } = useApp();
  const deptShort = useDeptShort();
  const sem = semesterOf(profile);
  const who = profile && profile.department ? ` · ${deptShort(profile.department)}${sem && sem > 0 ? t('greet.sem', sem) : ''}` : '';
  return (
    <section className="hero" aria-labelledby="heroTitle">
      <p className="greet" id="greet">{t('greet', new Date().getHours()) + who}</p>
      {/* Our own constant strings only (for the <em> in the headline). */}
      <h1 id="heroTitle">
        <span className="t-narrow" dangerouslySetInnerHTML={{ __html: t('hero.title') }} />
        <span className="t-wide" dangerouslySetInnerHTML={{ __html: t('hero.dash') }} />
      </h1>
      <AskForm id="heroAsk" className="narrow-only" placeholderKey="hero.placeholder" statusClass="narrow-only"
        sendLabel={<span>{t('hero.ask')}</span>} onAsk={(q, voice) => openChat(q, voice)} />
      <ChipList className="chips narrow-only" id="chips" />
    </section>
  );
}
