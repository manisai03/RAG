import axiosClient from './axiosClient';

export const uploadDocument = (file, description) => {
  const formData = new FormData();
  formData.append('file', file);
  if (description) {
    formData.append('description', description);
  }
  return axiosClient.post('/documents/upload', formData);
};

export const getDocuments = () =>
  axiosClient.get('/documents');

export const indexDocument = (documentId) =>
  axiosClient.post(`/rag/documents/${documentId}/index`, null, {
    timeout: 0,
  });

export const deleteDocument = (documentId) =>
  axiosClient.delete(`/documents/${documentId}`, {
    validateStatus: (status) => status === 204 || status === 200,
  });
