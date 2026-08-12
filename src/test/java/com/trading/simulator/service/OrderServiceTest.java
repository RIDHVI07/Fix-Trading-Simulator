package com.trading.simulator.service;

import com.trading.simulator.dto.PlaceOrderRequest;
import com.trading.simulator.fix.FixClient;
import com.trading.simulator.model.*;
import com.trading.simulator.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService Tests")
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private FixClient fixClient;

    @InjectMocks
    private OrderService orderService;

    private PlaceOrderRequest limitBuyRequest;

    @BeforeEach
    void setUp() {
        limitBuyRequest = new PlaceOrderRequest();
        limitBuyRequest.setSymbol("AAPL");
        limitBuyRequest.setSide(OrderSide.BUY);
        limitBuyRequest.setType(OrderType.LIMIT);
        limitBuyRequest.setQuantity(100);
        limitBuyRequest.setPrice(BigDecimal.valueOf(150.00));

        // Make repository.save() return the order it receives.
        // lenient() because only the tests that exercise a save path consume
        // this stub, and MockitoExtension defaults to strict stubbing.
        lenient().when(orderRepository.save(any(Order.class)))
                 .thenAnswer(inv -> inv.getArgument(0));
    }

    // ──────────────────────────────────────────────
    //  placeOrder
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("placeOrder should persist order and send FIX message")
    void placeOrderShouldPersistAndSendFix() {
        Order result = orderService.placeOrder(limitBuyRequest);

        // Order should be saved
        verify(orderRepository, times(1)).save(any(Order.class));

        // FIX message should be sent
        verify(fixClient, times(1)).sendNewOrderSingle(any(Order.class));

        // Verify initial state
        assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(result.getSymbol()).isEqualTo("AAPL");
        assertThat(result.getSide()).isEqualTo(OrderSide.BUY);
        assertThat(result.getQuantity()).isEqualTo(100);
    }

    @Test
    @DisplayName("placeOrder should uppercases symbol")
    void placeOrderShouldUppercaseSymbol() {
        limitBuyRequest.setSymbol("aapl");

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        orderService.placeOrder(limitBuyRequest);
        verify(orderRepository).save(captor.capture());

        assertThat(captor.getValue().getSymbol()).isEqualTo("AAPL");
    }

    @Test
    @DisplayName("placeOrder should fail when symbol is blank")
    void placeOrderShouldFailWhenSymbolBlank() {
        limitBuyRequest.setSymbol("");

        assertThatThrownBy(() -> orderService.placeOrder(limitBuyRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Symbol is required");

        verifyNoInteractions(fixClient);
    }

    @Test
    @DisplayName("placeOrder should fail when quantity is zero")
    void placeOrderShouldFailWhenQuantityZero() {
        limitBuyRequest.setQuantity(0);

        assertThatThrownBy(() -> orderService.placeOrder(limitBuyRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Quantity");

        verifyNoInteractions(fixClient);
    }

    @Test
    @DisplayName("placeOrder should fail for LIMIT order without price")
    void placeOrderShouldFailForLimitWithoutPrice() {
        limitBuyRequest.setType(OrderType.LIMIT);
        limitBuyRequest.setPrice(null);

        assertThatThrownBy(() -> orderService.placeOrder(limitBuyRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Limit price");

        verifyNoInteractions(fixClient);
    }

    @Test
    @DisplayName("placeOrder should allow MARKET order without price")
    void placeOrderShouldAllowMarketWithoutPrice() {
        limitBuyRequest.setType(OrderType.MARKET);
        limitBuyRequest.setPrice(null);

        Order result = orderService.placeOrder(limitBuyRequest);

        assertThat(result).isNotNull();
        verify(fixClient).sendNewOrderSingle(any(Order.class));
    }

    // ──────────────────────────────────────────────
    //  cancelOrder
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("cancelOrder should send FIX cancel for a NEW order")
    void cancelOrderShouldSendFix() {
        Order existingOrder = buildOrder(OrderStatus.NEW);
        when(orderRepository.findByOrderId("ORD-1")).thenReturn(Optional.of(existingOrder));

        orderService.cancelOrder("ORD-1");

        verify(fixClient).sendOrderCancelRequest(existingOrder);
    }

    @Test
    @DisplayName("cancelOrder should throw when order not found")
    void cancelOrderShouldThrowIfNotFound() {
        when(orderRepository.findByOrderId("MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.cancelOrder("MISSING"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Order not found");

        verifyNoInteractions(fixClient);
    }

    @Test
    @DisplayName("cancelOrder should throw for already FILLED order")
    void cancelOrderShouldThrowForFilledOrder() {
        Order filledOrder = buildOrder(OrderStatus.FILLED);
        when(orderRepository.findByOrderId("ORD-2")).thenReturn(Optional.of(filledOrder));

        assertThatThrownBy(() -> orderService.cancelOrder("ORD-2"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot cancel");

        verifyNoInteractions(fixClient);
    }

    @Test
    @DisplayName("cancelOrder should throw for already CANCELLED order")
    void cancelOrderShouldThrowForCancelledOrder() {
        Order cancelled = buildOrder(OrderStatus.CANCELLED);
        when(orderRepository.findByOrderId("ORD-3")).thenReturn(Optional.of(cancelled));

        assertThatThrownBy(() -> orderService.cancelOrder("ORD-3"))
                .isInstanceOf(IllegalStateException.class);
    }

    // ──────────────────────────────────────────────
    //  getOrder
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("getOrder should return order by ID")
    void getOrderShouldReturnOrder() {
        Order order = buildOrder(OrderStatus.NEW);
        when(orderRepository.findByOrderId("ORD-1")).thenReturn(Optional.of(order));

        Order result = orderService.getOrder("ORD-1");

        assertThat(result).isSameAs(order);
    }

    @Test
    @DisplayName("getOrder should throw when not found")
    void getOrderShouldThrowIfNotFound() {
        when(orderRepository.findByOrderId("X")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder("X"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ──────────────────────────────────────────────
    //  getAllOrders
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("getAllOrders with no filter should call findAll")
    void getAllOrdersShouldCallFindAll() {
        when(orderRepository.findAll()).thenReturn(List.of());

        orderService.getAllOrders(null);

        verify(orderRepository).findAll();
        verify(orderRepository, never()).findBySymbol(any());
    }

    @Test
    @DisplayName("getAllOrders with symbol filter should call findBySymbol")
    void getAllOrdersWithSymbolShouldCallFindBySymbol() {
        when(orderRepository.findBySymbol("AAPL")).thenReturn(List.of());

        orderService.getAllOrders("AAPL");

        verify(orderRepository).findBySymbol("AAPL");
        verify(orderRepository, never()).findAll();
    }

    // ──────────────────────────────────────────────
    //  updateOrderStatus
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("updateOrderStatus should update and re-save the order")
    void updateOrderStatusShouldPersistChanges() {
        Order order = buildOrder(OrderStatus.NEW);
        when(orderRepository.findByClOrdId("CLO-1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        orderService.updateOrderStatus("CLO-1", OrderStatus.FILLED, 100, BigDecimal.valueOf(150.25));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(order.getFilledQty()).isEqualTo(100);
        assertThat(order.getAvgFillPrice()).isEqualTo(BigDecimal.valueOf(150.25));
        verify(orderRepository).save(order);
    }

    @Test
    @DisplayName("updateOrderStatus should not throw for unknown clOrdId — just log warning")
    void updateOrderStatusShouldNotThrowForUnknownClOrdId() {
        when(orderRepository.findByClOrdId("UNKNOWN")).thenReturn(Optional.empty());

        // Should not throw
        orderService.updateOrderStatus("UNKNOWN", OrderStatus.FILLED, 100, BigDecimal.valueOf(99.0));

        verify(orderRepository, never()).save(any());
    }

    // ──────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────

    private Order buildOrder(OrderStatus status) {
        Order order = new Order("ORD-1", "CLO-1", "AAPL",
                OrderSide.BUY, OrderType.LIMIT, 100, BigDecimal.valueOf(150.00));
        order.setStatus(status);
        return order;
    }
}
