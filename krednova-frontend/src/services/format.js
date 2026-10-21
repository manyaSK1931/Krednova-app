export const inr = (n) => '₹' + Math.round(Number(n) || 0).toLocaleString('en-IN');
export const isoToday = () => new Date().toLocaleDateString('en-CA');
export const isoPlus = (days) => { const d = new Date(); d.setDate(d.getDate() + days); return d.toLocaleDateString('en-CA'); };
export const monthName = (period) => new Date(+period.slice(0, 4), +period.slice(5, 7) - 1, 1).toLocaleDateString('en-IN', { month: 'long', year: 'numeric' });
export const fmtDate = (iso) => new Date(iso + 'T00:00:00').toLocaleDateString('en-IN', { day: 'numeric', month: 'short' });
export const clock12 = (hhmm) => { const [h, m] = hhmm.split(':').map(Number); return (h % 12 || 12) + ':' + String(m).padStart(2, '0') + (h < 12 ? ' AM' : ' PM'); };
export const cap = (s) => s.charAt(0) + s.slice(1).toLowerCase();

// Spreadsheet formulas can run from CSV cells that start with = + - @ (or a tab / carriage return), so those are neutralised.
export function downloadCSV(name, rows) {
  const esc = (v) => { const s = String(v ?? ''); return '"' + (/^[=+\-@\t\r]/.test(s) ? "'" + s : s).replace(/"/g, '""') + '"'; };
  const blob = new Blob([rows.map((r) => r.map(esc).join(',')).join('\n')], { type: 'text/csv' });
  const a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = name; a.click(); URL.revokeObjectURL(a.href);
}
