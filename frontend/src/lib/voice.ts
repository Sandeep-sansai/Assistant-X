// Voice input: words appear in the box while the student talks, and the question is sent when they
// stop. The microphone is requested first, because speech recognition reports a refused permission
// inconsistently (sometimes not at all).
import { useCallback, useEffect, useRef, useState } from 'react';
import type { T } from '../i18n/i18n';
import { stopSpeaking } from './speech';

const Recognition: any = typeof window !== 'undefined' ? ((window as any).SpeechRecognition || (window as any).webkitSpeechRecognition) : null;
export const canListen = Boolean(Recognition);

// Only one form listens at a time.
let active: any = null;

/** null when access was granted, otherwise the message to show. */
async function microphoneProblem(t: T): Promise<string | null> {
  if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) return null;   // let recognition ask itself
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    stream.getTracks().forEach((track) => track.stop());   // only needed the permission
    return null;
  } catch (err: any) {
    if (err && (err.name === 'NotFoundError' || err.name === 'OverconstrainedError')) return t('voice.noMic');
    return t('voice.denied');   // refused, dismissed, or blocked by policy
  }
}

export type MicMode = 'off' | 'waiting' | 'on';

/** Microphone state for one form: its look, the status line, and the toggle. */
export function useVoice(t: T, lang: 'en' | 'de', setText: (s: string) => void, onQuestion: (q: string) => void) {
  const [mode, setMode] = useState<MicMode>('off');
  const [status, setStatus] = useState('');
  const starting = useRef(false);
  const cancelled = useRef(false);
  const rec = useRef<any>(null);
  const onQ = useRef(onQuestion);
  onQ.current = onQuestion;

  useEffect(() => () => { if (rec.current) rec.current.stop(); }, []);

  const toggle = useCallback(async () => {
    if (rec.current) { rec.current.stop(); return; }
    if (starting.current) {
      // Tapping again while the browser hasn't answered the permission request cancels it.
      cancelled.current = true;
      starting.current = false;
      setMode('off');
      setStatus('');
      return;
    }
    if (active) active.stop();
    stopSpeaking();
    starting.current = true;
    cancelled.current = false;
    setMode('waiting');
    setStatus(t('voice.permission'));
    const problem = await microphoneProblem(t);
    if (cancelled.current) return;
    if (problem) { starting.current = false; setMode('off'); setStatus(problem); return; }
    const r = new Recognition();
    r.lang = lang === 'de' ? 'de-DE' : 'en-GB';
    r.interimResults = true;
    r.maxAlternatives = 1;
    let finalText = '';
    let error: string | null = null;
    r.onstart = () => { starting.current = false; setMode('on'); setStatus(`🎙 ${t('voice.listening')} ${t('voice.notice')}`); };
    r.onresult = (e: any) => {
      let text = '';
      for (const result of e.results) text += result[0].transcript;
      setText(text);
      if (e.results[e.results.length - 1].isFinal) finalText = text.trim();
    };
    r.onerror = (e: any) => { error = e.error; };
    r.onend = () => {
      rec.current = null;
      if (active === r) active = null;
      starting.current = false;
      setMode('off');
      if (finalText) { setStatus(''); setText(''); onQ.current(finalText); return; }
      if (error === 'not-allowed' || error === 'service-not-allowed') setStatus(t('voice.denied'));
      else if (error === 'audio-capture') setStatus(t('voice.noMic'));
      else if (error === 'no-speech' || error === 'aborted' || !error) setStatus(t('voice.nothing'));
      else setStatus(t('voice.failed'));
    };
    rec.current = r;
    active = r;
    try { r.start(); } catch { rec.current = null; starting.current = false; setMode('off'); setStatus(t('voice.failed')); }
  }, [t, lang, setText]);

  const stop = useCallback(() => { if (rec.current) rec.current.stop(); }, []);
  return { mode, status, toggle, stop };
}
