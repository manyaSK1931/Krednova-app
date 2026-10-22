// Talks to the Spring Boot API (through the Vercel/Vite proxy). The session cookie is HttpOnly, so React never sees it.
const BASE = '/api/v1';

const xsrf = () => decodeURIComponent((document.cookie.split('; ').find((c) => c.startsWith('XSRF-TOKEN=')) || '').split('=')[1] || '');
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// Lets the UI show "server is waking up" while a request is slow (free hosting sleeps when idle).
const listeners = new Set();
export const onSlow = (fn) => { listeners.add(fn); return () => listeners.delete(fn); };
let slowCount = 0;
const slow = (d) => { slowCount = Math.max(0, slowCount + d); listeners.forEach((fn) => fn(slowCount > 0)); };

const SERVER_DOWN = [502, 503, 504];
const WAKING = 'The server is still starting up. Please wait a moment and try again.';

/** fetch that keeps trying while the server wakes up (gateway errors / network failures), for up to ~1 minute. */
async function resilientFetch(url, init, canRetry) {
  const tries = canRetry ? 8 : 1;
  let raised = false;
  const timer = setTimeout(() => { raised = true; slow(+1); }, 3000);
  try {
    for (let i = 1; i <= tries; i++) {
      try {
        const res = await fetch(url, init);
        if (SERVER_DOWN.includes(res.status) && i < tries) { await sleep(5000); continue; }
        return res;
      } catch (e) {
        if (i >= tries) throw e;
        await sleep(5000);
      }
    }
  } finally {
    clearTimeout(timer);
    if (raised) slow(-1);
  }
}

export const warmUp = () => { fetch(BASE + '/auth/csrf', { credentials: 'include' }).catch(() => {}); };

export const ensureCsrf = async () => {
  try { return await resilientFetch(BASE + '/auth/csrf', { credentials: 'include' }, true); } catch { return null; }
};

export async function api(path, { method = 'GET', body } = {}) {
  const headers = { Accept: 'application/json' };
  if (body) headers['Content-Type'] = 'application/json';
  if (method !== 'GET') headers['X-XSRF-TOKEN'] = xsrf();
  // Only safe-to-repeat calls are retried automatically: reads and signing in.
  const canRetry = method === 'GET' || path === '/auth/login';
  let res;
  try {
    res = await resilientFetch(BASE + path, { method, credentials: 'include', headers, body: body ? JSON.stringify(body) : undefined }, canRetry);
  } catch {
    const e = new Error(WAKING); e.status = 0; throw e;
  }
  let data = null;
  try { data = await res.json(); } catch { /* empty or non-JSON response */ }
  if (res.status === 401 && path !== '/auth/login' && path !== '/auth/me') window.dispatchEvent(new Event('kn-unauthorized'));
  if (!res.ok) {
    const msg = (data && data.message) || (SERVER_DOWN.includes(res.status) ? WAKING : 'Something went wrong. Please try again.');
    const e = new Error(msg); e.status = res.status; throw e;
  }
  return data;
}
