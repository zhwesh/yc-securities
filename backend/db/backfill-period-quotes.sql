-- ============================================================
-- 周/月/季/年K全量回填脚本（可重复执行）
-- 数据源：week/month 来自 daily_quote，quarter 来自 month_quote，year 来自 quarter_quote
-- 执行前建议停止应用；首次执行会扫描全量日线，耗时较长（分钟级）
-- 执行：cmd /c "mysql -uroot -p --default-character-set=utf8mb4 yc_securities < db\backfill-period-quotes.sql"
-- ============================================================

SET SESSION group_concat_max_len = 1000000;
SET SESSION tmp_table_size = 536870912;
SET SESSION max_heap_table_size = 536870912;

-- 周K：按 ISO 周（周一至周日）聚合
INSERT INTO week_quote (stock_code, period_key, date, open, high, low, close, pre_close,
                        volume, amount)
SELECT d.stock_code,
       DATE_FORMAT(d.date, '%x-W%v') AS period_key,
       MAX(d.date) AS date,
       SUBSTRING_INDEX(GROUP_CONCAT(d.open ORDER BY d.date SEPARATOR ','), ',', 1) AS open,
       MAX(d.high) AS high,
       MIN(d.low) AS low,
       SUBSTRING_INDEX(GROUP_CONCAT(d.close ORDER BY d.date DESC SEPARATOR ','), ',', 1) AS close,
       SUBSTRING_INDEX(GROUP_CONCAT(d.pre_close ORDER BY d.date SEPARATOR ','), ',', 1)
           AS pre_close,
       SUM(d.volume) AS volume,
       SUM(d.amount) AS amount
FROM daily_quote d
GROUP BY d.stock_code, DATE_FORMAT(d.date, '%x-W%v')
ON DUPLICATE KEY UPDATE
    date = VALUES(date),
    open = VALUES(open),
    high = VALUES(high),
    low = VALUES(low),
    close = VALUES(close),
    pre_close = VALUES(pre_close),
    volume = VALUES(volume),
    amount = VALUES(amount);

-- 月K
INSERT INTO month_quote (stock_code, period_key, date, open, high, low, close, pre_close,
                         volume, amount)
SELECT d.stock_code,
       DATE_FORMAT(d.date, '%Y-%m') AS period_key,
       MAX(d.date) AS date,
       SUBSTRING_INDEX(GROUP_CONCAT(d.open ORDER BY d.date SEPARATOR ','), ',', 1) AS open,
       MAX(d.high) AS high,
       MIN(d.low) AS low,
       SUBSTRING_INDEX(GROUP_CONCAT(d.close ORDER BY d.date DESC SEPARATOR ','), ',', 1) AS close,
       SUBSTRING_INDEX(GROUP_CONCAT(d.pre_close ORDER BY d.date SEPARATOR ','), ',', 1)
           AS pre_close,
       SUM(d.volume) AS volume,
       SUM(d.amount) AS amount
FROM daily_quote d
GROUP BY d.stock_code, DATE_FORMAT(d.date, '%Y-%m')
ON DUPLICATE KEY UPDATE
    date = VALUES(date),
    open = VALUES(open),
    high = VALUES(high),
    low = VALUES(low),
    close = VALUES(close),
    pre_close = VALUES(pre_close),
    volume = VALUES(volume),
    amount = VALUES(amount);

-- 季K：从月K聚合
INSERT INTO quarter_quote (stock_code, period_key, date, open, high, low, close, pre_close,
                           volume, amount)
SELECT m.stock_code,
       CONCAT(YEAR(m.date), '-Q', QUARTER(m.date)) AS period_key,
       MAX(m.date) AS date,
       SUBSTRING_INDEX(GROUP_CONCAT(m.open ORDER BY m.date SEPARATOR ','), ',', 1) AS open,
       MAX(m.high) AS high,
       MIN(m.low) AS low,
       SUBSTRING_INDEX(GROUP_CONCAT(m.close ORDER BY m.date DESC SEPARATOR ','), ',', 1) AS close,
       SUBSTRING_INDEX(GROUP_CONCAT(m.pre_close ORDER BY m.date SEPARATOR ','), ',', 1)
           AS pre_close,
       SUM(m.volume) AS volume,
       SUM(m.amount) AS amount
FROM month_quote m
GROUP BY m.stock_code, CONCAT(YEAR(m.date), '-Q', QUARTER(m.date))
ON DUPLICATE KEY UPDATE
    date = VALUES(date),
    open = VALUES(open),
    high = VALUES(high),
    low = VALUES(low),
    close = VALUES(close),
    pre_close = VALUES(pre_close),
    volume = VALUES(volume),
    amount = VALUES(amount);

-- 年K：从季K聚合
INSERT INTO year_quote (stock_code, period_key, date, open, high, low, close, pre_close,
                        volume, amount)
SELECT q.stock_code,
       DATE_FORMAT(q.date, '%Y') AS period_key,
       MAX(q.date) AS date,
       SUBSTRING_INDEX(GROUP_CONCAT(q.open ORDER BY q.date SEPARATOR ','), ',', 1) AS open,
       MAX(q.high) AS high,
       MIN(q.low) AS low,
       SUBSTRING_INDEX(GROUP_CONCAT(q.close ORDER BY q.date DESC SEPARATOR ','), ',', 1) AS close,
       SUBSTRING_INDEX(GROUP_CONCAT(q.pre_close ORDER BY q.date SEPARATOR ','), ',', 1)
           AS pre_close,
       SUM(q.volume) AS volume,
       SUM(q.amount) AS amount
FROM quarter_quote q
GROUP BY q.stock_code, DATE_FORMAT(q.date, '%Y')
ON DUPLICATE KEY UPDATE
    date = VALUES(date),
    open = VALUES(open),
    high = VALUES(high),
    low = VALUES(low),
    close = VALUES(close),
    pre_close = VALUES(pre_close),
    volume = VALUES(volume),
    amount = VALUES(amount);
