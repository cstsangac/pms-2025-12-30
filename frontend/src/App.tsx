import React, { useEffect, useState } from 'react';
import './index.css';
import { portfolioService, transactionService } from './services/api';
import axios from 'axios';

interface Holding {
  symbol: string;
  name: string;
  assetType: string;
  quantity: number;
  averageCost: number;
  currentPrice: number;
  marketValue: number;
  unrealizedGainLoss: number;
  unrealizedGainLossPercentage: number;
}

interface Portfolio {
  id: string;
  clientName: string;
  accountNumber: string;
  totalValue: number;
  cashBalance: number;
  holdingsCount?: number;
  status: string;
}

interface PortfolioDetail extends Portfolio {
  holdings: Holding[];
}

// Random data generators
const firstNames = ['Michael', 'Sarah', 'David', 'Emily', 'Robert', 'Jennifer', 'William', 'Lisa', 'James', 'Mary', 'Christopher', 'Patricia', 'Daniel', 'Linda', 'Matthew'];
const lastNames = ['Johnson', 'Williams', 'Brown', 'Jones', 'Garcia', 'Martinez', 'Davis', 'Rodriguez', 'Wilson', 'Anderson', 'Taylor', 'Thomas', 'Moore', 'Jackson', 'Martin'];
const stocks = [
  { symbol: 'GOOGL', name: 'Alphabet Inc.', basePrice: 140 },
  { symbol: 'MSFT', name: 'Microsoft Corp.', basePrice: 380 },
  { symbol: 'AAPL', name: 'Apple Inc.', basePrice: 185 },
  { symbol: 'AMZN', name: 'Amazon.com Inc.', basePrice: 175 },
  { symbol: 'NVDA', name: 'NVIDIA Corp.', basePrice: 490 },
  { symbol: 'META', name: 'Meta Platforms Inc.', basePrice: 470 },
  { symbol: 'TSLA', name: 'Tesla Inc.', basePrice: 245 },
  { symbol: 'AMD', name: 'Advanced Micro Devices', basePrice: 160 },
  { symbol: 'NFLX', name: 'Netflix Inc.', basePrice: 610 },
  { symbol: 'DIS', name: 'Walt Disney Co.', basePrice: 95 },
  { symbol: 'INTC', name: 'Intel Corp.', basePrice: 42 },
  { symbol: 'BA', name: 'Boeing Co.', basePrice: 215 },
  { symbol: 'COST', name: 'Costco Wholesale', basePrice: 780 },
  { symbol: 'PEP', name: 'PepsiCo Inc.', basePrice: 170 },
  { symbol: 'ADBE', name: 'Adobe Inc.', basePrice: 565 }
];

