export default function StatCard({ label, value, tone }) {
  return <div className={'stat ' + (tone || '')}><span>{label}</span><strong>{value}</strong></div>;
}
