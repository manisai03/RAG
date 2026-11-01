import { Link } from 'react-router-dom';

export default function AuthLayout({ children, title, subtitle }) {
  return (
    <div className="auth-layout">
      <div className="auth-layout-left">
        <div className="auth-brand">
          <span className="auth-logo">AI</span>
          <h1>Interview Preparation Assistant</h1>
          <p>
            Upload study materials, index them with RAG, and chat with an AI trained on
            your documents.
          </p>
          <ul className="auth-features">
            <li>PDF upload &amp; indexing</li>
            <li>Vector search with Qdrant</li>
            <li>Ollama-powered answers</li>
          </ul>
        </div>
      </div>
      <div className="auth-layout-right">
        <div className="auth-card">
          <h2>{title}</h2>
          {subtitle && <p className="auth-card-subtitle">{subtitle}</p>}
          {children}
        </div>
        <p className="auth-back">
          <Link to="/">← Back to home</Link>
        </p>
      </div>
    </div>
  );
}
