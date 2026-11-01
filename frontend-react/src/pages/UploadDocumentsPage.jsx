import { useCallback, useEffect, useState } from 'react';
import DocumentList from '../components/documents/DocumentList';
import DocumentUploader from '../components/documents/DocumentUploader';
import { getDocuments } from '../api/documentApi';

export default function UploadDocumentsPage() {
  const [documents, setDocuments] = useState([]);
  const [loading, setLoading] = useState(true);

  const loadDocuments = useCallback(async () => {
    setLoading(true);
    try {
      const { data } = await getDocuments();
      setDocuments(data);
    } catch {
      setDocuments([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadDocuments();
  }, [loadDocuments]);

  return (
    <div className="page">
      <header className="page-header">
        <h1>Upload Documents</h1>
        <p className="page-subtitle">
          Upload PDF files and index them so the AI can answer questions from your content.
        </p>
      </header>

      <div className="upload-page-grid">
        <DocumentUploader onUploaded={loadDocuments} />
        <DocumentList
          documents={documents}
          loading={loading}
          onRefresh={loadDocuments}
        />
      </div>
    </div>
  );
}
