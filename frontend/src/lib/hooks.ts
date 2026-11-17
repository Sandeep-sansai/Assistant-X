import { useEffect, useRef, useState, type RefObject } from 'react';

export const REDUCED = typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;

export function useMediaQuery(query: string): boolean {
  const [matches, setMatches] = useState(() => matchMedia(query).matches);
  useEffect(() => {
    const m = matchMedia(query);
    const on = () => setMatches(m.matches);
    m.addEventListener('change', on);
    return () => m.removeEventListener('change', on);
  }, [query]);
  return matches;
}

// Sections fade up as they scroll into view; without IntersectionObserver (or with reduced motion) they just show.
const canReveal = typeof IntersectionObserver !== 'undefined' && !REDUCED;
if (canReveal) document.documentElement.classList.add('js-reveal');

/** Adds "reveal" and, once visible, "in" to the element; returns whether it has been seen. */
export function useReveal<E extends HTMLElement>(): [RefObject<E>, boolean] {
  const ref = useRef<E>(null);
  const [seen, setSeen] = useState(!canReveal);
  useEffect(() => {
    const node = ref.current;
    if (!node || !canReveal) return;
    node.classList.add('reveal');
    const io = new IntersectionObserver((entries) => {
      if (entries.some((e) => e.isIntersecting)) { node.classList.add('in'); setSeen(true); io.disconnect(); }
    }, { threshold: 0.12 });
    io.observe(node);
    return () => io.disconnect();
  }, []);
  return [ref, seen];
}

/** A number that counts up to {@code to} once {@code run} is true. */
export function useCountUp(to: number, run: boolean, ms = 800): number {
  const [value, setValue] = useState(REDUCED ? to : 0);
  useEffect(() => {
    if (!run) return;
    if (REDUCED || !to) { setValue(to); return; }
    let frame = 0;
    const t0 = performance.now();
    const step = (now: number) => {
      const p = Math.min(1, (now - t0) / ms);
      setValue(Math.round(to * (1 - Math.pow(1 - p, 3))));
      if (p < 1) frame = requestAnimationFrame(step);
    };
    frame = requestAnimationFrame(step);
    return () => cancelAnimationFrame(frame);
  }, [to, run, ms]);
  return value;
}

/** Keeps a native <dialog> in step with an "open" flag. */
export function useDialog(open: boolean, onClose: () => void): RefObject<HTMLDialogElement> {
  const ref = useRef<HTMLDialogElement>(null);
  const closeRef = useRef(onClose);
  closeRef.current = onClose;
  useEffect(() => {
    const d = ref.current;
    if (!d) return;
    if (open && !d.open) d.showModal();
    if (!open && d.open) d.close();
  }, [open]);
  useEffect(() => {
    const d = ref.current;
    if (!d) return;
    const handler = () => closeRef.current();
    d.addEventListener('close', handler);
    return () => d.removeEventListener('close', handler);
  }, []);
  return ref;
}
