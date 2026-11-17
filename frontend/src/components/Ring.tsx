/** An SVG ring showing credits out of the required total. */
export function Ring({ value, total, className }: { value: number; total: number; className?: string }) {
  const r = 52;
  const circumference = 2 * Math.PI * r;
  return (
    <svg viewBox="0 0 120 120" className={`ring-svg ${className || ''}`}>
      <circle cx="60" cy="60" r={r} className="ring-back" />
      <circle cx="60" cy="60" r={r} className="ring-front" style={{
        strokeDasharray: `${circumference}`,
        ['--ring-offset' as string]: String(circumference * (1 - Math.min(1, value / total))),
        ['--ring-full' as string]: String(circumference),
      }} />
      <text x="60" y="58" className="ring-value">{value}</text>
      <text x="60" y="80" className="ring-unit">{`/ ${total} CP`}</text>
    </svg>
  );
}
