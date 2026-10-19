import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api';
import { Countdown, Empty, Price, SkeletonGrid } from '../components';
import { subscribe } from '../live';
import { useAuth, useToast } from '../state';
import { money, timeAgo } from '../utils';

export default function Dashboard() {
  const { user } = useAuth();
  const toast = useToast();
  const [tab, setTab] = useState('bids');
  const [myAuctions, setMyAuctions] = useState(null);
  const [rows, setRows] = useState(null); // auctions I have bid on, with my latest bid

  const load = useCallback(async () => {
    try {
      const [mine, bids] = await Promise.all([api.myAuctions(), api.myBids()]);
      setMyAuctions(mine.content);
      // One row per auction: my highest bid, plus the auction's current state.
      const byAuction = new Map();
      bids.content.forEach((b) => {
        const cur = byAuction.get(b.auctionId);
        if (!cur || Number(b.amount) > Number(cur.amount)) byAuction.set(b.auctionId, b);
      });
      const auctions = await Promise.allSettled([...byAuction.keys()].map((id) => api.auction(id)));
      setRows(
        auctions
          .filter((r) => r.status === 'fulfilled')
          .map((r) => ({ auction: r.value, myBid: byAuction.get(r.value.id) }))
          .sort((x, y) => new Date(y.myBid.createdAt) - new Date(x.myBid.createdAt)),
      );
    } catch (e) {
      toast.error('Could not load your dashboard', e.message);
    }
  }, [toast]);

  useEffect(() => {
    load();
  }, [load]);

  // Any auction change may affect my standings; patch rows in place.
  useEffect(
    () =>
      subscribe('/topic/auctions', (ev) => {
        setRows((cur) => cur && cur.map((r) => (r.auction.id === ev.auction.id ? { ...r, auction: ev.auction } : r)));
        setMyAuctions((cur) => cur && cur.map((a) => (a.id === ev.auction.id ? ev.auction : a)));
      }),
    [],
  );

  async function cancel(a) {
    if (!window.confirm(`Cancel "${a.title}"?`)) return;
    try {
      await api.cancelAuction(a.id);
      toast.success('Auction cancelled');
      load();
    } catch (e) {
      toast.error('Could not cancel', e.message);
    }
  }

  const winning = rows?.filter((r) => r.auction.status === 'ACTIVE' && r.auction.highestBidderId === user.id).length ?? 0;
  const outbid = rows?.filter((r) => r.auction.status === 'ACTIVE' && r.auction.highestBidderId !== user.id).length ?? 0;
  const won = rows?.filter((r) => r.auction.status === 'CLOSED' && r.auction.highestBidderId === user.id).length ?? 0;

  return (
    <div className="page">
      <h1>Your dashboard</h1>
      <div className="stats stats-row">
        <div className="stat good"><strong>{winning}</strong><span>Currently winning</span></div>
        <div className="stat warn"><strong>{outbid}</strong><span>Outbid</span></div>
        <div className="stat"><strong>{won}</strong><span>Auctions won</span></div>
        <div className="stat"><strong>{myAuctions?.length ?? 0}</strong><span>Listings</span></div>
      </div>

      <div className="tabs">
        <button className={`tab ${tab === 'bids' ? 'active' : ''}`} onClick={() => setTab('bids')}>My bids</button>
        <button className={`tab ${tab === 'listings' ? 'active' : ''}`} onClick={() => setTab('listings')}>My listings</button>
      </div>

      {tab === 'bids' &&
        (rows === null ? (
          <SkeletonGrid n={3} />
        ) : rows.length === 0 ? (
          <Empty icon={'\u{1F528}'} title="No bids yet">Find something you love and place your first bid.</Empty>
        ) : (
          <div className="table-wrap">
            <table className="table">
              <thead><tr><th>Item</th><th>My bid</th><th>Current</th><th>Status</th><th>Time</th></tr></thead>
              <tbody>
                {rows.map(({ auction: a, myBid }) => {
                  const leading = a.highestBidderId === user.id;
                  const state =
                    a.status === 'CANCELLED' ? ['Cancelled', 'badge'] :
                    a.status === 'CLOSED' ? (leading ? ['Won', 'badge badge-good'] : ['Lost', 'badge badge-bad']) :
                    leading ? ['Winning', 'badge badge-good'] : ['Outbid', 'badge badge-warn'];
                  return (
                    <tr key={a.id}>
                      <td><Link to={`/auction/${a.id}`}>{a.title}</Link><small>bid {timeAgo(myBid.createdAt)}</small></td>
                      <td>{money(myBid.amount)}</td>
                      <td><Price value={a.currentPrice} /></td>
                      <td><span className={state[1]}>{state[0]}</span></td>
                      <td><Countdown endTime={a.endTime} status={a.status} /></td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        ))}

      {tab === 'listings' &&
        (myAuctions === null ? (
          <SkeletonGrid n={3} />
        ) : myAuctions.length === 0 ? (
          <Empty icon={'\u{1F4E6}'} title="Nothing listed yet">
            <Link to="/sell" className="btn btn-primary btn-sm">List your first item</Link>
          </Empty>
        ) : (
          <div className="table-wrap">
            <table className="table">
              <thead><tr><th>Item</th><th>Price</th><th>Bids</th><th>Status</th><th>Time</th><th /></tr></thead>
              <tbody>
                {myAuctions.map((a) => (
                  <tr key={a.id}>
                    <td><Link to={`/auction/${a.id}`}>{a.title}</Link></td>
                    <td><Price value={a.currentPrice} /></td>
                    <td>{a.bidCount}</td>
                    <td><span className={`badge ${a.status === 'ACTIVE' ? 'badge-good' : ''}`}>{a.status.toLowerCase()}</span></td>
                    <td><Countdown endTime={a.endTime} status={a.status} /></td>
                    <td>{a.status === 'ACTIVE' && a.bidCount === 0 && <button className="btn btn-ghost btn-sm" onClick={() => cancel(a)}>Cancel</button>}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ))}
    </div>
  );
}
