import { useState } from 'react';
import { deleteDocument, indexDocument } from '../../api/documentApi';
import { generateQuestionsFromDocument, generateQuestionsFromLatest } from '../../api/ragApi';

export default function DocumentList({ documents, loading, onRefresh }) {
  const [indexingId, setIndexingId] = useState(null);
  const [deletingId, setDeletingId] = useState(null);
  const [questionsLoadingId, setQuestionsLoadingId] = useState(null);
  const [generatedQuestions, setGeneratedQuestions] = useState('');
  const [questionsForFile, setQuestionsForFile] = useState('');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  const handleIndex = async (documentId) => {
    setIndexingId(documentId);
    setError('');
    setMessage('Indexing started. Large PDFs can take 10–20+ minutes — keep this tab open.');
    try {
      const { data } = await indexDocument(documentId);
      const chunkInfo =
        data.chunksIndexed != null ? ` (${data.chunksIndexed} chunks indexed)` : '';
      setMessage((data.message || `Document #${documentId} indexed for RAG`) + chunkInfo);
      onRefresh?.();
    } catch (err) {
      if (err.code === 'ECONNABORTED' || err.message?.includes('timeout')) {
        setError(
          'The browser gave up waiting, but indexing may still be running on the server. ' +
            'Wait a few minutes, refresh the page, then try Chat. Or use a smaller PDF.'
        );
      } else {
        setError(err.response?.data?.message || 'Indexing failed');
      }
    } finally {
      setIndexingId(null);
    }
  };

  const handleGenerateQuestions = async (doc) => {
    setQuestionsLoadingId(doc?.id ?? 'latest');
    setError('');
    setGeneratedQuestions('');
    const fileName = doc?.originalFileName ?? 'Latest upload';
    setQuestionsForFile(fileName);
    try {
      const { data } = doc
        ? await generateQuestionsFromDocument(doc.id)
        : await generateQuestionsFromLatest();
      setGeneratedQuestions(data.answer);
      setMessage(`Generated questions from "${fileName}".`);
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to generate questions. Index the document first.');
    } finally {
      setQuestionsLoadingId(null);
    }
  };

  const handleDelete = async (doc) => {
    const confirmed = window.confirm(
      `Delete "${doc.originalFileName}"?\n\nThis removes the PDF file and any indexed embeddings. This cannot be undone.`
    );
    if (!confirmed) return;

    setDeletingId(doc.id);
    setError('');
    setMessage('');
    try {
      await deleteDocument(doc.id);
      setMessage(`"${doc.originalFileName}" was deleted.`);
      await onRefresh?.();
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to delete document');
    } finally {
      setDeletingId(null);
    }
  };

  if (loading) {
    return (
      <div className="card">
        <p className="loading-text">Loading documents...</p>
      </div>
    );
  }

  return (
    <div className="card">
      <div className="card-header-row">
        <h2 className="card-title">Your Documents</h2>
        <div className="document-actions">
          <button
            type="button"
            className="btn btn-secondary btn-sm"
            onClick={() => handleGenerateQuestions(null)}
            disabled={documents.length === 0 || questionsLoadingId !== null}
          >
            {questionsLoadingId === 'latest' ? 'Generating...' : 'Questions from latest'}
          </button>
          <button type="button" className="btn btn-secondary btn-sm" onClick={onRefresh}>
            Refresh
          </button>
        </div>
      </div>

      {message && <div className="success-banner">{message}</div>}
      {error && <div className="error-banner">{error}</div>}

      {generatedQuestions && (
        <div className="generated-questions">
          <h3>Interview questions — {questionsForFile}</h3>
          <pre className="questions-body">{generatedQuestions}</pre>
        </div>
      )}

      {documents.length === 0 ? (
        <p className="empty-state">No documents yet. Upload a PDF above to get started.</p>
      ) : (
        <ul className="document-list">
          {documents.map((doc) => (
            <li key={doc.id} className="document-item">
              <div className="document-icon">PDF</div>
              <div className="document-info">
                <strong>{doc.originalFileName}</strong>
                <span className="doc-meta">
                  {(doc.fileSize / 1024).toFixed(1)} KB ·{' '}
                  {new Date(doc.uploadedAt).toLocaleString()}
                </span>
                {doc.description && <p className="doc-desc">{doc.description}</p>}
              </div>
              <div className="document-actions">
                <button
                  type="button"
                  className="btn btn-secondary btn-sm"
                  onClick={() => handleIndex(doc.id)}
                  disabled={indexingId === doc.id || deletingId === doc.id || questionsLoadingId}
                >
                  {indexingId === doc.id ? 'Indexing...' : 'Index for RAG'}
                </button>
                <button
                  type="button"
                  className="btn btn-secondary btn-sm"
                  onClick={() => handleGenerateQuestions(doc)}
                  disabled={questionsLoadingId === doc.id || indexingId === doc.id || deletingId === doc.id}
                >
                  {questionsLoadingId === doc.id ? 'Generating...' : 'Get questions'}
                </button>
                <button
                  type="button"
                  className="btn btn-danger btn-sm"
                  onClick={() => handleDelete(doc)}
                  disabled={deletingId === doc.id || indexingId === doc.id}
                >
                  {deletingId === doc.id ? 'Deleting...' : 'Delete'}
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
