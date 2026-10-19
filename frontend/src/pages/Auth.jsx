import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth, useToast } from '../state';

export default function Auth() {
  const { login, register } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const next = params.get('next') || '/';
  const [mode, setMode] = useState('login');
  const [form, setForm] = useState({ username: '', email: '', password: '' });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));

  async function submit(e, creds) {
    e?.preventDefault();
    setBusy(true);
    setError('');
    try {
      const u = creds
        ? await login(creds.username, creds.password)
        : mode === 'login'
          ? await login(form.username, form.password)
          : await register(form.username, form.email, form.password);
      toast.success(`Welcome, ${u.username}!`);
      navigate(next, { replace: true });
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="page auth-page">
      <div className="card pad auth-card">
        <div className="auth-logo">&#128296;</div>
        <h1>{mode === 'login' ? 'Welcome back' : 'Create your account'}</h1>
        <p className="muted">{mode === 'login' ? 'Sign in to place bids and list items.' : 'Join in seconds and start bidding.'}</p>

        <div className="tabs full">
          <button type="button" className={`tab ${mode === 'login' ? 'active' : ''}`} onClick={() => { setMode('login'); setError(''); }}>Sign in</button>
          <button type="button" className={`tab ${mode === 'register' ? 'active' : ''}`} onClick={() => { setMode('register'); setError(''); }}>Register</button>
        </div>

        <form onSubmit={submit} className="form">
          <label>
            {mode === 'login' ? 'Username or email' : 'Username'}
            <input className="input" value={form.username} onChange={set('username')} autoComplete="username" required minLength={mode === 'register' ? 3 : 1} maxLength={mode === 'register' ? 30 : 120} />
          </label>
          {mode === 'register' && (
            <label>
              Email
              <input className="input" type="email" value={form.email} onChange={set('email')} autoComplete="email" required />
            </label>
          )}
          <label>
            Password
            <input className="input" type="password" value={form.password} onChange={set('password')} autoComplete={mode === 'login' ? 'current-password' : 'new-password'} required minLength={mode === 'register' ? 6 : 1} />
          </label>
          {error && <div className="banner banner-bad">{error}</div>}
          <button className="btn btn-primary btn-block" disabled={busy}>
            {busy ? 'Please wait...' : mode === 'login' ? 'Sign in' : 'Create account'}
          </button>
        </form>

        <div className="divider"><span>or try it instantly</span></div>
        <button className="btn btn-secondary btn-block" disabled={busy} onClick={() => submit(null, { username: 'demo', password: 'demo1234' })}>
          &#9889; Continue as demo user
        </button>
      </div>
    </div>
  );
}
