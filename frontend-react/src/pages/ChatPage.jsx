import { useEffect, useRef, useState } from 'react';
import { askQuestion } from '../api/chatApi';
import { getDocuments } from '../api/documentApi';
import ChatInput from '../components/chat/ChatInput';
import ChatMessage from '../components/chat/ChatMessage';

const WELCOME_NO_DOCS =
  'Hello! Ask me interview preparation questions — no PDF needed. ' +
  'Upload and index a PDF anytime to get answers from your own documents.';

const WELCOME_WITH_DOCS =
  'Hello! Choose **General interview help** or **My PDF (RAG)** below. ' +
  'For document answers, index your PDF on the Upload page first.';

const HISTORY_TURNS = 4;

const WELCOME_MESSAGE = {
  id: 'welcome',
  role: 'assistant',
  content: WELCOME_NO_DOCS,
  timestamp: new Date().toISOString(),
};

export default function ChatPage() {
  const [messages, setMessages] = useState([WELCOME_MESSAGE]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [documents, setDocuments] = useState([]);
  const [selectedDocumentId, setSelectedDocumentId] = useState('');
  const [useDocuments, setUseDocuments] = useState(false);
  const messagesEndRef = useRef(null);

  useEffect(() => {
    getDocuments()
      .then(({ data }) => {
        setDocuments(data);
        if (data.length > 0) {
          setSelectedDocumentId(String(data[0].id));
          setMessages([
            {
              id: 'welcome',
              role: 'assistant',
              content: WELCOME_WITH_DOCS,
              timestamp: new Date().toISOString(),
            },
          ]);
        }
      })
      .catch(() => setDocuments([]));
  }, []);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, loading]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    const question = input.trim();
    if (!question || loading) return;

    const userMessage = {
      id: `user-${Date.now()}`,
      role: 'user',
      content: question,
      historyContent: question,
      timestamp: new Date().toISOString(),
    };

    const history = messages
      .filter((msg) => msg.historyContent)
      .slice(-HISTORY_TURNS)
      .map((msg) => ({ role: msg.role, content: msg.historyContent }));

    setMessages((prev) => [...prev, userMessage]);
    setInput('');
    setLoading(true);
    setError('');

    try {
      const documentId =
        useDocuments && selectedDocumentId ? Number(selectedDocumentId) : null;
      const ragMode = useDocuments && documents.length > 0;
      const { data } = await askQuestion(question, documentId, ragMode ? true : false, history);
      const sourceNote = data.ragFallback
        ? `\n\n— Not in PDF (${data.documentFileName}); answered using general AI knowledge`
        : data.generalChat
          ? '\n\n— General interview help (not from a PDF)'
          : data.documentFileName
            ? `\n\n— Answered from: ${data.documentFileName}`
            : '';
      const assistantMessage = {
        id: `assistant-${Date.now()}`,
        role: 'assistant',
        content: data.answer + sourceNote,
        historyContent: data.answer,
        sources: data.sources || [],
        timestamp: new Date().toISOString(),
      };
      setMessages((prev) => [...prev, assistantMessage]);
    } catch (err) {
      const errMsg =
        err.response?.data?.message ||
        (err.code === 'ERR_NETWORK'
          ? 'Cannot reach the backend. Restart Spring Boot on port 8080.'
          : 'Failed to get a response. Check backend and Ollama.');
      setError(errMsg);
      setMessages((prev) => [
        ...prev,
        {
          id: `error-${Date.now()}`,
          role: 'assistant',
          content: `Sorry, something went wrong: ${errMsg}`,
          timestamp: new Date().toISOString(),
        },
      ]);
    } finally {
      setLoading(false);
    }
  };

  const handleClear = () => {
    setMessages([WELCOME_MESSAGE]);
    setError('');
  };

  return (
    <div className="page chat-page">
      <header className="page-header chat-header">
        <div>
          <h1>Chat</h1>
          <p className="page-subtitle">
            {documents.length === 0
              ? 'General interview help — upload a PDF anytime for document Q&A.'
              : 'Switch between general interview tips and answers from your indexed PDFs.'}
          </p>
        </div>
        <button type="button" className="btn btn-secondary btn-sm" onClick={handleClear}>
          Clear chat
        </button>
      </header>

      {error && <div className="error-banner chat-error">{error}</div>}

      <label className="chat-doc-picker">
        Answer mode
        <select
          value={useDocuments && documents.length > 0 ? 'rag' : 'general'}
          onChange={(e) => setUseDocuments(e.target.value === 'rag')}
          disabled={loading || documents.length === 0}
        >
          <option value="general">General interview help</option>
          {documents.length > 0 && <option value="rag">My PDF (RAG)</option>}
        </select>
      </label>

      {useDocuments && documents.length > 0 && (
        <p className="chat-mode-hint">
          Answers use your PDF first. If the topic is not in the PDF, the AI answers from general knowledge.
        </p>
      )}

      {useDocuments && documents.length > 0 && (
        <label className="chat-doc-picker">
          PDF document
          <select
            value={selectedDocumentId}
            onChange={(e) => setSelectedDocumentId(e.target.value)}
            disabled={loading}
          >
            {documents.map((doc) => (
              <option key={doc.id} value={doc.id}>
                {doc.originalFileName}
              </option>
            ))}
          </select>
        </label>
      )}

      <div className="chat-container">
        <div className="chat-messages">
          {messages.map((msg) => (
            <ChatMessage
              key={msg.id}
              role={msg.role}
              content={msg.content}
              sources={msg.sources}
              timestamp={msg.timestamp}
            />
          ))}
          {loading && (
            <div className="chat-typing">
              <span className="typing-dot" />
              <span className="typing-dot" />
              <span className="typing-dot" />
              <span>Assistant is thinking...</span>
            </div>
          )}
          <div ref={messagesEndRef} />
        </div>

        <ChatInput
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onSubmit={handleSubmit}
          loading={loading}
        />
      </div>
    </div>
  );
}
