import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, NavLink, useNavigate } from 'react-router-dom';
import { onConnectionChange } from './live';
import { useAuth } from './state';
import { formatRemaining, money, PLACEHOLDER, remaining, urgency, useNow } from './utils';

export function Navbar() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [live, setLive] = useState(false);
  useEffect(() => onConnectionChange(setLive), []);

  return (
    <header className="nav">
      <div className="nav-inner">
        <Link to="/" className="logo">
          <span className="logo-mark">&#128296;</span> Gavel
        </Link>
        <nav className="nav-links">
          <NavLink to="/" end>Auctions</NavLink>
          {user && <NavLink to="/sell">Sell</NavLink>}
          {user && <NavLink to="/dashboard">Dashboard</NavLink>}
          {user?.role === 'ADMIN' && <NavLink to="/admin">Admin</NavLink>}
        </nav>
        <div className="nav-right">
          <span className={`live-pill ${live ? 'on' : 'off'}`} title={live ? 'Real-time updates connected' : 'Reconnecting...'}>
            <i /> {live ? 'Live' : 'Offline'}
          </span>
          {user ? (
            <div className="user-chip">
              <span className="avatar">{user.username[0].toUpperCase()}</span>
              <span className="uname">{user.username}</span>
              <button
                className="btn btn-ghost btn-sm"
                onClick={() => {
                  logout();
                  navigate('/');
                }}
              >
                Log out
              </button>
            </div>
          ) : (
            <Link to="/auth" className="btn btn-primary btn-sm">Sign in</Link>
          )}
        </div>
      </div>
    </header>
  );
}

export function Countdown({ endTime, status, big = false }) {
  const now = useNow();
  if (status === 'CANCELLED') return <span className="time-pill ended">Cancelled</span>;
  const ms = remaining(endTime, now);
  const level = status === 'CLOSED' ? 'ended' : urgency(ms);
  if (level === 'ended') return <span className={`time-pill ended ${big ? 'big' : ''}`}>Ended</span>;
  return (
    <span className={`time-pill ${level} ${big ? 'big' : ''}`}>
      <span className="dot" /> {formatRemaining(ms)}
    </span>
  );
}

/** Shows a price and plays a flash animation every time it changes. */
export function Price({ value, className = '' }) {
  const prev = useRef(value);
  const [flashKey, setFlashKey] = useState(0);
  useEffect(() => {
    if (prev.current !== value) {
      prev.current = value;
      setFlashKey((k) => k + 1);
    }
  }, [value]);
  return (
    <span key={flashKey} className={`price ${className} ${flashKey ? 'flash' : ''}`}>
      {money(value)}
    </span>
  );
}

export function AuctionCard({ a, watched, onWatch, fresh }) {
  const { user } = useAuth();
  const leading = user && a.highestBidderId === user.id && a.status === 'ACTIVE';
  return (
    <article className={`card auction-card ${fresh ? 'pop-in' : ''}`}>
      <Link to={`/auction/${a.id}`} className="card-img">
        <img src={a.imageUrl || PLACEHOLDER} alt={a.title} loading="lazy" onError={(e) => (e.currentTarget.src = PLACEHOLDER)} />
        <span className="chip chip-cat">{a.category}</span>
        <Countdown endTime={a.endTime} status={a.status} />
      </Link>
      <button
        className={`watch ${watched ? 'on' : ''}`}
        onClick={() => onWatch(a.id)}
        aria-label={watched ? 'Remove from watchlist' : 'Add to watchlist'}
        title={watched ? 'Remove from watchlist' : 'Add to watchlist'}
      >
        {watched ? '♥' : '♡'}
      </button>
      <div className="card-body">
        <Link to={`/auction/${a.id}`} className="card-title">{a.title}</Link>
        <div className="card-row">
          <div>
            <div className="label">{a.bidCount ? 'Current bid' : 'Starting bid'}</div>
            <Price value={a.currentPrice} className="price-lg" />
          </div>
          <div className="right">
            <div className="label">{a.bidCount} {a.bidCount === 1 ? 'bid' : 'bids'}</div>
            {leading ? (
              <span className="badge badge-good">You&rsquo;re winning</span>
            ) : a.highestBidderName ? (
              <span className="muted small">by {a.highestBidderName}</span>
            ) : (
              <span className="muted small">no bids yet</span>
            )}
          </div>
        </div>
      </div>
    </article>
  );
}

