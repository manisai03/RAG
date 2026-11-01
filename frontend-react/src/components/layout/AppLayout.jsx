import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';

const navItems = [
  { to: '/dashboard', label: 'Dashboard', icon: '◉' },
  { to: '/upload', label: 'Upload Documents', icon: '↑' },
  { to: '/chat', label: 'Chat', icon: '💬' },
];

export default function AppLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <div className="app-layout">
      <aside className="sidebar">
        <div className="sidebar-header">
          <span className="sidebar-logo">AI</span>
          <div>
            <p className="sidebar-title">Interview Assistant</p>
            <p className="sidebar-subtitle">RAG powered</p>
          </div>
        </div>

        <nav className="sidebar-nav">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                `sidebar-link ${isActive ? 'sidebar-link-active' : ''}`
              }
            >
              <span className="sidebar-icon">{item.icon}</span>
              {item.label}
            </NavLink>
          ))}
        </nav>

        <div className="sidebar-footer">
          <div className="user-card">
            <div className="user-avatar">
              {(user?.fullName || user?.email || 'U').charAt(0).toUpperCase()}
            </div>
            <div>
              <p className="user-name">{user?.fullName || 'User'}</p>
              <p className="user-email">{user?.email}</p>
            </div>
          </div>
          <button type="button" className="btn btn-ghost btn-full" onClick={handleLogout}>
            Logout
          </button>
        </div>
      </aside>

      <div className="app-main">
        <Outlet />
      </div>
    </div>
  );
}
