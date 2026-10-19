import { Client } from '@stomp/stompjs';

// One shared STOMP connection for the whole app. Components subscribe to topics and get
// automatic re-subscription whenever the socket reconnects.
const listeners = new Map(); // topic -> Set<callback>
const stompSubs = new Map(); // topic -> STOMP subscription
const statusListeners = new Set();
let client = null;
let connected = false;

function wsUrl() {
  const base = import.meta.env.VITE_API_URL || window.location.origin;
  return base.replace(/^http/, 'ws') + '/ws';
}

function setConnected(v) {
  connected = v;
  statusListeners.forEach((cb) => cb(v));
}

function bind(topic) {
  if (!client || !connected || stompSubs.has(topic)) return;
  const sub = client.subscribe(topic, (msg) => {
    let event;
    try {
      event = JSON.parse(msg.body);
    } catch {
      return;
    }
    listeners.get(topic)?.forEach((cb) => cb(event));
  });
  stompSubs.set(topic, sub);
}

function ensureClient() {
  if (client) return;
  client = new Client({
    brokerURL: wsUrl(),
    reconnectDelay: 3000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
  });
  client.onConnect = () => {
    stompSubs.clear();
    setConnected(true);
    listeners.forEach((_, topic) => bind(topic));
  };
  client.onWebSocketClose = () => {
    stompSubs.clear();
    setConnected(false);
  };
  client.activate();
}

export function subscribe(topic, callback) {
  ensureClient();
  let set = listeners.get(topic);
  if (!set) {
    set = new Set();
    listeners.set(topic, set);
  }
  set.add(callback);
  bind(topic);
  return () => {
    set.delete(callback);
    if (set.size === 0) {
      listeners.delete(topic);
      stompSubs.get(topic)?.unsubscribe();
      stompSubs.delete(topic);
    }
  };
}

export function onConnectionChange(cb) {
  ensureClient();
  statusListeners.add(cb);
  cb(connected);
  return () => statusListeners.delete(cb);
}
