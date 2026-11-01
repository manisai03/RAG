import { useState } from 'react';
import { uploadDocument } from '../../api/documentApi';

export default function DocumentUploader({ onUploaded }) {
  const [file, setFile] = useState(null);
  const [description, setDescription] = useState('');
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!file) return;

    setUploading(true);
    setError('');
    setSuccess('');

    try {
      await uploadDocument(file, description);
      setFile(null);
      setDescription('');
      setSuccess('Document uploaded successfully!');
      e.target.reset();
      onUploaded?.();
    } catch (err) {
      if (err.response?.status === 401) {
        setError('Session expired. Please log in again.');
      } else if (err.response?.status === 413) {
        setError('File is too large. Maximum size is 20 MB.');
      } else {
        setError(err.response?.data?.message || 'Upload failed. Use a PDF under 20 MB.');
      }
    } finally {
      setUploading(false);
    }
  };

  return (
    <div className="card">
      <h2 className="card-title">Upload PDF</h2>
      <p className="card-desc">Only PDF files are supported (max 20 MB).</p>

      <form className="upload-form" onSubmit={handleSubmit}>
        <div
          className={`dropzone ${file ? 'dropzone-active' : ''}`}
          onDragOver={(e) => e.preventDefault()}
          onDrop={(e) => {
            e.preventDefault();
            const dropped = e.dataTransfer.files?.[0];
            if (dropped && (dropped.type === 'application/pdf' || dropped.name?.toLowerCase().endsWith('.pdf'))) {
              setFile(dropped);
            } else {
              setError('Only PDF files are supported.');
            }
          }}
        >
          <input
            type="file"
            id="pdf-upload"
            accept="application/pdf,.pdf"
            onChange={(e) => setFile(e.target.files?.[0] || null)}
            className="dropzone-input"
          />
          <label htmlFor="pdf-upload" className="dropzone-label">
            {file ? (
              <>
                <strong>{file.name}</strong>
                <span>{(file.size / 1024).toFixed(1)} KB</span>
              </>
            ) : (
              <>
                <span className="dropzone-icon">↑</span>
                <span>Drag &amp; drop a PDF or click to browse</span>
              </>
            )}
          </label>
        </div>

        <label>
          Description (optional)
          <input
            type="text"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="e.g. Java interview notes"
          />
        </label>

        <button type="submit" className="btn btn-primary" disabled={!file || uploading}>
          {uploading ? 'Uploading...' : 'Upload Document'}
        </button>
      </form>

      {success && <div className="success-banner">{success}</div>}
      {error && <div className="error-banner">{error}</div>}
    </div>
  );
}
