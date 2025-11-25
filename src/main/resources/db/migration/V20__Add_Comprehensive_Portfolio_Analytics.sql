-- V20: Add Comprehensive Portfolio Analytics Fields
-- Adds new financial metrics, valuation tracking, and vacant period analysis to portfolio_analytics table

-- Add comprehensive financial metrics (Last 12 months or custom range)
ALTER TABLE portfolio_analytics ADD COLUMN rent_due DECIMAL(12, 2) DEFAULT 0.00;
ALTER TABLE portfolio_analytics ADD COLUMN rent_received DECIMAL(12, 2) DEFAULT 0.00;
ALTER TABLE portfolio_analytics ADD COLUMN total_expenses DECIMAL(12, 2) DEFAULT 0.00;
ALTER TABLE portfolio_analytics ADD COLUMN total_commission DECIMAL(12, 2) DEFAULT 0.00;
ALTER TABLE portfolio_analytics ADD COLUMN net_income DECIMAL(12, 2) DEFAULT 0.00;

-- Add cumulative arrears (all time to calculation date)
ALTER TABLE portfolio_analytics ADD COLUMN total_arrears DECIMAL(12, 2) DEFAULT 0.00;

-- Add valuation metrics
ALTER TABLE portfolio_analytics ADD COLUMN total_purchase_price DECIMAL(14, 2) DEFAULT 0.00;
ALTER TABLE portfolio_analytics ADD COLUMN total_current_value DECIMAL(14, 2) DEFAULT 0.00;
ALTER TABLE portfolio_analytics ADD COLUMN total_capital_gain DECIMAL(14, 2) DEFAULT 0.00;
ALTER TABLE portfolio_analytics ADD COLUMN capital_gain_percentage DECIMAL(5, 2) DEFAULT 0.00;

-- Add yield calculations
ALTER TABLE portfolio_analytics ADD COLUMN gross_yield DECIMAL(5, 2) DEFAULT 0.00;
ALTER TABLE portfolio_analytics ADD COLUMN net_yield DECIMAL(5, 2) DEFAULT 0.00;

-- Add vacant period tracking
ALTER TABLE portfolio_analytics ADD COLUMN average_days_vacant DECIMAL(6, 1) DEFAULT 0.0;
ALTER TABLE portfolio_analytics ADD COLUMN current_vacancies INT DEFAULT 0;
ALTER TABLE portfolio_analytics ADD COLUMN total_vacant_days INT DEFAULT 0;

-- Add index for performance
CREATE INDEX idx_portfolio_analytics_calculation ON portfolio_analytics(portfolio_id, calculation_date);

-- Add column comments (MySQL syntax)
ALTER TABLE portfolio_analytics MODIFY COLUMN rent_due DECIMAL(12, 2) DEFAULT 0.00 COMMENT 'Total rent due from leases/invoices (last 12 months)';
ALTER TABLE portfolio_analytics MODIFY COLUMN rent_received DECIMAL(12, 2) DEFAULT 0.00 COMMENT 'Actual rent received from transactions (last 12 months)';
ALTER TABLE portfolio_analytics MODIFY COLUMN total_expenses DECIMAL(12, 2) DEFAULT 0.00 COMMENT 'Total property expenses (last 12 months)';
ALTER TABLE portfolio_analytics MODIFY COLUMN total_commission DECIMAL(12, 2) DEFAULT 0.00 COMMENT 'Total management commissions (last 12 months)';
ALTER TABLE portfolio_analytics MODIFY COLUMN net_income DECIMAL(12, 2) DEFAULT 0.00 COMMENT 'Net income after expenses and commission (last 12 months)';
ALTER TABLE portfolio_analytics MODIFY COLUMN total_arrears DECIMAL(12, 2) DEFAULT 0.00 COMMENT 'Cumulative rent arrears (all time to calculation date)';
ALTER TABLE portfolio_analytics MODIFY COLUMN total_purchase_price DECIMAL(14, 2) DEFAULT 0.00 COMMENT 'Sum of all property purchase prices in portfolio';
ALTER TABLE portfolio_analytics MODIFY COLUMN total_current_value DECIMAL(14, 2) DEFAULT 0.00 COMMENT 'Sum of all current property valuations';
ALTER TABLE portfolio_analytics MODIFY COLUMN total_capital_gain DECIMAL(14, 2) DEFAULT 0.00 COMMENT 'Total capital appreciation (current value - purchase price)';
ALTER TABLE portfolio_analytics MODIFY COLUMN capital_gain_percentage DECIMAL(5, 2) DEFAULT 0.00 COMMENT 'Capital gain as percentage of purchase price';
ALTER TABLE portfolio_analytics MODIFY COLUMN gross_yield DECIMAL(5, 2) DEFAULT 0.00 COMMENT 'Gross yield: (Annual Rent / Portfolio Value) × 100';
ALTER TABLE portfolio_analytics MODIFY COLUMN net_yield DECIMAL(5, 2) DEFAULT 0.00 COMMENT 'Net yield: (Annual Net Income / Portfolio Value) × 100';
ALTER TABLE portfolio_analytics MODIFY COLUMN average_days_vacant DECIMAL(6, 1) DEFAULT 0.0 COMMENT 'Average days properties have been vacant';
ALTER TABLE portfolio_analytics MODIFY COLUMN current_vacancies INT DEFAULT 0 COMMENT 'Number of currently vacant properties';
ALTER TABLE portfolio_analytics MODIFY COLUMN total_vacant_days INT DEFAULT 0 COMMENT 'Total days of vacancy across all properties';
