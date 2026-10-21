import { useEffect } from 'react';
const tones = ['#0B7A75', '#B26A00', '#6B4FBB', '#C2415D', '#2B6CB0', '#2F855A'];
export function Avatar({ name, i = 0, size = 34 }) {
  const ini = name.split(' ').map((w) => w[0]).slice(0, 2).join('');
  return <span className="avatar" style={{ background: tones[i % tones.length], width: size, height: size, fontSize: size * 0.38 }} aria-hidden="true">{ini}</span>;
}
export function Modal({ title, onClose, children }) {
  useEffect(() => {
    const k = (e) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', k); return () => window.removeEventListener('keydown', k);
  }, [onClose]);
  return (
    <div className="overlay" onMouseDown={onClose}>
      <div className="modal" role="dialog" aria-modal="true" aria-label={title} onMouseDown={(e) => e.stopPropagation()}>
        <div className="mh"><h2>{title}</h2><button className="link" onClick={onClose}>Close</button></div>
        {children}
      </div>
    </div>
  );
}
