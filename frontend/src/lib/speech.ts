// Read-aloud with the device's own voices. Only one answer speaks at a time; buttons subscribe to
// know whether "their" answer is the one speaking.
import { useEffect, useState } from 'react';

let current: string | null = null;
const listeners = new Set<(id: string | null) => void>();
const notify = () => listeners.forEach((l) => l(current));

export const canSpeak = typeof window !== 'undefined' && 'speechSynthesis' in window;

export function stopSpeaking(): void {
  if (canSpeak) speechSynthesis.cancel();
  current = null;
  notify();
}

/** Reads an answer without citation markers and links. */
export function speak(text: string, id: string, lang: 'en' | 'de'): void {
  if (!canSpeak) return;
  stopSpeaking();
  const clean = text.replace(/\[(\d+(?:\s*,\s*\d+)*)\]/g, '').replace(/https?:\/\/\S+/g, '').replace(/\*\*/g, '');
  const utterance = new SpeechSynthesisUtterance(clean);
  utterance.lang = lang === 'de' ? 'de-DE' : 'en-GB';
  const voice = speechSynthesis.getVoices().find((v) => v.lang.startsWith(lang));
  if (voice) utterance.voice = voice;
  utterance.onend = () => { if (current === id) { current = null; notify(); } };
  current = id;
  notify();
  speechSynthesis.speak(utterance);
}

export function useSpeaking(): string | null {
  const [id, setId] = useState<string | null>(current);
  useEffect(() => { listeners.add(setId); return () => { listeners.delete(setId); }; }, []);
  return id;
}
