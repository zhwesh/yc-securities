package com.zhwesh.engine.match;

import com.zhwesh.engine.model.EngineOrder;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * 单价格订单队列
 */
public final class OrderQueue implements Iterable<EngineOrder> {

    private final Node head = new Node(null);
    private final Map<Long, Node> nodeIndex = new HashMap<>();
    private int size;

    public OrderQueue() {
        head.prev = head;
        head.next = head;
    }

    /**
     * 追加订单
     * @param order 委托单
     */
    public void push(EngineOrder order) {
        if (order == null || order.getId() == null) {
            throw new IllegalArgumentException("委托单和委托单id不能为空");
        }
        if (nodeIndex.containsKey(order.getId())) {
            throw new IllegalArgumentException("委托单id重复：" + order.getId());
        }
        Node node = new Node(order);
        Node tail = head.prev;
        node.prev = tail;
        node.next = head;
        tail.next = node;
        head.prev = node;
        nodeIndex.put(order.getId(), node);
        size++;
    }

    /**
     * 取出队首订单
     * @return 队首订单，队列为空时返回null
     */
    public EngineOrder pop() {
        if (size == 0) {
            return null;
        }
        Node first = head.next;
        unlink(first);
        nodeIndex.remove(first.order.getId());
        return first.order;
    }

    /**
     * 查看队首订单
     * @return 队首订单，队列为空时返回null
     */
    public EngineOrder peek() {
        return size == 0 ? null : head.next.order;
    }

    /**
     * 按委托单id删除订单
     * @param orderId 委托单id
     * @return 被删除的订单，订单不存在时返回null
     */
    public EngineOrder deleteById(Long orderId) {
        Node node = nodeIndex.remove(orderId);
        if (node == null) {
            return null;
        }
        unlink(node);
        return node.order;
    }

    /**
     * 查询订单数量
     * @return 订单数量
     */
    public int size() {
        return size;
    }

    /**
     * 判断队列是否为空
     * @return 队列为空返回true
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * 清空订单队列
     */
    public void clear() {
        head.prev = head;
        head.next = head;
        nodeIndex.clear();
        size = 0;
    }

    /**
     * 获取订单迭代器
     * @return 订单迭代器
     */
    @Override
    public Iterator<EngineOrder> iterator() {
        return new Iterator<>() {
            private Node current = head.next;

            @Override
            public boolean hasNext() {
                return current != head;
            }

            @Override
            public EngineOrder next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                EngineOrder order = current.order;
                current = current.next;
                return order;
            }
        };
    }

    private void unlink(Node node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
        node.prev = null;
        node.next = null;
        size--;
    }

    private static final class Node {
        private final EngineOrder order;
        private Node prev;
        private Node next;

        private Node(EngineOrder order) {
            this.order = order;
        }
    }
}
