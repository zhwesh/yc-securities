package com.zhwesh.engine.match;

import com.zhwesh.engine.model.EngineOrder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 单价格订单队列测试
 */
class OrderQueueTest {

    @Test
    void pushPopAndDeleteById() {
        OrderQueue queue = new OrderQueue();
        EngineOrder first = order(1L);
        EngineOrder second = order(2L);
        EngineOrder third = order(3L);

        queue.push(first);
        queue.push(second);
        queue.push(third);
        assertThat(queue.size()).isEqualTo(3);
        assertThat(ids(queue)).containsExactly(1L, 2L, 3L);
        assertThat(queue.peek()).isSameAs(first);

        assertThat(queue.deleteById(2L)).isSameAs(second);
        assertThat(queue.size()).isEqualTo(2);
        assertThat(ids(queue)).containsExactly(1L, 3L);

        assertThat(queue.pop()).isSameAs(first);
        assertThat(queue.pop()).isSameAs(third);
        assertThat(queue.pop()).isNull();
        assertThat(queue.isEmpty()).isTrue();
        assertThat(queue.deleteById(999L)).isNull();
    }

    @Test
    void rejectDuplicateOrderId() {
        OrderQueue queue = new OrderQueue();
        queue.push(order(1L));
        assertThatThrownBy(() -> queue.push(order(1L)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 创建测试委托单
     * @param id 委托单id
     * @return 委托单
     */
    private EngineOrder order(Long id) {
        return EngineOrder.builder().id(id).build();
    }

    /**
     * 获取订单id列表
     * @param queue 订单队列
     * @return 订单id列表
     */
    private List<Long> ids(OrderQueue queue) {
        List<Long> ids = new ArrayList<>();
        for (EngineOrder order : queue) {
            ids.add(order.getId());
        }
        return ids;
    }
}
