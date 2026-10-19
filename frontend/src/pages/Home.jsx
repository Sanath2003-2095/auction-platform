import { useCallback, useEffect, useRef, useState } from 'react';
import { api } from '../api';
import { AuctionCard, Empty, SkeletonGrid } from '../components';
import { subscribe } from '../live';
import { useToast, useWatchlist } from '../state';
import { useDebounced } from '../utils';

const TABS = [
  { key: 'ACTIVE', label: 'Live now' },
  { key: 'CLOSED', label: 'Ended' },
  { key: 'WATCH', label: 'Watchlist' },
];
const SORTS = [
  { key: 'ending', label: 'Ending soonest' },
  { key: 'newest', label: 'Newly listed' },
  { key: 'popular', label: 'Most bids' },
  { key: 'price_asc', label: 'Price: low to high' },
  { key: 'price_desc', label: 'Price: high to low' },
];

export default function Home() {
  const toast = useToast();
  const watch = useWatchlist();
  const [tab, setTab] = useState('ACTIVE');
  const [q, setQ] = useState('');
  const [category, setCategory] = useState('');
  const [sort, setSort] = useState('ending');
  const [categories, setCategories] = useState([]);
  const [items, setItems] = useState([]);
  const [page, setPage] = useState({ page: 0, totalPages: 0, totalElements: 0 });
  const [loading, setLoading] = useState(true);
  const [stats, setStats] = useState(null);
  const [fresh, setFresh] = useState({});
  const dq = useDebounced(q);
  const reqId = useRef(0);

  const filterRef = useRef({});
  filterRef.current = { tab, dq, category };

  const load = useCallback(
    async (pageNo = 0) => {
      const id = ++reqId.current;
      setLoading(true);
      try {
        if (tab === 'WATCH') {
          const res = await Promise.allSettled(watch.ids.map((i) => api.auction(i)));
          if (id !== reqId.current) return;
          setItems(res.filter((r) => r.status === 'fulfilled').map((r) => r.value));
          setPage({ page: 0, totalPages: 1, totalElements: watch.ids.length });
        } else {
          const res = await api.auctions({ status: tab, q: dq, category, sort, page: pageNo, size: 12 });
          if (id !== reqId.current) return;
          setItems((cur) => (pageNo === 0 ? res.content : [...cur, ...res.content.filter((n) => !cur.some((c) => c.id === n.id))]));
          setPage(res);
        }
      } catch (e) {
        if (id === reqId.current) toast.error('Could not load auctions', e.message);
      } finally {
        if (id === reqId.current) setLoading(false);
      }
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [tab, dq, category, sort, tab === 'WATCH' ? watch.ids.length : 0],
  );

  useEffect(() => {
    load(0);
  }, [load]);

  useEffect(() => {
    api.categories().then(setCategories).catch(() => {});
    const refresh = () => api.stats().then(setStats).catch(() => {});
    refresh();
    const t = setInterval(refresh, 15000);
    return () => clearInterval(t);
  }, []);

  // Live feed: patch cards in place; prepend newly created listings that match the filters.
  useEffect(
    () =>
      subscribe('/topic/auctions', (ev) => {
        const a = ev.auction;
        setItems((cur) => {
          const idx = cur.findIndex((x) => x.id === a.id);
          if (idx >= 0) {
            if (cur[idx].bidCount > a.bidCount) return cur;
            const next = [...cur];
            next[idx] = a;
            return next;
          }
          const f = filterRef.current;
          if (ev.type === 'CREATED' && f.tab === 'ACTIVE' && !f.dq && (!f.category || f.category.toLowerCase() === a.category.toLowerCase())) {
            setFresh((s) => ({ ...s, [a.id]: true }));
            return [a, ...cur];
          }
          return cur;
        });
      }),
    [],
  );

  const showing = items;
  return (
    <div className="page">
      <section className="hero">
        <div>
          <h1>Bid live. <span className="grad">Win in real time.</span></h1>
          <p className="muted">Prices update the instant someone bids &mdash; no refreshing. Last-minute bids extend the clock, so every second counts.</p>
        </div>
        <div className="stats">
          <Stat label="Live auctions" value={stats?.activeAuctions} />
          <Stat label="Bids placed" value={stats?.totalBids} />
          <Stat label="Members" value={stats?.totalUsers} />
        </div>
      </section>

      <div className="toolbar">
        <div className="tabs">
          {TABS.map((t) => (
            <button key={t.key} className={`tab ${tab === t.key ? 'active' : ''}`} onClick={() => setTab(t.key)}>
              {t.label}
              {t.key === 'WATCH' && watch.ids.length > 0 && <em>{watch.ids.length}</em>}
            </button>
          ))}
        </div>
        <div className="filters">
          <input className="input search" placeholder="Search auctions..." value={q} onChange={(e) => setQ(e.target.value)} disabled={tab === 'WATCH'} />
          <select className="input" value={sort} onChange={(e) => setSort(e.target.value)} disabled={tab === 'WATCH'}>
            {SORTS.map((s) => <option key={s.key} value={s.key}>{s.label}</option>)}
          </select>
        </div>
      </div>

      {tab !== 'WATCH' && categories.length > 0 && (
        <div className="chips">
          <button className={`chip-btn ${category === '' ? 'active' : ''}`} onClick={() => setCategory('')}>All</button>
          {categories.map((c) => (
            <button key={c} className={`chip-btn ${category === c ? 'active' : ''}`} onClick={() => setCategory(c)}>{c}</button>
          ))}
        </div>
      )}

      {loading && showing.length === 0 ? (
        <SkeletonGrid />
      ) : showing.length === 0 ? (
        <Empty icon={tab === 'WATCH' ? '♡' : '\u{1F50D}'} title={tab === 'WATCH' ? 'Your watchlist is empty' : 'No auctions found'}>
          {tab === 'WATCH' ? 'Tap the heart on any auction to keep an eye on it.' : 'Try a different search, category or tab.'}
        </Empty>
      ) : (
        <>
          <div className="grid">
            {showing.map((a) => (
              <AuctionCard key={a.id} a={a} watched={watch.has(a.id)} onWatch={watch.toggle} fresh={fresh[a.id]} />
            ))}
          </div>
          {tab !== 'WATCH' && page.page + 1 < page.totalPages && (
            <div className="center">
              <button className="btn btn-secondary" disabled={loading} onClick={() => load(page.page + 1)}>
                {loading ? 'Loading...' : `Load more (${page.totalElements - items.length} left)`}
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
}

function Stat({ label, value }) {
  return (
    <div className="stat">
      <strong>{value === undefined || value === null ? '—' : value.toLocaleString()}</strong>
      <span>{label}</span>
    </div>
  );
}