export function SkeletonGrid({ n = 8 }) {
  return (
    <div className="grid">
      {Array.from({ length: n }).map((_, i) => (
        <div key={i} className="card skeleton">
          <div className="sk-img" />
          <div className="sk-line" />
          <div className="sk-line short" />
        </div>
      ))}
    </div>
  );
}

/** Step-line chart of the price after each bid. */
export function PriceChart({ auction, bids }) {
  const points = useMemo(() => {
    const asc = [...bids].sort((x, y) => x.id - y.id);
    return [{ v: Number(auction.startingPrice), label: 'Start' }, ...asc.map((b) => ({ v: Number(b.amount), label: b.bidderName }))];
  }, [bids, auction.startingPrice]);

  const W = 600;
  const H = 170;
  const pad = { l: 8, r: 8, t: 14, b: 14 };
  const min = Math.min(...points.map((p) => p.v));
  const max = Math.max(...points.map((p) => p.v));
  const span = max - min || 1;
  const x = (i) => pad.l + (points.length === 1 ? (W - pad.l - pad.r) / 2 : (i / (points.length - 1)) * (W - pad.l - pad.r));
  const y = (v) => pad.t + (1 - (v - min) / span) * (H - pad.t - pad.b);

  let d = `M ${x(0)} ${y(points[0].v)}`;
  for (let i = 1; i < points.length; i++) d += ` H ${x(i)} V ${y(points[i].v)}`;
  const last = points[points.length - 1];
  const area = `${d} V ${H} H ${x(0)} Z`;
  const [hover, setHover] = useState(null);

  return (
    <div className="chart">
      <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" onMouseLeave={() => setHover(null)}>
        <defs>
          <linearGradient id="g" x1="0" x2="0" y1="0" y2="1">
            <stop offset="0%" stopColor="var(--accent)" stopOpacity="0.45" />
            <stop offset="100%" stopColor="var(--accent)" stopOpacity="0" />
          </linearGradient>
        </defs>
        <path d={area} fill="url(#g)" />
        <path d={d} fill="none" stroke="var(--accent)" strokeWidth="2.5" vectorEffect="non-scaling-stroke" />
        {points.map((p, i) => (
          <circle
            key={i}
            cx={x(i)}
            cy={y(p.v)}
            r={i === points.length - 1 ? 5 : 3.5}
            className={i === points.length - 1 ? 'pulse-dot' : ''}
            fill="var(--accent2)"
            onMouseEnter={() => setHover(i)}
          />
        ))}
      </svg>
      <div className="chart-meta">
        {hover !== null ? (
          <span>{points[hover].label}: <strong>{money(points[hover].v)}</strong></span>
        ) : (
          <span>
            {points.length - 1} {points.length === 2 ? 'bid' : 'bids'} &middot; latest <strong>{money(last.v)}</strong>
          </span>
        )}
      </div>
    </div>
  );
}

export function Confetti() {
  const pieces = useMemo(
    () =>
      Array.from({ length: 60 }).map((_, i) => ({
        left: Math.random() * 100,
        delay: Math.random() * 0.8,
        dur: 2.2 + Math.random() * 2,
        color: ['#7c5cff', '#00d4ff', '#22c55e', '#f59e0b', '#ef4444', '#ec4899'][i % 6],
        size: 6 + Math.random() * 8,
        rot: Math.random() * 360,
      })),
    [],
  );
  return (
    <div className="confetti" aria-hidden="true">
      {pieces.map((p, i) => (
        <i
          key={i}
          style={{
            left: `${p.left}%`,
            width: p.size,
            height: p.size * 0.5,
            background: p.color,
            animationDelay: `${p.delay}s`,
            animationDuration: `${p.dur}s`,
            transform: `rotate(${p.rot}deg)`,
          }}
        />
      ))}
    </div>
  );
}

export function Empty({ icon = '\u{1F50D}', title, children }) {
  return (
    <div className="empty">
      <div className="empty-icon">{icon}</div>
      <h3>{title}</h3>
      <p className="muted">{children}</p>
    </div>
  );
}

export function RequireAuth({ children, admin = false }) {
  const { user, loading } = useAuth();
  const navigate = useNavigate();
  useEffect(() => {
    if (!loading && !user) navigate(`/auth?next=${encodeURIComponent(window.location.pathname)}`, { replace: true });
  }, [loading, user, navigate]);
  if (loading || !user) return <div className="page"><SkeletonGrid n={4} /></div>;
  if (admin && user.role !== 'ADMIN') return <div className="page"><Empty icon={'\u{1F512}'} title="Admins only">You do not have access to this page.</Empty></div>;
  return children;
}
