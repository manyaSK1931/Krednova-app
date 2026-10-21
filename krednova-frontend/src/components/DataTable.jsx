export default function DataTable({ cols, rows, empty = 'Nothing to show yet.' }) {
  if (!rows.length) return <p className="muted">{empty}</p>;
  return (
    <div className="tbl"><table>
      <thead><tr>{cols.map((c) => <th key={c.key}>{c.label}</th>)}</tr></thead>
      <tbody>{rows.map((r, i) => (
        <tr key={r.id ?? i}>{cols.map((c) => <td key={c.key}>{c.render ? c.render(r) : r[c.key]}</td>)}</tr>
      ))}</tbody>
    </table></div>
  );
}
