import { useState, type ReactNode } from 'react';
import { useApp } from '../AppContext';
import { canListen, useVoice } from '../lib/voice';
import { ArrowIcon, MicIcon } from './Icons';

/** The question box with voice input, used on the start page and in the chat. */
export function AskForm({ id, className, placeholderKey, onAsk, disabled, sendLabel, statusClass }: {
  id: string; className?: string; placeholderKey: string; onAsk: (q: string, voice: boolean) => void; disabled?: boolean;
  sendLabel?: ReactNode; statusClass?: string;
}) {
  const { t, lang } = useApp();
  const [text, setText] = useState('');
  const voice = useVoice(t, lang, setText, (q) => onAsk(q, true));
  return (
    <>
      <form className={`ask ${className || ''}`} id={id} onSubmit={(e) => {
        e.preventDefault();
        const q = text.trim();
        if (!q) return;
        setText('');
        onAsk(q, false);
      }}>
        <label className="sr-only" htmlFor={`${id}Input`}>{t('aria.question')}</label>
        <input id={`${id}Input`} maxLength={1000} autoComplete="off" placeholder={t(placeholderKey)} value={text} onChange={(e) => setText(e.target.value)} />
        {canListen && (
          <button className={`mic${voice.mode === 'on' ? ' on' : ''}${voice.mode === 'waiting' ? ' waiting' : ''}`} type="button"
            aria-pressed={voice.mode !== 'off'} aria-label={voice.mode === 'off' ? t('voice.start') : t('voice.stop')} onClick={voice.toggle}>
            <MicIcon />
          </button>
        )}
        <button className="send" disabled={disabled} aria-label={sendLabel ? undefined : t('aria.send')}><ArrowIcon />{sendLabel}</button>
      </form>
      {voice.status && <p className={`voice-status ${statusClass || ''}`}>{voice.status}</p>}
    </>
  );
}
