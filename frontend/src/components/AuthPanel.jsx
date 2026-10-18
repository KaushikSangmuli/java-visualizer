import { useState } from 'react';

const views = {
  login: { title: 'Welcome back', subtitle: 'Sign in to your Java Visualizer account.' },
  signup: { title: 'Create your account', subtitle: 'We will email you a verification code.' },
  verify: { title: 'Verify your email', subtitle: 'Enter the verification code for your account.' },
  forgot: { title: 'Reset your password', subtitle: 'Request a one-time code for your verified email.' },
  reset: { title: 'Choose a new password', subtitle: 'Enter your reset code and a new password.' },
};

export default function AuthPanel({ user, onClose, onRequest, onLogin, onLogout, standalone = false }) {
  const [view, setView] = useState(user ? 'account' : 'login');
  const [email, setEmail] = useState(user?.email ?? '');
  const [password, setPassword] = useState('');
  const [code, setCode] = useState('');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(event) {
    event.preventDefault(); setBusy(true); setError(''); setMessage('');
    try {
      if (view === 'login') {
        const result = await onRequest('/api/auth/login', { email, password });
        onLogin(result);
        onClose();
      } else if (view === 'signup') {
        const result = await onRequest('/api/auth/signup', { email, password });
        setMessage(result.message); setView('verify');
      } else if (view === 'verify') {
        const result = await onRequest('/api/auth/verify-email', { email, code });
        setMessage(result.message); setView('login'); setPassword(''); setCode('');
      } else if (view === 'forgot') {
        const result = await onRequest('/api/auth/password-reset/request', { email });
        setMessage(result.message); setView('reset');
      } else if (view === 'reset') {
        const result = await onRequest('/api/auth/password-reset/complete', { email, code, newPassword: password });
        setMessage(result.message); setView('login'); setPassword(''); setCode('');
      }
    } catch (reason) { setError(reason.message || 'The request could not be completed.'); }
    finally { setBusy(false); }
  }

  async function resendCode() {
    setError(''); setMessage(''); setBusy(true);
    try { const result = await onRequest('/api/auth/verification-code/resend', { email }); setMessage(result.message); }
    catch (reason) { setError(reason.message); }
    finally { setBusy(false); }
  }

  if (view === 'account') return <div className={`auth-backdrop ${standalone ? 'standalone' : ''}`} onMouseDown={(event) => !standalone && event.target === event.currentTarget && onClose()}>
    <section className="auth-card" role="dialog" aria-modal="true" aria-labelledby="auth-title">
      {!standalone && <button className="auth-close" onClick={onClose} aria-label="Close">×</button>}
      <span className="auth-eyebrow">ACCOUNT</span><h2 id="auth-title">You’re signed in</h2>
      <p className="auth-subtitle">{user.email}</p>
      {error && <p className="auth-error" role="alert">{error}</p>}
      <button className="auth-primary" disabled={busy} onClick={async () => { setBusy(true); setError(''); try { await onLogout(); onClose(); } catch (reason) { setError(reason.message || 'Could not sign out.'); } finally { setBusy(false); } }}>{busy ? 'Please wait…' : 'Sign out'}</button>
    </section>
  </div>;

  const config = views[view];
  return <div className={`auth-backdrop ${standalone ? 'standalone' : ''}`} onMouseDown={(event) => !standalone && event.target === event.currentTarget && onClose()}>
    <section className="auth-card" role="dialog" aria-modal="true" aria-labelledby="auth-title">
      {!standalone && <button className="auth-close" onClick={onClose} aria-label="Close">×</button>}
      <span className="auth-eyebrow">JAVA VISUALIZER</span>
      <h2 id="auth-title">{config.title}</h2><p className="auth-subtitle">{config.subtitle}</p>
      <form onSubmit={submit} className="auth-form">
        <label>Email address<input type="email" autoComplete="email" required maxLength={320} value={email} onChange={(event) => setEmail(event.target.value)} /></label>
        {['login', 'signup', 'reset'].includes(view) && <label>{view === 'reset' ? 'New password' : 'Password'}<input type="password" autoComplete={view === 'login' ? 'current-password' : 'new-password'} required minLength={view === 'login' ? 1 : 12} maxLength={72} value={password} onChange={(event) => setPassword(event.target.value)} />{view !== 'login' && <small>Use at least 12 characters.</small>}</label>}
        {['verify', 'reset'].includes(view) && <label>Verification code<input inputMode="numeric" pattern="[0-9]{4}|[0-9]{6}" autoComplete="one-time-code" required minLength={4} maxLength={6} value={code} onChange={(event) => setCode(event.target.value.replace(/\D/g, '').slice(0, 6))} /></label>}
        {error && <p className="auth-error" role="alert">{error}</p>}
        {message && <p className="auth-message" role="status">{message}</p>}
        <button className="auth-primary" disabled={busy}>{busy ? 'Please wait…' : ({ login: 'Sign in', signup: 'Create account', verify: 'Verify email', forgot: 'Send reset code', reset: 'Update password' })[view]}</button>
      </form>
      {view === 'verify' && <button className="auth-link" disabled={busy} onClick={resendCode}>Resend verification code</button>}
      {view === 'login' && <div className="auth-links"><button className="auth-link" onClick={() => { setView('forgot'); setMessage(''); setError(''); }}>Forgot password?</button><button className="auth-link" onClick={() => { setView('signup'); setMessage(''); setError(''); }}>Create account</button></div>}
      {view !== 'login' && <button className="auth-link" onClick={() => { setView('login'); setMessage(''); setError(''); }}>Back to sign in</button>}
    </section>
  </div>;
}