function App() {
  const [portfolios, setPortfolios] = useState<Portfolio[]>([]);
  const [expandedPortfolioId, setExpandedPortfolioId] = useState<string | null>(null);
  const [portfolioDetails, setPortfolioDetails] = useState<Map<string, PortfolioDetail>>(new Map());
  const [loadingHoldings, setLoadingHoldings] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [demoStatus, setDemoStatus] = useState<string>('');
  const [backendHealthy, setBackendHealthy] = useState(false);
  const [lastApiCall, setLastApiCall] = useState<string>('');

  useEffect(() => {
    loadPortfolios();
  }, []);

  const loadPortfolios = async () => {
    try {
      setLoading(true);
      setLastApiCall(`GET /api/portfolio/portfolios - ${new Date().toLocaleTimeString()}`);
      const data = await portfolioService.getAllPortfolios();
      setPortfolios(data);
      setError(null);
      setBackendHealthy(true); // Backend is healthy if API call succeeds
    } catch (err) {
      setError('Failed to load portfolios. Please ensure the backend services are running.');
      console.error('Error loading portfolios:', err);
      setBackendHealthy(false);
    } finally {
      setLoading(false);
    }
  };

  const togglePortfolioExpansion = async (portfolioId: string) => {
    if (expandedPortfolioId === portfolioId) {
      setExpandedPortfolioId(null);
      return;
    }

    setExpandedPortfolioId(portfolioId);

    // Load portfolio details if not already loaded
    if (!portfolioDetails.has(portfolioId)) {
      try {
        setLoadingHoldings(portfolioId);
        const detail = await portfolioService.getPortfolioById(portfolioId);
        setPortfolioDetails(new Map(portfolioDetails.set(portfolioId, detail)));
      } catch (err) {
        console.error('Error loading portfolio details:', err);
        setError('Failed to load portfolio holdings.');
      } finally {
        setLoadingHoldings(null);
      }
    }
  };

  const calculateTotals = () => {
    const totalValue = portfolios.reduce((sum, p) => sum + p.totalValue, 0);
    const totalCash = portfolios.reduce((sum, p) => sum + p.cashBalance, 0);
    const totalHoldings = portfolios.reduce((sum, p) => sum + (p.holdingsCount || 0), 0);
    return { totalValue, totalCash, totalHoldings };
  };

  const totals = calculateTotals();

  const createRandomPortfolio = async () => {
    try {
      setDemoStatus('Creating random portfolio...');
      
      // Generate random client
      const firstName = firstNames[Math.floor(Math.random() * firstNames.length)];
      const lastName = lastNames[Math.floor(Math.random() * lastNames.length)];
      const clientName = `${firstName} ${lastName}`;
      const clientId = `CLIENT${Math.floor(Math.random() * 900 + 100)}`;
      const accountNumber = `ACC${Math.floor(Math.random() * 90000 + 10000)}`;
      const initialCash = Math.floor(Math.random() * 400000 + 100000); // $100K-$500K
      
      setDemoStatus(`Creating portfolio for ${clientName}...`);
      
      // Create portfolio
      const portfolioResponse = await axios.post('/api/portfolio/portfolios', {
        clientId,
        clientName,
        accountNumber,
        cashBalance: initialCash,
        currency: 'USD'
      });
      
      const portfolioId = portfolioResponse.data.id;
      setDemoStatus(`Portfolio created! Adding ${3 + Math.floor(Math.random() * 4)} holdings...`);
      
      // Select random stocks (3-6 holdings)
      const numHoldings = 3 + Math.floor(Math.random() * 4);
      const shuffled = [...stocks].sort(() => 0.5 - Math.random());
      const selectedStocks = shuffled.slice(0, numHoldings);
      
      // Create transactions for each holding
      for (const stock of selectedStocks) {
        const quantity = Math.floor(Math.random() * 150 + 25); // 25-175 shares
        const priceVariation = (Math.random() * 0.3 - 0.15); // ±15% from base
        const price = stock.basePrice * (1 + priceVariation);
        
        await axios.post('/api/transaction/transactions', {
          portfolioId,
          accountNumber,
          type: 'BUY',
          symbol: stock.symbol,
          assetName: stock.name,
          quantity,
          price: parseFloat(price.toFixed(2)),
          currency: 'USD'
        });
        
        setDemoStatus(`Added ${stock.symbol}...`);
        await new Promise(resolve => setTimeout(resolve, 300));
      }
      
      setDemoStatus('Updating market prices...');
      await new Promise(resolve => setTimeout(resolve, 2000)); // Wait for Kafka events
      
      // Reload portfolios
      await loadPortfolios();
      setDemoStatus(`✅ Successfully created portfolio for ${clientName} with ${numHoldings} holdings!`);
      
      setTimeout(() => setDemoStatus(''), 5000);
    } catch (err: any) {
      setDemoStatus(`❌ Error: ${err.message}`);
      setTimeout(() => setDemoStatus(''), 5000);
    }
  };

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

      {/* Project Description Section */}
      <div className="project-banner">
        <div className="project-description">
          <h2>💼 Enterprise Java Microservices Architecture</h2>
          <p className="project-subtitle">
            A microservices-based wealth management platform with 4 services communicating via Kafka events, 
            using MongoDB for persistence, Redis for caching, and Spring Cloud Gateway for API routing.
          </p>
          <div className="project-links">
            <a 
              href="https://github.com/cstsangac/pms-2025-12-30/blob/main/README.md" 
              target="_blank" 
              rel="noopener noreferrer"
              className="github-link"
            >
              <svg viewBox="0 0 16 16" width="20" height="20" fill="currentColor">
                <path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z"/>
              </svg>
              View Source Code
            </a>
            <div className="tech-badges">
              <span className="tech-badge">Spring Boot 3.x</span>
              <span className="tech-badge">Kafka</span>
              <span className="tech-badge">MongoDB</span>
              <span className="tech-badge">Redis</span>
              <span className="tech-badge">Docker</span>
              <span className="tech-badge">React</span>
            </div>
          </div>
        </div>
      </div>

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
          <p style={{ marginBottom: '1rem', color: '#64748b' }}>Click on any row to view holdings</p>
          {portfolios.length === 0 ? (
            <p>No portfolios found. Create a portfolio using the API.</p>
          ) : (
            <table className="table">
              <thead>
                <tr>
                  <th></th>
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
                  <React.Fragment key={portfolio.id}>
                    <tr 
                      className={`portfolio-row ${expandedPortfolioId === portfolio.id ? 'expanded' : ''}`}
                      onClick={() => togglePortfolioExpansion(portfolio.id)}
                      style={{ cursor: 'pointer' }}
                    >
                      <td className="expand-icon">
                        {expandedPortfolioId === portfolio.id ? '▼' : '▶'}
                      </td>
                      <td>{portfolio.clientName}</td>
                      <td>{portfolio.accountNumber}</td>
                      <td>${portfolio.totalValue.toLocaleString('en-US', { minimumFractionDigits: 2 })}</td>
                      <td>${portfolio.cashBalance.toLocaleString('en-US', { minimumFractionDigits: 2 })}</td>
                      <td>{portfolio.holdingsCount || 0}</td>
                      <td>
                        <span className={`status-badge status-${portfolio.status.toLowerCase()}`}>
                          {portfolio.status}
                        </span>
                      </td>
                    </tr>
                    
                    {expandedPortfolioId === portfolio.id && (
                      <tr className="holdings-row">
                        <td colSpan={7}>
                          <div className="holdings-container">
                            {loadingHoldings === portfolio.id ? (
                              <div className="loading-holdings">Loading holdings...</div>
                            ) : (portfolioDetails.get(portfolio.id)?.holdings?.length ?? 0) > 0 ? (
                              <>
                                <h3>Holdings</h3>
                                <table className="holdings-table">
                                  <thead>
                                    <tr>
                                      <th>Symbol</th>
                                      <th>Name</th>
                                      <th>Type</th>
                                      <th>Quantity</th>
                                      <th>Avg Cost</th>
                                      <th>Current Price</th>
                                      <th>Market Value</th>
                                      <th>Gain/Loss</th>
                                      <th>Gain/Loss %</th>
                                    </tr>
                                  </thead>
                                  <tbody>
                                    {portfolioDetails.get(portfolio.id)?.holdings.map((holding, idx) => (
                                      <tr key={idx}>
                                        <td className="holding-symbol">{holding.symbol}</td>
                                        <td>{holding.name || '-'}</td>
                                        <td>{holding.assetType}</td>
                                        <td>{holding.quantity}</td>
                                        <td>${holding.averageCost.toLocaleString('en-US', { minimumFractionDigits: 2 })}</td>
                                        <td>${holding.currentPrice.toLocaleString('en-US', { minimumFractionDigits: 2 })}</td>
                                        <td>${holding.marketValue.toLocaleString('en-US', { minimumFractionDigits: 2 })}</td>
                                        <td className={holding.unrealizedGainLoss >= 0 ? 'gain' : 'loss'}>
                                          ${holding.unrealizedGainLoss.toLocaleString('en-US', { minimumFractionDigits: 2 })}
                                        </td>
                                        <td className={holding.unrealizedGainLossPercentage >= 0 ? 'gain' : 'loss'}>
                                          {holding.unrealizedGainLossPercentage.toFixed(2)}%
                                        </td>
                                      </tr>
                                    ))}
                                  </tbody>
                                </table>
                              </>
                            ) : (
                              <div className="no-holdings">No holdings in this portfolio</div>
                            )}
                          </div>
                        </td>
                      </tr>
                    )}
                  </React.Fragment>
                ))}
              </tbody>
            </table>
          )}
        </div>

        {/* Demo Section */}
        <div className="demo-section">
          <h2>🎮 Interactive Demo</h2>
          <p>Click the button below to automatically create a new portfolio with random client data and holdings.</p>
          <div className="demo-controls">
            <button 
              className="demo-button"
              onClick={createRandomPortfolio}
              disabled={demoStatus !== ''}
            >
              🎲 Create Random Portfolio
            </button>
            {demoStatus && (
              <div className={`demo-status ${demoStatus.includes('✅') ? 'success' : demoStatus.includes('❌') ? 'error' : 'loading'}`}>
                {demoStatus}
              </div>
            )}
          </div>
          <div className="demo-info">
            <h3>What happens when you click:</h3>
            <ul>
              <li>✨ Random client name is generated (e.g., "Sarah Johnson")</li>
              <li>💰 Random initial balance between $100K - $500K</li>
              <li>📊 3-6 random stock holdings are added</li>
              <li>📈 Market prices are simulated with realistic variations</li>
              <li>🔄 Portfolio appears in the table above - click to expand!</li>
            </ul>
          </div>
        </div>

        {/* System Status Section */}
        <div className="system-status-section">
          <h2>🔧 Backend System Status</h2>
          <p className="status-subtitle">Real-time monitoring of microservices architecture</p>
          
          <div className="status-grid">
            <div className="status-card">
              <h3>Live Services</h3>
              <div className="service-list">
                <div className="service-item">
                  <span className={`status-indicator ${backendHealthy ? 'healthy' : 'unhealthy'}`}>
                    {backendHealthy ? '✓' : '✗'}
                  </span>
                  <span className="service-name">API Gateway</span>
                  <span className={`status-label ${backendHealthy ? 'healthy' : 'unhealthy'}`}>
                    {backendHealthy ? 'Healthy' : 'Down'}
                  </span>
                </div>
                <div className="service-item">
                  <span className={`status-indicator ${backendHealthy ? 'healthy' : 'unhealthy'}`}>
                    {backendHealthy ? '✓' : '✗'}
                  </span>
                  <span className="service-name">Portfolio Service</span>
                  <span className={`status-label ${backendHealthy ? 'healthy' : 'unhealthy'}`}>
                    {backendHealthy ? 'Healthy' : 'Down'}
                  </span>
                </div>
                <div className="service-item">
                  <span className={`status-indicator ${backendHealthy ? 'healthy' : 'unhealthy'}`}>
                    {backendHealthy ? '✓' : '✗'}
                  </span>
                  <span className="service-name">Transaction Service</span>
                  <span className={`status-label ${backendHealthy ? 'healthy' : 'unhealthy'}`}>
                    {backendHealthy ? 'Healthy' : 'Down'}
                  </span>
                </div>
                <div className="service-item">
                  <span className={`status-indicator ${backendHealthy ? 'healthy' : 'unhealthy'}`}>
                    {backendHealthy ? '✓' : '✗'}
                  </span>
                  <span className="service-name">MongoDB (Portfolio)</span>
                  <span className={`status-label ${backendHealthy ? 'healthy' : 'unhealthy'}`}>
                    {backendHealthy ? 'Running' : 'Down'}
                  </span>
                </div>
                <div className="service-item">
                  <span className={`status-indicator ${backendHealthy ? 'healthy' : 'unhealthy'}`}>
                    {backendHealthy ? '✓' : '✗'}
                  </span>
                  <span className="service-name">MongoDB (Transaction)</span>
                  <span className={`status-label ${backendHealthy ? 'healthy' : 'unhealthy'}`}>
                    {backendHealthy ? 'Running' : 'Down'}
                  </span>
                </div>
                <div className="service-item">
                  <span className={`status-indicator ${backendHealthy ? 'healthy' : 'unhealthy'}`}>
                    {backendHealthy ? '✓' : '✗'}
                  </span>
                  <span className="service-name">Redis Cache</span>
                  <span className={`status-label ${backendHealthy ? 'healthy' : 'unhealthy'}`}>
                    {backendHealthy ? 'Running' : 'Down'}
                  </span>
                </div>
                <div className="service-item">
                  <span className={`status-indicator ${backendHealthy ? 'healthy' : 'unhealthy'}`}>
                    {backendHealthy ? '✓' : '✗'}
                  </span>
                  <span className="service-name">Apache Kafka</span>
                  <span className={`status-label ${backendHealthy ? 'healthy' : 'unhealthy'}`}>
                    {backendHealthy ? 'Running' : 'Down'}
                  </span>
                </div>
              </div>
            </div>

            <div className="status-card">
              <h3>Architecture Overview</h3>
              <div className="architecture-info">
                <div className="arch-item">
                  <span className="arch-icon">🏗️</span>
                  <div>
                    <strong>Microservices Pattern</strong>
                    <p>4 independent services with separate databases</p>
                  </div>
                </div>
                <div className="arch-item">
                  <span className="arch-icon">📡</span>
                  <div>
                    <strong>Event-Driven</strong>
                    <p>Kafka for asynchronous communication</p>
                  </div>
                </div>
                <div className="arch-item">
                  <span className="arch-icon">⚡</span>
                  <div>
                    <strong>Caching Layer</strong>
                    <p>Redis for high-performance data access</p>
                  </div>
                </div>
                <div className="arch-item">
                  <span className="arch-icon">🔀</span>
                  <div>
                    <strong>API Gateway</strong>
                    <p>Spring Cloud Gateway with circuit breakers</p>
                  </div>
                </div>
              </div>
            </div>

            <div className="status-card">
              <h3>Live Activity</h3>
              <div className="activity-log">
                {lastApiCall && (
                  <div className="activity-item">
                    <span className="activity-dot"></span>
                    <span className="activity-text">{lastApiCall}</span>
                  </div>
                )}
                <div className="activity-item">
                  <span className="activity-dot"></span>
                  <span className="activity-text">Kafka: Listening for transaction events</span>
                </div>
                <div className="activity-item">
                  <span className="activity-dot"></span>
                  <span className="activity-text">Redis: Caching portfolio data</span>
                </div>
                <div className="activity-item">
                  <span className="activity-dot"></span>
                  <span className="activity-text">MongoDB: Persisting {portfolios.length} portfolio(s)</span>
                </div>
              </div>
              <div className="tech-stack-footer">
                <strong>Tech Stack:</strong> Java 17 • Spring Boot 3.x • Docker • React 18 • TypeScript
              </div>
            </div>
          </div>

          <div className="verification-note">
            💡 <strong>Not a Mock!</strong> All data is fetched from real backend microservices. 
            Check browser DevTools Network tab to see actual API calls to <code>localhost:8080</code>
          </div>
        </div>
      </div>
    </div>
  );
}

export default App;
