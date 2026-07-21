package com.trading.simulator.repository;

import com.trading.simulator.model.Order;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Thread-safe in-memory order store.
 * Uses two indexes:
 *   orderId  → Order  (primary key for REST API)
 *   clOrdId  → Order  (FIX ClOrdID lookup when execution reports arrive)
 */
@Repository
public class OrderRepository {

    private final Map<String, Order> byOrderId  = new ConcurrentHashMap<>();
    private final Map<String, Order> byClOrdId  = new ConcurrentHashMap<>();

    public Order save(Order order) {
        byOrderId.put(order.getOrderId(), order);
        byClOrdId.put(order.getClOrdId(), order);
        return order;
    }

    public Optional<Order> findByOrderId(String orderId) {
        return Optional.ofNullable(byOrderId.get(orderId));
    }

    public Optional<Order> findByClOrdId(String clOrdId) {
        return Optional.ofNullable(byClOrdId.get(clOrdId));
    }

    public List<Order> findAll() {
        return new ArrayList<>(byOrderId.values());
    }

    public List<Order> findBySymbol(String symbol) {
        return byOrderId.values().stream()
                .filter(o -> symbol.equalsIgnoreCase(o.getSymbol()))
                .collect(Collectors.toList());
    }

    public void deleteByOrderId(String orderId) {
        Order order = byOrderId.remove(orderId);
        if (order != null) {
            byClOrdId.remove(order.getClOrdId());
        }
    }

    public int count() {
        return byOrderId.size();
    }

    /** Clears all orders — used in tests. */
    public void clear() {
        byOrderId.clear();
        byClOrdId.clear();
    }
}
