-- ============================================================
-- 演示数据清理脚本（仅用于开发库 yc_securities）
-- 清理内容：做市机器人用户/账户/持仓/委托/成交、
--           机器人创建的 admin 演示账户、演示期间行情快照、自选股
-- 保留：admin 用户、watchlist 表结构、daily_quote 历史行情
-- 执行前请先停止应用，避免定时任务继续写入
-- 可重复执行
-- 执行：cmd /c "mysql -uroot -p --default-character-set=utf8mb4 < db\reset-demo-data.sql"
-- ============================================================

USE yc_securities;

-- 委托与成交（基准时间之后的全部属于测试/演示数据）
DELETE FROM sold_orders WHERE create_time >= '2026-10-21 13:20:00';
DELETE FROM orders WHERE create_time >= '2026-10-21 13:20:00';

-- 持仓与自选（基准初始状态为空）
DELETE FROM `position`;
DELETE FROM watchlist;

-- 资金账户与银行卡（基准之后创建的都是演示/测试账户）
DELETE FROM account WHERE create_time >= '2026-10-21 13:20:00';
DELETE FROM bank_card WHERE create_time >= '2026-10-21 13:20:00';

-- 做市机器人用户
DELETE FROM user WHERE username LIKE 'mm_bot_%';

-- 演示期间产生的行情快照（分时快照全部清空）
TRUNCATE TABLE minute_quote;
DELETE FROM daily_quote WHERE date >= '2026-10-21';
