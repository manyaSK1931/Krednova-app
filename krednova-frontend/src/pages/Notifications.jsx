import { Link } from 'react-router-dom';
import { useNotifications } from '../context/NotificationsContext';
export default function Notifications() {
  const { items, unread, readAll, readOne } = useNotifications();
  return (
    <>
      <div className="headrow"><div><h1>Notifications</h1><p className="muted">{unread ? unread + ' unread' : 'You are all caught up.'}</p></div>
        <button className="btn alt" disabled={!unread} onClick={readAll}>Mark all as read</button></div>
      {!items.length && <p className="muted">Nothing needs your attention right now.</p>}
      <ul className="notifs">{items.map((n) => (
        <li key={n.id} className={n.read ? '' : 'unread'}>
          <div><b>{n.title}</b><p>{n.text}</p>{n.to && <Link to={n.to} onClick={() => readOne(n.id)}>Open</Link>}</div>
          {!n.read && <button className="link" onClick={() => readOne(n.id)}>Mark as read</button>}
        </li>))}</ul>
    </>
  );
}
