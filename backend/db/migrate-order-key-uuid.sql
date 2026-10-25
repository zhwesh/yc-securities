-- 将订单幂等键改为UUID字符串
ALTER TABLE orders
    MODIFY COLUMN order_key VARCHAR(36) NOT NULL COMMENT '幂等键';
