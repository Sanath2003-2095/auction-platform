import React, { useEffect } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter, Route, Routes } from 'react-router-dom';
import { Empty, Navbar, RequireAuth } from './components';
import { subscribe } from './live';
import Admin from './pages/Admin';
import AuctionDetail from './pages/AuctionDetail';
import Auth from './pages/Auth';
import Create from './pages/Create';
import Dashboard from './pages/Dashboard';
import Home from './pages/Home';
import { AuthProvider, ToastProvider, useAuth, useToast } from './state';
import { money } from './utils';
import './styles.css';

/** Global, personal notifications: outbid warnings and win announcements on any page. */
function LiveNotifier() {
  const { user } = useAuth();
  const toast = useToast();
  useEffect(() => {
    if (!user) return undefined;
    return subscribe('/topic/auctions', (ev) => {
      const a = ev.auction;
      if (ev.type === 'BID' && ev.previousBidderId === user.id && a.highestBidderId !== user.id) {
        toast.warn('You were outbid!', `${a.title} is now ${money(a.currentPrice)}`);
      }
      if (ev.type === 'CLOSED' && a.highestBidderId === user.id) {
        toast.success('You won!', `${a.title} for ${money(a.currentPrice)}`);
      }
    });
  }, [user, toast]);
  return null;
}

function App() {
  return (
    <>
      <Navbar />
      <LiveNotifier />
      <main>
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/auction/:id" element={<AuctionDetail />} />
          <Route path="/auth" element={<Auth />} />
          <Route path="/sell" element={<RequireAuth><Create /></RequireAuth>} />
          <Route path="/dashboard" element={<RequireAuth><Dashboard /></RequireAuth>} />
          <Route path="/admin" element={<RequireAuth admin><Admin /></RequireAuth>} />
          <Route path="*" element={<div className="page"><Empty icon={'\u{1F9ED}'} title="Page not found">That page does not exist.</Empty></div>} />
        </Routes>
      </main>
      <footer className="footer">Gavel &middot; real-time auctions &middot; built with Spring Boot, WebSocket &amp; React</footer>
    </>
  );
}

createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <BrowserRouter>
      <ToastProvider>
        <AuthProvider>
          <App />
        </AuthProvider>
      </ToastProvider>
    </BrowserRouter>
  </React.StrictMode>,
);
