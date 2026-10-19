import { useEffect, useState, useSyncExternalStore } from 'react';
import { serverNow } from './api';

const fmt = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', minimumFractionDigits: 2 });
export const money = (v) => fmt.format(Number(v));

export function timeAgo(iso) {
  const s = Math.max(0, Math.floor((serverNow() - new Date(iso).getTime()) / 1000));
  if (s < 5) return 'just now';
  if (s < 60) return `${s}s ago`;
  if (s < 3600) return `${Math.floor(s / 60)}m ago`;
  if (s < 86400) return `${Math.floor(s / 3600)}h ago`;
  return `${Math.floor(s / 86400)}d ago`;
}

// ---- shared 1-second ticker so hundreds of countdowns cost one timer ----
let tick = 0;
let timer = null;
const tickSubs = new Set();
function subscribeTick(cb) {
  tickSubs.add(cb);
  if (!timer) {
    timer = setInterval(() => {
      tick++;
      tickSubs.forEach((f) => f());
    }, 1000);
  }
  return () => {
    tickSubs.delete(cb);
    if (!tickSubs.size) {
      clearInterval(timer);
      timer = null;
    }
  };
}
export function useNow() {
  useSyncExternalStore(subscribeTick, () => tick);
  return serverNow();
}

export function remaining(endTime, now) {
  return Math.max(0, new Date(endTime).getTime() - now);
}

export function formatRemaining(ms) {
  const total = Math.floor(ms / 1000);
  const d = Math.floor(total / 86400);
  const h = Math.floor((total % 86400) / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  const pad = (n) => String(n).padStart(2, '0');
  if (d > 0) return `${d}d ${h}h`;
  if (h > 0) return `${h}h ${pad(m)}m`;
  return `${pad(m)}:${pad(s)}`;
}

export function urgency(ms) {
  if (ms <= 0) return 'ended';
  if (ms < 60_000) return 'critical';
  if (ms < 5 * 60_000) return 'warn';
  return 'ok';
}

export function useLocalStorage(key, initial) {
  const [value, setValue] = useState(() => {
    try {
      const raw = localStorage.getItem(key);
      return raw ? JSON.parse(raw) : initial;
    } catch {
      return initial;
    }
  });
  useEffect(() => {
    try {
      localStorage.setItem(key, JSON.stringify(value));
    } catch {
      /* ignore */
    }
  }, [key, value]);
  return [value, setValue];
}

export function useDebounced(value, ms = 300) {
  const [v, setV] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setV(value), ms);
    return () => clearTimeout(t);
  }, [value, ms]);
  return v;
}

export const PLACEHOLDER =
  "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='800' height='600'%3E%3Crect width='100%25' height='100%25' fill='%23141a2b'/%3E%3Ctext x='50%25' y='50%25' fill='%238b95b5' font-family='sans-serif' font-size='28' text-anchor='middle'%3ENo image%3C/text%3E%3C/svg%3E";
