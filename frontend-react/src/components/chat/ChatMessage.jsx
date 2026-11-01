export default function ChatMessage({ role, content, sources = [], timestamp }) {
  const isUser = role === 'user';

  return (
    <div className={`chat-message ${isUser ? 'chat-message-user' : 'chat-message-assistant'}`}>
      <div className="chat-message-avatar">{isUser ? 'You' : 'AI'}</div>
      <div className="chat-message-body">
        <p className="chat-message-role">{isUser ? 'You' : 'Assistant'}</p>
        <div className="chat-message-content">{content}</div>
        {sources.length > 0 && (
          <details className="chat-sources">
            <summary>Sources ({sources.length})</summary>
            <ul>
              {sources.map((source) => (
                <li key={source.sourceNumber}>
                  <span className="chat-source-label">
                    [Source {source.sourceNumber}]
                    {source.pageNumber != null && ` · page ${source.pageNumber}`}
                  </span>
                  <span className="chat-source-snippet">{source.snippet}</span>
                </li>
              ))}
            </ul>
          </details>
        )}
        {timestamp && (
          <span className="chat-message-time">
            {new Date(timestamp).toLocaleTimeString()}
          </span>
        )}
      </div>
    </div>
  );
}
