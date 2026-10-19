import { useEffect, useState } from 'react';
import { api } from '../api';
import { SkeletonGrid } from '../components';
import { useAuth, useToast } from '../state';

export default function Admin() {
  const { user } = useAuth();
  const toast = useToast();
  const [users, setUsers] = useState(null);

  useEffect(() => {
    api.adminUsers().then((r) => setUsers(r.content)).catch((e) => toast.error('Could not load users', e.message));
  }, [toast]);

  async function toggle(u) {
    try {
      const updated = await api.setUserEnabled(u.id, !u.enabled);
      setUsers((cur) => cur.map((x) => (x.id === u.id ? updated : x)));
      toast.success(updated.enabled ? 'User enabled' : 'User disabled', updated.username);
    } catch (e) {
      toast.error('Action failed', e.message);
    }
  }

  return (
    <div className="page">
      <h1>User management</h1>
      <p className="muted">Disabled users cannot sign in or bid; their existing token stops working immediately.</p>
      {users === null ? (
        <SkeletonGrid n={3} />
      ) : (
        <div className="table-wrap">
          <table className="table">
            <thead><tr><th>ID</th><th>Username</th><th>Email</th><th>Role</th><th>Joined</th><th>Status</th><th /></tr></thead>
            <tbody>
              {users.map((u) => (
                <tr key={u.id}>
                  <td>{u.id}</td>
                  <td>{u.username}</td>
                  <td>{u.email}</td>
                  <td><span className={`badge ${u.role === 'ADMIN' ? 'badge-warn' : ''}`}>{u.role}</span></td>
                  <td>{new Date(u.createdAt).toLocaleDateString()}</td>
                  <td><span className={`badge ${u.enabled ? 'badge-good' : 'badge-bad'}`}>{u.enabled ? 'active' : 'disabled'}</span></td>
                  <td>
                    {u.role !== 'ADMIN' && u.id !== user.id && (
                      <button className="btn btn-ghost btn-sm" onClick={() => toggle(u)}>{u.enabled ? 'Disable' : 'Enable'}</button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
