export default function ChatInput({ value, onChange, onSubmit, loading, placeholder }) {
  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      onSubmit(e);
    }
  };

  return (
    <form className="chat-input-form" onSubmit={onSubmit}>
      <textarea
        value={value}
        onChange={onChange}
        onKeyDown={handleKeyDown}
        placeholder={placeholder || 'Ask a question about your documents...'}
        rows={2}
        disabled={loading}
      />
      <button type="submit" className="btn btn-primary" disabled={loading || !value.trim()}>
        {loading ? 'Sending...' : 'Send'}
      </button>
    </form>
  );
}
