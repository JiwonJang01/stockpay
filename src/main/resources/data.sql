INSERT INTO stock (
    stock_ticker,
    stock_code,
    stock_status,
    stock_name,
    stock_dec,
    stock_sector,
    stock_open,
    stock_close,
    stock_createtime,
    stock_changetime
) VALUES
('005930', 'KRX', 'LISTED', '삼성전자', '반도체 및 IT 솔루션', '반도체', '1975-06-11', NULL, NOW(), NOW()),
('000660', 'KRX', 'LISTED', 'SK하이닉스', '메모리 반도체', '반도체', '1996-12-26', NULL, NOW(), NOW()),
('035420', 'KRX', 'LISTED', 'NAVER', '인터넷 포털 서비스', 'IT서비스', '2002-10-29', NULL, NOW(), NOW()),
('051910', 'KRX', 'LISTED', 'LG화학', '석유화학 및 배터리', '화학', '2001-04-03', NULL, NOW(), NOW()),
('006400', 'KRX', 'LISTED', '삼성SDI', '2차전지 및 전자재료', '전기전자', '2002-06-19', NULL, NOW(), NOW())
ON CONFLICT (stock_ticker) DO UPDATE SET
    stock_name = EXCLUDED.stock_name,
    stock_changetime = NOW();