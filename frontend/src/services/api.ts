import axios from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api';

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

export const portfolioService = {
  getAllPortfolios: async () => {
    const response = await apiClient.get('/portfolio/portfolios');
    return response.data;
  },

  getPortfolioById: async (id: string) => {
    const response = await apiClient.get(`/portfolio/portfolios/${id}`);
    return response.data;
  },

  createPortfolio: async (data: any) => {
    const response = await apiClient.post('/portfolio/portfolios', data);
    return response.data;
  },

  addHolding: async (portfolioId: string, data: any) => {
    const response = await apiClient.post(`/portfolio/portfolios/${portfolioId}/holdings`, data);
    return response.data;
  },
};

export const transactionService = {
  getAllTransactions: async () => {
    const response = await apiClient.get('/transaction/transactions');
    return response.data;
  },

  getTransactionsByPortfolio: async (portfolioId: string) => {
    const response = await apiClient.get(`/transaction/transactions/portfolio/${portfolioId}`);
    return response.data;
  },

  createTransaction: async (data: any) => {
    const response = await apiClient.post('/transaction/transactions', data);
    return response.data;
  },
};

export default apiClient;
