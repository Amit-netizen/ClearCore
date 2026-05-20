-- V2__seed_data.sql
-- Sample data for development and testing

INSERT INTO merchants (id, name, email, balance) VALUES
    ('a1b2c3d4-e5f6-7890-abcd-ef1234567890', 'Amazon India', 'payments@amazon.in', 0),
    ('b2c3d4e5-f6a7-8901-bcde-f12345678901', 'Flipkart', 'payments@flipkart.com', 0),
    ('c3d4e5f6-a7b8-9012-cdef-123456789012', 'Swiggy', 'payments@swiggy.com', 0);

-- Cards with bcrypt hash of '123' as CVV (for testing)
-- bcrypt hash: $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
INSERT INTO cards (id, card_number, card_holder_name, expiry_month, expiry_year, cvv_hash, available_balance, credit_limit) VALUES
    ('d4e5f6a7-b8c9-0123-defa-234567890123', '4111-1111-1111-1111', 'Amit Kumar',
     12, 2027, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
     500000, 1000000),  -- ₹5000 balance, ₹10000 limit
    ('e5f6a7b8-c9d0-1234-efab-345678901234', '5500-0000-0000-0004', 'Test User',
     6, 2025,  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
     200000, 500000),   -- expired card for testing
    ('f6a7b8c9-d0e1-2345-fabc-456789012345', '3714-496353-98431', 'Rich User',
     3, 2028, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
     10000000, 20000000);  -- ₹1L balance for load testing
