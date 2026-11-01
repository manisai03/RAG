import axiosClient from './axiosClient';

export const askRag = (question, documentId = null) =>
  axiosClient.post('/rag/ask', { question, documentId }, { timeout: 0 });

export const generateQuestionsFromLatest = () =>
  axiosClient.post('/rag/documents/latest/questions', null, { timeout: 0 });

export const generateQuestionsFromDocument = (documentId) =>
  axiosClient.post(`/rag/documents/${documentId}/questions`, null, { timeout: 0 });
