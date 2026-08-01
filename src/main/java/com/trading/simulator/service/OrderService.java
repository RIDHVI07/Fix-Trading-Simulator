package com.trading.simulator.service;

import com.trading.simulator.dto.PlaceOrderRequest;
import com.trading.simulator.fix.FixClient;
import com.trading.simulator.model.Order;
import com.trading.simulator.model.OrderStatus;
import com.trading.simulator.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Core order management service.
 *
 * NOTE: FixClient is injected @Lazy to break the circular dependency:
 *   OrderService → FixClient → OrderService
 * Spring resolves this by injecting a proxy on first use.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;

    @Lazy
    private final FixClient fixClient;

    @Autowired
    public OrderService(OrderRepository orderRepository,
                        @Lazy FixClient fixClient) {
        this.orderRepository = orderRepository;
        this.fixClient       = fixClient;
    }

    /**
     * Place a new order.
     * 1. Persists the order with PENDING status.
     * 2. Sends a NewOrderSingle FIX message via FixClient.
     * 3. The exchange will asynchronously send back ExecutionReports.
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

        fixClient.sendNewOrderSingle(order);

        return order;
    }

    /**
     * Cancel an existing order.
     * Sends an OrderCancelRequest FIX message.
     */
    public Order cancelOrder(String orderId) {
        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        if (order.getStatus() == OrderStatus.FILLED
                || order.getStatus() == OrderStatus.CANCELLED
                || order.getStatus() == OrderStatus.REJECTED) {
            throw new IllegalStateException(
                    "Cannot cancel order in status: " + order.getStatus());
        }

        fixClient.sendOrderCancelRequest(order);
        log.info("Cancel request sent for orderId={}", orderId);
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

    // ---------- Called by FixClient when ExecutionReports arrive ----------

    /**
     * Update order status based on incoming FIX ExecutionReport.
     */
    public void updateOrderStatus(String clOrdId, OrderStatus newStatus,
                                  int filledQty, BigDecimal avgFillPrice) {
        orderRepository.findByClOrdId(clOrdId).ifPresentOrElse(order -> {
            order.setStatus(newStatus);
            order.setFilledQty(filledQty);
            order.setAvgFillPrice(avgFillPrice);
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
            log.info("Order updated — clOrdId={}, status={}, filledQty={}",
                    clOrdId, newStatus, filledQty);
        }, () -> log.warn("Received ExecutionReport for unknown clOrdId={}", clOrdId));
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
