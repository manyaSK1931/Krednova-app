export function Donut({ data, center, label = 'people' }) {
  const total = data.reduce((s, d) => s + d.value, 0) || 1, r = 52, c = 2 * Math.PI * r;
  let off = 0;
  return (
    <div className="donut">
      <svg viewBox="0 0 140 140" width="150" height="150" role="img" aria-label={data.map((d) => d.label + ' ' + d.value).join(', ')}>
        <circle cx="70" cy="70" r={r} fill="none" stroke="var(--line)" strokeWidth="18" />
        {data.map((d) => {
          const len = (d.value / total) * c;
          const el = <circle key={d.label} cx="70" cy="70" r={r} fill="none" stroke={d.color} strokeWidth="18"
            strokeDasharray={len + ' ' + (c - len)} strokeDashoffset={-off} transform="rotate(-90 70 70)" />;
          off += len; return el;
        })}
        <text x="70" y="70" textAnchor="middle" className="dn">{center}</text>
        <text x="70" y="88" textAnchor="middle" className="dl">{label}</text>
      </svg>
      <ul className="legend">{data.map((d) => <li key={d.label}><i style={{ background: d.color }} />{d.label} <b>{d.value}</b></li>)}</ul>
    </div>
  );
}
export function Bars({ data, fmt = (v) => v }) {
  const max = Math.max(...data.map((d) => d.value), 1);
  return (
    <div className="bars">{data.map((d) => (
      <div className="bar" key={d.label}><span>{d.label}</span>
        <div className="bt"><div className="bf" style={{ width: (d.value / max) * 100 + '%' }} /></div><b>{fmt(d.value)}</b></div>
    ))}</div>
  );
}
export function Columns({ data, max }) {
  const m = max || Math.max(...data.map((d) => d.value), 1);
  return (
    <div className="colchart">{data.map((d) => (
      <div key={d.label} className="colm"><b>{d.value}</b><div className="cf" style={{ height: (d.value / m) * 100 + '%' }} /><span>{d.label}</span></div>
    ))}</div>
  );
}
