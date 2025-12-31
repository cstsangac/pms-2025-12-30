import React, { useEffect, useState } from 'react';
import './index.css';
import { portfolioService } from './services/api';

interface Portfolio {
  id: string;
  clientName: string;
  accountNumber: string;
  totalValue: number;
  cashBalance: number;
  holdingsCount?: number;
  status: string;
}

function App() {
  const [portfolios, setPortfolios] = useState<Portfolio[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    loadPortfolios();
  }, []);

  const loadPortfolios = async () => {
    try {
      setLoading(true);
      const data = await portfolioService.getAllPortfolios();
      setPortfolios(data);
      setError(null);
    } catch (err) {
      setError('Failed to load portfolios. Please ensure the backend services are running.');
      console.error('Error loading portfolios:', err);
    } finally {
      setLoading(false);
    }
  };

  const calculateTotals = () => {
    const totalValue = portfolios.reduce((sum, p) => sum + p.totalValue, 0);
    const totalCash = portfolios.reduce((sum, p) => sum + p.cashBalance, 0);
    const totalHoldings = portfolios.reduce((sum, p) => sum + (p.holdingsCount || 0), 0);
    return { totalValue, totalCash, totalHoldings };
  };

  const totals = calculateTotals();

  if (loading) {
    return (
      <div className="app">
        <header className="header">
          <h1>Portfolio Management System</h1>
        </header>
        <div className="loading">Loading portfolios...</div>
      </div>
    );
  }

  return (
    <div className="app">
      <header className="header">
        <h1>Portfolio Management System</h1>
        <p>Wealth Management Dashboard</p>
      </header>

      <div className="container">
        {error && (
          <div className="error">
            {error}
          </div>
        )}

        <div className="dashboard-grid">
          <div className="card">
            <h2>Total Portfolio Value</h2>
            <div className="card-value">${totals.totalValue.toLocaleString('en-US', { minimumFractionDigits: 2 })}</div>
            <div className="card-label">Across {portfolios.length} portfolio(s)</div>
          </div>

          <div className="card">
            <h2>Total Cash Balance</h2>
            <div className="card-value">${totals.totalCash.toLocaleString('en-US', { minimumFractionDigits: 2 })}</div>
            <div className="card-label">Available for investment</div>
          </div>

          <div className="card">
            <h2>Total Holdings</h2>
            <div className="card-value">{totals.totalHoldings}</div>
            <div className="card-label">Positions across all portfolios</div>
          </div>
        </div>

        <div className="section">
          <h2>Client Portfolios</h2>
          {portfolios.length === 0 ? (
            <p>No portfolios found. Create a portfolio using the API.</p>
          ) : (
            <table className="table">
              <thead>
                <tr>
                  <th>Client Name</th>
                  <th>Account Number</th>
                  <th>Total Value</th>
                  <th>Cash Balance</th>
                  <th>Holdings</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {portfolios.map((portfolio) => (
                  <tr key={portfolio.id}>
                    <td>{portfolio.clientName}</td>
                    <td>{portfolio.accountNumber}</td>
                    <td>${portfolio.totalValue.toLocaleString('en-US', { minimumFractionDigits: 2 })}</td>
                    <td>${portfolio.cashBalance.toLocaleString('en-US', { minimumFractionDigits: 2 })}</td>
                    <td>{portfolio.holdingsCount || 0}</td>
                    <td>{portfolio.status}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>

        {/* Holdings detail can be shown by fetching individual portfolio by ID */}
      </div>
    </div>
  );
}

export default App;
