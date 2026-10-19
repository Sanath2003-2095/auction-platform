import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api';
import { Countdown, Price } from '../components';
import { useAuth, useToast } from '../state';
import { PLACEHOLDER } from '../utils';

const DURATIONS = [
  { label: '5 min', v: 5 },
  { label: '1 hour', v: 60 },
  { label: '6 hours', v: 360 },
  { label: '1 day', v: 1440 },
  { label: '3 days', v: 4320 },
  { label: '7 days', v: 10080 },
];
const CATEGORIES = ['Electronics', 'Furniture', 'Collectibles', 'Watches', 'Art', 'Home', 'Music', 'Antiques', 'Fashion', 'Other'];

export default function Create() {
  const { user } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  const [f, setF] = useState({
    title: '',
    description: '',
    imageUrl: '',
    category: 'Electronics',
    startingPrice: '10',
    minIncrement: '1',
    durationMinutes: 60,
  });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});

  const set = (k) => (e) => setF((cur) => ({ ...cur, [k]: e.target.value }));

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError('');
    setFieldErrors({});
    try {
      const created = await api.createAuction({
        ...f,
        startingPrice: Number(f.startingPrice),
        minIncrement: Number(f.minIncrement),
        durationMinutes: Number(f.durationMinutes),
        imageUrl: f.imageUrl.trim() || null,
      });
      toast.success('Auction is live!', created.title);
      navigate(`/auction/${created.id}`);
    } catch (err) {
      setError(err.message);
      setFieldErrors(err.errors || {});
    } finally {
      setBusy(false);
    }
  }

  const preview = {
    id: 0,
    ...f,
    imageUrl: f.imageUrl.trim(),
    currentPrice: Number(f.startingPrice) || 0,
    bidCount: 0,
    status: 'ACTIVE',
    endTime: new Date(Date.now() + Number(f.durationMinutes) * 60000).toISOString(),
  };

  return (
    <div className="page create">
      <h1>List an item</h1>
      <p className="muted">Your auction goes live the moment you publish it. Selling as <strong>{user.username}</strong>.</p>
      <div className="create-grid">
        <form className="card pad form" onSubmit={submit}>
          <Field label="Title" error={fieldErrors.title}>
            <input className="input" value={f.title} onChange={set('title')} maxLength={150} required placeholder="e.g. Vintage film camera" />
          </Field>
          <Field label="Description" error={fieldErrors.description}>
            <textarea className="input" rows={5} value={f.description} onChange={set('description')} maxLength={2000} required placeholder="Condition, history, what's included..." />
            <small className="muted">{f.description.length}/2000</small>
          </Field>
          <Field label="Image URL (optional)" error={fieldErrors.imageUrl}>
            <input className="input" value={f.imageUrl} onChange={set('imageUrl')} placeholder="https://..." maxLength={500} />
          </Field>
          <div className="row2">
            <Field label="Category" error={fieldErrors.category}>
              <select className="input" value={f.category} onChange={set('category')}>
                {CATEGORIES.map((c) => <option key={c}>{c}</option>)}
              </select>
            </Field>
            <Field label="Starting price ($)" error={fieldErrors.startingPrice}>
              <input className="input" type="number" min="0.01" step="0.01" value={f.startingPrice} onChange={set('startingPrice')} required />
            </Field>
            <Field label="Min. increment ($)" error={fieldErrors.minIncrement}>
              <input className="input" type="number" min="0.01" step="0.01" value={f.minIncrement} onChange={set('minIncrement')} required />
            </Field>
          </div>
          <Field label="Duration" error={fieldErrors.durationMinutes}>
            <div className="chips">
              {DURATIONS.map((d) => (
                <button type="button" key={d.v} className={`chip-btn ${Number(f.durationMinutes) === d.v ? 'active' : ''}`} onClick={() => setF((c) => ({ ...c, durationMinutes: d.v }))}>
                  {d.label}
                </button>
              ))}
            </div>
          </Field>
          {error && <div className="banner banner-bad">{error}</div>}
          <button className="btn btn-primary" disabled={busy}>{busy ? 'Publishing...' : 'Publish auction'}</button>
        </form>

        <div className="preview">
          <div className="label">Live preview</div>
          <article className="card auction-card">
            <div className="card-img">
              <img src={preview.imageUrl || PLACEHOLDER} alt="" onError={(e) => (e.currentTarget.src = PLACEHOLDER)} />
              <span className="chip chip-cat">{preview.category}</span>
              <Countdown endTime={preview.endTime} status="ACTIVE" />
            </div>
            <div className="card-body">
              <div className="card-title">{preview.title || 'Your title here'}</div>
              <div className="card-row">
                <div>
                  <div className="label">Starting bid</div>
                  <Price value={preview.currentPrice} className="price-lg" />
                </div>
                <div className="right"><span className="muted small">no bids yet</span></div>
              </div>
            </div>
          </article>
        </div>
      </div>
    </div>
  );
}

function Field({ label, error, children }) {
  return (
    <label className="field">
      <span>{label}</span>
      {children}
      {error && <span className="err">{error}</span>}
    </label>
  );
}
