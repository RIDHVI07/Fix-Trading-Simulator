package com.trading.simulator.service;

import com.trading.simulator.dto.PlaceOrderRequest;
import com.trading.simulator.model.Order;
import com.trading.simulator.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Core order management service.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;

    @Autowired
    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * Place a new order.
     * Persists the order with PENDING status — routing to the exchange
     * is added once the FIX session is in place.
     */
    public Order placeOrder(PlaceOrderRequest request) {
        validateRequest(request);

        String orderId  = UUID.randomUUID().toString();
        String clOrdId  = "CLO-" + System.currentTimeMillis();

        Order order = new Order(
                orderId, clOrdId,
                request.getSymbol().toUpperCase(),
                request.getSide(),
                request.getType(),
                request.getQuantity(),
                request.getPrice()
        );

        orderRepository.save(order);
        log.info("Order saved — orderId={}, clOrdId={}", orderId, clOrdId);

        return order;
    }

    /**
     * Get a single order by ID.
     */
    public Order getOrder(String orderId) {
        return orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
    }

    /**
     * Get all orders (optionally filtered by symbol).
     */
    public List<Order> getAllOrders(String symbol) {
        if (symbol != null && !symbol.isBlank()) {
            return orderRepository.findBySymbol(symbol);
        }
        return orderRepository.findAll();
    }

    // ---------- Validation ----------

    private void validateRequest(PlaceOrderRequest request) {
        if (request.getSymbol() == null || request.getSymbol().isBlank()) {
            throw new IllegalArgumentException("Symbol is required");
        }
        if (request.getSide() == null) {
            throw new IllegalArgumentException("Side (BUY/SELL) is required");
        }
        if (request.getType() == null) {
            throw new IllegalArgumentException("Order type (MARKET/LIMIT) is required");
        }
        if (request.getQuantity() <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than 0");
        }
        if (request.getType() == com.trading.simulator.model.OrderType.LIMIT
                && (request.getPrice() == null || request.getPrice().compareTo(BigDecimal.ZERO) <= 0)) {
            throw new IllegalArgumentException("Limit price is required and must be > 0");
        }
    }
}
