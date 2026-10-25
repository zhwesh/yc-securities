-- ============================================================
-- 测试数据重置脚本（yc_securities_test）
-- 基准时间：2026-10-21 13:20:00（测试数据集初始状态）
-- 初始状态：除 user / daily_quote（历史行情）/ stock（基础数据）外，业务数据为空
-- 用途：删除基准时间之后测试产生的数据，恢复到初始状态
-- 执行前请先停止应用（行情快照定时任务会持续写入 minute_quote / daily_quote）
-- 执行：cmd /c "mysql -uroot -p --default-character-set=utf8mb4 < db\reset-test-data.sql"
-- ============================================================

USE yc_securities_test;

-- 委托单与成交记录
DELETE FROM orders WHERE create_time >= '2026-10-21 13:20:00';
DELETE FROM sold_orders WHERE create_time >= '2026-10-21 13:20:00';

-- 持仓（无时间字段，初始状态为空）
DELETE FROM `position`;

-- 自选股（初始状态为空）
DELETE FROM watchlist;

-- 资金账户与银行卡（初始状态为空，测试中新开的账户都会被清除）
DELETE FROM account WHERE create_time >= '2026-10-21 13:20:00';
DELETE FROM bank_card WHERE create_time >= '2026-10-21 13:20:00';

-- 行情快照（保留 daily_quote 历史，删除测试日及之后的当日快照）
DELETE FROM minute_quote WHERE trade_time >= '2026-10-21 13:20:00';
DELETE FROM daily_quote WHERE date >= '2026-10-21';

-- Redis 在线态清理（在命令行执行）：
-- redis-cli -a 123456 --scan --pattern "trade:online:*"
-- redis-cli -a 123456 --scan --pattern "sms:code:*"
-- 对每个 key 执行 DEL
