import axiosClient from './axiosClient';

export const askQuestion = (question, documentId = null, useDocuments = null, history = []) =>
  axiosClient.post(
    '/chat/ask',
    { question, documentId, useDocuments, history },
    { timeout: 0 }
  );
