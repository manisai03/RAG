# AI Interview Preparation Assistant — Frontend

React + Vite + Axios frontend for the Spring Boot RAG backend.

## Pages

| Route | Page |
|-------|------|
| `/login` | Login |
| `/register` | Register |
| `/dashboard` | Dashboard (stats & quick links) |
| `/upload` | Upload Documents (PDF + index for RAG) |
| `/chat` | Chat Interface (RAG Q&A) |

## Setup

```bash
cd frontend-react
npm install
```

Copy environment file:

```bash
cp .env.example .env
```

## Run

```bash
npm run dev
```

Open [http://localhost:5173](http://localhost:5173)

**Backend** must run at `http://localhost:8080` (context path `/api`).

## Build for production

```bash
npm run build
npm run preview
```

## Project structure

```
src/
├── api/              # Axios client & API modules
├── components/
│   ├── chat/         # ChatMessage, ChatInput
│   ├── documents/    # DocumentUploader, DocumentList
│   └── layout/       # AppLayout, AuthLayout, ProtectedRoute
├── context/          # AuthContext (JWT)
├── pages/            # Login, Register, Dashboard, Upload, Chat
└── routes/           # React Router
```

## API (Axios)

| Action | Method | Endpoint |
|--------|--------|----------|
| Register | POST | `/auth/register` |
| Login | POST | `/auth/login` |
| Chat | POST | `/chat/ask` |
| Upload PDF | POST | `/documents/upload` |
| List docs | GET | `/documents` |
| Index RAG | POST | `/rag/documents/{id}/index` |
