import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getDocuments } from '../api/documentApi';
import { useAuth } from '../context/AuthContext';

export default function DashboardPage() {
  const { user } = useAuth();
  const [documentCount, setDocumentCount] = useState(0);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    getDocuments()
      .then((res) => setDocumentCount(res.data.length))
      .catch(() => setDocumentCount(0))
      .finally(() => setLoading(false));
  }, []);

  const cards = [
    {
      title: 'Upload Documents',
      desc: 'Add PDF study materials to your knowledge base.',
      to: '/upload',
      cta: 'Upload PDF',
      color: 'card-accent-blue',
    },
    {
      title: 'Chat with AI',
      desc: 'Ask questions answered from your indexed documents.',
      to: '/chat',
      cta: 'Open Chat',
      color: 'card-accent-purple',
    },
  ];

  return (
    <div className="page">
      <header className="page-header">
        <div>
          <h1>Welcome back, {user?.fullName?.split(' ')[0] || 'there'}!</h1>
          <p className="page-subtitle">
            Prepare for interviews using your own documents and AI-powered RAG.
          </p>
        </div>
      </header>

      <div className="stats-grid">
        <div className="stat-card">
          <span className="stat-label">Documents</span>
          <span className="stat-value">{loading ? '—' : documentCount}</span>
        </div>
        <div className="stat-card">
          <span className="stat-label">Account</span>
          <span className="stat-value stat-value-sm">{user?.email}</span>
        </div>
        <div className="stat-card">
          <span className="stat-label">Data access</span>
          <span className="stat-value stat-value-sm">Your PDFs only</span>
        </div>
      </div>

      <div className="dashboard-cards">
        {cards.map((card) => (
          <Link key={card.to} to={card.to} className={`dashboard-card ${card.color}`}>
            <h3>{card.title}</h3>
            <p>{card.desc}</p>
            <span className="dashboard-card-cta">{card.cta} →</span>
          </Link>
        ))}
      </div>

      <div className="card steps-card">
        <h2 className="card-title">Getting started</h2>
        <ol className="steps-list">
          <li>Upload a PDF on the <Link to="/upload">Upload Documents</Link> page</li>
          <li>Click <strong>Index for RAG</strong> to store embeddings in Qdrant</li>
          <li>Go to <Link to="/chat">Chat</Link> and ask interview questions</li>
        </ol>
      </div>
    </div>
  );
}
