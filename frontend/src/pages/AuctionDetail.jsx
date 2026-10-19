import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api } from '../api';
import { Confetti, Countdown, Empty, Price, PriceChart } from '../components';
import { subscribe } from '../live';
import { useAuth, useToast, useWatchlist } from '../state';
import { money, PLACEHOLDER, remaining, timeAgo, useNow } from '../utils';

export default function AuctionDetail() {
  const { id } = useParams();
  const { user } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  const watch = useWatchlist();
  const now = useNow();

  const [auction, setAuction] = useState(null);
  const [bids, setBids] = useState([]);
  const [error, setError] = useState(null);
  const [amount, setAmount] = useState('');
  const [touched, setTouched] = useState(false);
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState('');
  const [newBidId, setNewBidId] = useState(null);
  const [celebrate, setCelebrate] = useState(false);

  useEffect(() => {
    let alive = true;
    setAuction(null);
    setError(null);
    Promise.all([api.auction(id), api.bids(id)])
      .then(([a, b]) => {
        if (!alive) return;
        setAuction(a);
        setBids(b.content);
      })
      .catch((e) => alive && setError(e.message));
    return () => {
      alive = false;
    };
  }, [id]);

  // Real-time updates for this auction only.
  useEffect(
    () =>
      subscribe(`/topic/auctions/${id}`, (ev) => {
        setAuction((cur) => (cur && cur.bidCount > ev.auction.bidCount ? cur : ev.auction));
        if (ev.bid) {
          setBids((cur) => (cur.some((b) => b.id === ev.bid.id) ? cur : [ev.bid, ...cur]));
          setNewBidId(ev.bid.id);
        }
        if (ev.type === 'CLOSED' && user && ev.auction.highestBidderId === user.id) setCelebrate(true);
      }),
    [id, user],
  );

  // Keep the suggested bid in step with the live minimum unless the user is typing their own.
  useEffect(() => {
    if (auction && !touched) setAmount(Number(auction.minNextBid).toFixed(2));
  }, [auction, touched]);

  useEffect(() => {
    if (!celebrate) return;
    const t = setTimeout(() => setCelebrate(false), 6000);
    return () => clearTimeout(t);
  }, [celebrate]);

  const submit = useCallback(
    async (value) => {
      if (!user) {
        navigate(`/auth?next=${encodeURIComponent(`/auction/${id}`)}`);
        return;
      }
      setFormError('');
      setBusy(true);
      try {
        await api.placeBid(id, value);
        toast.success('Bid placed', `You bid ${money(value)}`);
        setTouched(false);
        const fresh = await api.auction(id);
        setAuction((cur) => (cur && cur.bidCount > fresh.bidCount ? cur : fresh));
      } catch (e) {
        setFormError(e.message);
        api.auction(id).then(setAuction).catch(() => {});
      } finally {
        setBusy(false);
      }
    },
    [user, id, navigate, toast],
  );

  if (error) return <div className="page"><Empty icon={'\u{1F6AB}'} title="Auction not found">{error}</Empty></div>;
  if (!auction) return <div className="page"><div className="card skeleton detail-sk"><div className="sk-img" /></div></div>;

  const a = auction;
  const active = a.status === 'ACTIVE' && remaining(a.endTime, now) > 0;
  const isSeller = user?.id === a.sellerId;
  const leading = user && a.highestBidderId === user.id;
  const min = Number(a.minNextBid);
  const inc = Number(a.minIncrement);
  const quick = [min, min + inc, min + inc * 4, min + inc * 9];
  const numeric = Number(amount);
  const tooLow = amount !== '' && numeric < min;
  const watched = watch.has(a.id);

  return (
    <div className="page detail">
      {celebrate && <Confetti />}
      <div className="crumbs"><Link to="/">&larr; All auctions</Link></div>
      <div className="detail-grid">
        <div className="detail-main">
          <div className="card hero-img">
            <img src={a.imageUrl || PLACEHOLDER} alt={a.title} onError={(e) => (e.currentTarget.src = PLACEHOLDER)} />
            <span className="chip chip-cat">{a.category}</span>
          </div>
          <div className="card pad">
            <div className="title-row">
              <h1>{a.title}</h1>
              <button className={`btn btn-secondary btn-sm ${watched ? 'on' : ''}`} onClick={() => watch.toggle(a.id)}>
                {watched ? '♥ Watching' : '♡ Watch'}
              </button>
            </div>
            <p className="muted small">Listed by <strong>{a.sellerName}</strong> &middot; {timeAgo(a.startTime)}</p>
            <p className="desc">{a.description}</p>
          </div>
          <div className="card pad">
            <h3>Price history</h3>
            <PriceChart auction={a} bids={bids} />
          </div>
        </div>

        <aside className="detail-side">
          <div className={`card pad bid-panel ${leading && active ? 'leading' : ''}`}>
            <div className="bid-top">
              <div>
                <div className="label">{a.bidCount ? 'Current bid' : 'Starting bid'}</div>
                <Price value={a.currentPrice} className="price-xl" />
              </div>
              <div className="right">
                <div className="label">{active ? 'Time left' : 'Status'}</div>
                <Countdown endTime={a.endTime} status={a.status} big />
              </div>
            </div>
            <div className="muted small bid-meta">
              {a.bidCount} {a.bidCount === 1 ? 'bid' : 'bids'}
              {a.highestBidderName && <> &middot; leader <strong>{a.highestBidderName}</strong></>}
            </div>

            {a.status === 'CANCELLED' ? (
              <div className="banner banner-bad">This auction was cancelled by the seller.</div>
            ) : !active ? (
              <div className={`banner ${leading ? 'banner-good' : ''}`}>
                {a.highestBidderName
                  ? leading
                    ? <>&#127881; You won this auction for <strong>{money(a.currentPrice)}</strong>!</>
                    : <>Sold to <strong>{a.highestBidderName}</strong> for <strong>{money(a.currentPrice)}</strong>.</>
                  : 'Ended with no bids.'}
              </div>
            ) : isSeller ? (
              <div className="banner">This is your listing. You cannot bid on it.</div>
            ) : (
              <>
                {leading ? (
                  <div className="banner banner-good">&#10003; You&rsquo;re the highest bidder. Sit tight!</div>
                ) : user && bids.some((b) => b.bidderId === user.id) ? (
                  <div className="banner banner-warn">You&rsquo;ve been outbid &mdash; bid again to take the lead.</div>
                ) : null}

                <div className="quick">
                  {quick.map((v, i) => (
                    <button key={i} className="chip-btn" disabled={busy || leading} onClick={() => { setTouched(true); setAmount(v.toFixed(2)); }}>
                      {money(v)}
                    </button>
                  ))}
                </div>
                <form
                  className="bid-form"
                  onSubmit={(e) => {
                    e.preventDefault();
                    if (!tooLow && amount) submit(numeric);
                  }}
                >
                  <div className="money-input">
                    <span>$</span>
                    <input
                      className={`input ${tooLow ? 'invalid' : ''}`}
                      type="number"
                      step="0.01"
                      min={min}
                      inputMode="decimal"
                      value={amount}
                      onChange={(e) => { setTouched(true); setAmount(e.target.value); setFormError(''); }}
                      aria-label="Your bid"
                    />
                  </div>
                  <button className="btn btn-primary btn-block" disabled={busy || tooLow || !amount || leading}>
                    {busy ? 'Placing bid...' : user ? `Place bid ${amount && !tooLow ? money(numeric) : ''}` : 'Sign in to bid'}
                  </button>
                </form>
                <div className="hint">
                  {tooLow ? <span className="err">Minimum bid is {money(min)}</span> : <>Minimum next bid <strong>{money(min)}</strong> (+{money(inc)} steps)</>}
                </div>
                {formError && <div className="banner banner-bad">{formError}</div>}
                <p className="muted tiny">Bids in the final 30 seconds extend the auction by 30 seconds.</p>
              </>
            )}
          </div>

          <div className="card pad">
            <h3>Bid history <span className="muted small">({bids.length})</span></h3>
            {bids.length === 0 ? (
              <p className="muted small">No bids yet &mdash; be the first.</p>
            ) : (
              <ul className="bid-list">
                {bids.slice(0, 30).map((b, i) => (
                  <li key={b.id} className={`${b.id === newBidId ? 'slide-in' : ''} ${i === 0 ? 'top' : ''}`}>
                    <span className="avatar sm">{b.bidderName[0].toUpperCase()}</span>
                    <span className="who">
                      {b.bidderName}
                      {user?.id === b.bidderId && <em> (you)</em>}
                      <small>{timeAgo(b.createdAt)}</small>
                    </span>
                    <strong>{money(b.amount)}</strong>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </aside>
      </div>
    </div>
  );
}
