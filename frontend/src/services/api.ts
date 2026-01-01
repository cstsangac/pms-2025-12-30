import axios from 'axios';

// Toggle between mock and real API
const USE_MOCK_DATA = false;

const API_BASE_URL = (import.meta as any).env?.VITE_API_BASE_URL || 'http://localhost:8080/api';

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Mock data
const mockPortfolios = [
  {
    id: '1',
    clientId: 'CLIENT001',
    clientName: 'John Doe',
    accountNumber: 'ACC12345',
    totalValue: 150000,
    cashBalance: 25000,
    holdingsCount: 5,
    status: 'ACTIVE',
  },
  {
    id: '2',
    clientId: 'CLIENT002',
    clientName: 'Jane Smith',
    accountNumber: 'ACC67890',
    totalValue: 280000,
    cashBalance: 50000,
    holdingsCount: 8,
    status: 'ACTIVE',
  },
];

const mockPortfolioDetails: any = {
  '1': {
    id: '1',
    clientId: 'CLIENT001',
    clientName: 'John Doe',
    accountNumber: 'ACC12345',
    totalValue: 150000,
    cashBalance: 25000,
    status: 'ACTIVE',
    holdings: [
      {
        symbol: 'AAPL',
        name: 'Apple Inc.',
        assetType: 'STOCK',
        quantity: 100,
        averageCost: 150,
        currentPrice: 185,
        marketValue: 18500,
        unrealizedGainLoss: 3500,
        unrealizedGainLossPercentage: 23.33,
      },
      {
        symbol: 'GOOGL',
        name: 'Alphabet Inc.',
        assetType: 'STOCK',
        quantity: 50,
        averageCost: 120,
        currentPrice: 140,
        marketValue: 7000,
        unrealizedGainLoss: 1000,
        unrealizedGainLossPercentage: 16.67,
      },
    ],
  },
  '2': {
    id: '2',
    clientId: 'CLIENT002',
    clientName: 'Jane Smith',
    accountNumber: 'ACC67890',
    totalValue: 280000,
    cashBalance: 50000,
    status: 'ACTIVE',
    holdings: [
      {
        symbol: 'MSFT',
        name: 'Microsoft Corp.',
        assetType: 'STOCK',
        quantity: 200,
        averageCost: 350,
        currentPrice: 380,
        marketValue: 76000,
        unrealizedGainLoss: 6000,
        unrealizedGainLossPercentage: 8.57,
      },
    ],
  },
};

const mockTransactions = [
  {
    id: 'TXN001',
    portfolioId: '1',
    type: 'BUY',
    symbol: 'AAPL',
    quantity: 50,
    price: 150,
    amount: 7500,
    status: 'COMPLETED',
    transactionDate: new Date().toISOString(),
  },
  {
    id: 'TXN002',
    portfolioId: '2',
    type: 'SELL',
    symbol: 'GOOGL',
    quantity: 20,
    price: 140,
    amount: 2800,
    status: 'COMPLETED',
    transactionDate: new Date().toISOString(),
  },
];

export const portfolioService = {
  getAllPortfolios: async () => {
    if (USE_MOCK_DATA) {
      return new Promise((resolve) => setTimeout(() => resolve(mockPortfolios), 300));
    }
    const response = await apiClient.get('/portfolio/portfolios');
    return response.data;
  },

  getPortfolioById: async (id: string) => {
    if (USE_MOCK_DATA) {
      return new Promise((resolve) =>
        setTimeout(() => resolve(mockPortfolioDetails[id] || null), 300)
      );
    }
    const response = await apiClient.get(`/portfolio/portfolios/${id}`);
    return response.data;
  },

  createPortfolio: async (data: any) => {
    if (USE_MOCK_DATA) {
      const newPortfolio = {
        id: Math.random().toString(36).substr(2, 9),
        ...data,
        totalValue: data.cashBalance || 0,
        holdingsCount: 0,
        status: 'ACTIVE',
      };
      return new Promise((resolve) => setTimeout(() => resolve(newPortfolio), 300));
    }
    const response = await apiClient.post('/portfolio/portfolios', data);
    return response.data;
  },

  addHolding: async (portfolioId: string, data: any) => {
    if (USE_MOCK_DATA) {
      return new Promise((resolve) => setTimeout(() => resolve({ success: true }), 300));
    }
    const response = await apiClient.post(`/portfolio/portfolios/${portfolioId}/holdings`, data);
    return response.data;
  },
};

export const transactionService = {
  getAllTransactions: async () => {
    if (USE_MOCK_DATA) {
      return new Promise((resolve) => setTimeout(() => resolve(mockTransactions), 300));
    }
    const response = await apiClient.get('/transaction/transactions');
    return response.data;
  },

  getTransactionsByPortfolio: async (portfolioId: string) => {
    if (USE_MOCK_DATA) {
      const filtered = mockTransactions.filter((t) => t.portfolioId === portfolioId);
      return new Promise((resolve) => setTimeout(() => resolve(filtered), 300));
    }
    const response = await apiClient.get(`/transaction/transactions/portfolio/${portfolioId}`);
    return response.data;
  },

  createTransaction: async (data: any) => {
    if (USE_MOCK_DATA) {
      const newTransaction = {
        id: Math.random().toString(36).substr(2, 9),
        ...data,
        status: 'PENDING',
        transactionDate: new Date().toISOString(),
      };
      return new Promise((resolve) => setTimeout(() => resolve(newTransaction), 300));
    }
    const response = await apiClient.post('/transaction/transactions', data);
    return response.data;
  },
};

export default apiClient;
