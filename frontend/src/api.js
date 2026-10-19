const BASE = import.meta.env.VITE_API_URL || '';

let token = null;
try {
  token = localStorage.getItem('gavel_token');
} catch {
  /* storage unavailable */
}

export function setToken(t) {
  token = t;
  try {
    if (t) localStorage.setItem('gavel_token', t);
    else localStorage.removeItem('gavel_token');
  } catch {
    /* ignore */
  }
}
export const hasToken = () => !!token;

// Offset between the server clock and this browser's clock, so countdowns match the server.
let clockOffset = 0;
export const serverNow = () => Date.now() + clockOffset;

async function request(path, { method = 'GET', body } = {}) {
  const headers = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (token) headers.Authorization = `Bearer ${token}`;
  let res;
  try {
    res = await fetch(BASE + path, { method, headers, body: body !== undefined ? JSON.stringify(body) : undefined });
  } catch {
    const err = new Error('Cannot reach the server. Is the backend running?');
    err.status = 0;
    throw err;
  }
  const text = await res.text();
  const data = text ? safeJson(text) : null;
  if (!res.ok) {
    const err = new Error(data?.message || `Request failed (${res.status})`);
    err.status = res.status;
    err.errors = data?.errors;
    throw err;
  }
  return data;
}

function safeJson(text) {
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

const qs = (params) => {
  const p = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') p.set(k, v);
  });
  const s = p.toString();
  return s ? `?${s}` : '';
};

export const api = {
  register: (b) => request('/api/auth/register', { method: 'POST', body: b }),
  login: (b) => request('/api/auth/login', { method: 'POST', body: b }),
  me: () => request('/api/auth/me'),
  stats: async () => {
    const t0 = Date.now();
    const s = await request('/api/stats');
    const t1 = Date.now();
    if (s?.serverTime) clockOffset = new Date(s.serverTime).getTime() - (t0 + t1) / 2;
    return s;
  },
  auctions: (params) => request('/api/auctions' + qs(params)),
  categories: () => request('/api/auctions/categories'),
  auction: (id) => request(`/api/auctions/${id}`),
  createAuction: (b) => request('/api/auctions', { method: 'POST', body: b }),
  cancelAuction: (id) => request(`/api/auctions/${id}`, { method: 'DELETE' }),
  bids: (id) => request(`/api/auctions/${id}/bids?size=100`),
  placeBid: (id, amount) => request(`/api/auctions/${id}/bids`, { method: 'POST', body: { amount } }),
  myAuctions: () => request('/api/users/me/auctions?size=50'),
  myBids: () => request('/api/users/me/bids?size=50'),
  adminUsers: () => request('/api/admin/users?size=100'),
  setUserEnabled: (id, value) => request(`/api/admin/users/${id}/enabled?value=${value}`, { method: 'PATCH' }),
};
