package com.trading.simulator.repository;

import com.trading.simulator.model.Order;
import com.trading.simulator.model.OrderSide;
import com.trading.simulator.model.OrderStatus;
import com.trading.simulator.model.OrderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrderRepository Tests")
class OrderRepositoryTest {

    private OrderRepository repository;

    @BeforeEach
    void setUp() {
        repository = new OrderRepository();
    }

    // ──────────────────────────────────────────────
    //  save / findByOrderId
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Should save and retrieve order by orderId")
    void shouldSaveAndFindByOrderId() {
        Order order = buildOrder("ORD-001", "CLO-001", "AAPL");
        repository.save(order);

        Optional<Order> found = repository.findByOrderId("ORD-001");

        assertThat(found).isPresent();
        assertThat(found.get().getSymbol()).isEqualTo("AAPL");
    }

    @Test
    @DisplayName("Should return empty Optional for unknown orderId")
    void shouldReturnEmptyForUnknownOrderId() {
        Optional<Order> result = repository.findByOrderId("DOES-NOT-EXIST");
        assertThat(result).isEmpty();
    }

    // ──────────────────────────────────────────────
    //  findByClOrdId
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Should find order by FIX ClOrdID")
    void shouldFindByClOrdId() {
        Order order = buildOrder("ORD-002", "CLO-002", "MSFT");
        repository.save(order);

        Optional<Order> found = repository.findByClOrdId("CLO-002");

        assertThat(found).isPresent();
        assertThat(found.get().getOrderId()).isEqualTo("ORD-002");
    }

    @Test
    @DisplayName("Should return empty Optional for unknown clOrdId")
    void shouldReturnEmptyForUnknownClOrdId() {
        Optional<Order> result = repository.findByClOrdId("CLO-UNKNOWN");
        assertThat(result).isEmpty();
    }

    // ──────────────────────────────────────────────
    //  findAll
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Should return all saved orders")
    void shouldReturnAllOrders() {
        repository.save(buildOrder("ORD-003", "CLO-003", "AAPL"));
        repository.save(buildOrder("ORD-004", "CLO-004", "TSLA"));
        repository.save(buildOrder("ORD-005", "CLO-005", "GOOGL"));

        List<Order> all = repository.findAll();

        assertThat(all).hasSize(3);
    }

    @Test
    @DisplayName("Should return empty list when no orders saved")
    void shouldReturnEmptyList() {
        assertThat(repository.findAll()).isEmpty();
    }

    // ──────────────────────────────────────────────
    //  findBySymbol
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Should filter orders by symbol")
    void shouldFilterBySymbol() {
        repository.save(buildOrder("ORD-006", "CLO-006", "AAPL"));
        repository.save(buildOrder("ORD-007", "CLO-007", "AAPL"));
        repository.save(buildOrder("ORD-008", "CLO-008", "MSFT"));

        List<Order> aaplOrders = repository.findBySymbol("AAPL");

        assertThat(aaplOrders).hasSize(2);
        assertThat(aaplOrders).allMatch(o -> "AAPL".equals(o.getSymbol()));
    }

    @Test
    @DisplayName("findBySymbol should be case-insensitive")
    void findBySymbolShouldBeCaseInsensitive() {
        repository.save(buildOrder("ORD-009", "CLO-009", "AAPL"));

        List<Order> result = repository.findBySymbol("aapl");

        assertThat(result).hasSize(1);
    }

    // ──────────────────────────────────────────────
    //  deleteByOrderId
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Should remove order and both indexes on delete")
    void shouldDeleteOrderById() {
        Order order = buildOrder("ORD-010", "CLO-010", "AAPL");
        repository.save(order);

        repository.deleteByOrderId("ORD-010");

        assertThat(repository.findByOrderId("ORD-010")).isEmpty();
        assertThat(repository.findByClOrdId("CLO-010")).isEmpty();
        assertThat(repository.count()).isZero();
    }

    // ──────────────────────────────────────────────
    //  count
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("count() should reflect saved orders")
    void countShouldReflectSavedOrders() {
        assertThat(repository.count()).isZero();

        repository.save(buildOrder("ORD-011", "CLO-011", "AAPL"));
        assertThat(repository.count()).isEqualTo(1);

        repository.save(buildOrder("ORD-012", "CLO-012", "MSFT"));
        assertThat(repository.count()).isEqualTo(2);
    }

    // ──────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────

    private Order buildOrder(String orderId, String clOrdId, String symbol) {
        return new Order(
                orderId, clOrdId, symbol,
                OrderSide.BUY, OrderType.LIMIT,
                100, BigDecimal.valueOf(150.00)
        );
    }
}
