package com.trading.simulator.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.simulator.dto.PlaceOrderRequest;
import com.trading.simulator.model.*;
import com.trading.simulator.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@DisplayName("OrderController Tests")
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    private Order sampleOrder;

    @BeforeEach
    void setUp() {
        sampleOrder = new Order(
                "test-order-id-001",
                "CLO-001",
                "AAPL",
                OrderSide.BUY,
                OrderType.LIMIT,
                100,
                BigDecimal.valueOf(150.00)
        );
        sampleOrder.setStatus(OrderStatus.PENDING);
    }

    // ──────────────────────────────────────────────
    //  POST /api/orders
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/orders should return 201 with created order")
    void placeOrderShouldReturn201() throws Exception {
        when(orderService.placeOrder(any(PlaceOrderRequest.class))).thenReturn(sampleOrder);

        PlaceOrderRequest request = buildLimitBuyRequest();

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value("test-order-id-001"))
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.side").value("BUY"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.quantity").value(100));
    }

    @Test
    @DisplayName("POST /api/orders should return 404 when service throws IllegalArgumentException")
    void placeOrderShouldReturn404OnIllegalArgument() throws Exception {
        when(orderService.placeOrder(any())).thenThrow(new IllegalArgumentException("Symbol is required"));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildLimitBuyRequest())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Symbol is required"));
    }

    // ──────────────────────────────────────────────
    //  GET /api/orders
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/orders should return list of orders")
    void getAllOrdersShouldReturnList() throws Exception {
        Order second = new Order("ORD-002", "CLO-002", "MSFT",
                OrderSide.SELL, OrderType.MARKET, 50, null);

        when(orderService.getAllOrders(null)).thenReturn(List.of(sampleOrder, second));

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$[1].symbol").value("MSFT"));
    }

    @Test
    @DisplayName("GET /api/orders?symbol=AAPL should filter by symbol")
    void getAllOrdersWithSymbolFilterShouldFilterResults() throws Exception {
        when(orderService.getAllOrders("AAPL")).thenReturn(List.of(sampleOrder));

        mockMvc.perform(get("/api/orders").param("symbol", "AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].symbol").value("AAPL"));

        verify(orderService).getAllOrders("AAPL");
    }

    @Test
    @DisplayName("GET /api/orders should return empty array when no orders")
    void getAllOrdersShouldReturnEmptyList() throws Exception {
        when(orderService.getAllOrders(null)).thenReturn(List.of());

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ──────────────────────────────────────────────
    //  GET /api/orders/{orderId}
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/orders/{id} should return order when found")
    void getOrderShouldReturnOrder() throws Exception {
        when(orderService.getOrder("test-order-id-001")).thenReturn(sampleOrder);

        mockMvc.perform(get("/api/orders/test-order-id-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("test-order-id-001"))
                .andExpect(jsonPath("$.symbol").value("AAPL"));
    }

    @Test
    @DisplayName("GET /api/orders/{id} should return 404 when not found")
    void getOrderShouldReturn404WhenNotFound() throws Exception {
        when(orderService.getOrder("missing")).thenThrow(
                new IllegalArgumentException("Order not found: missing"));

        mockMvc.perform(get("/api/orders/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", containsString("not found")));
    }

    // ──────────────────────────────────────────────
    //  DELETE /api/orders/{orderId}
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /api/orders/{id} should return 200 for valid cancel")
    void cancelOrderShouldReturn200() throws Exception {
        when(orderService.cancelOrder("test-order-id-001")).thenReturn(sampleOrder);

        mockMvc.perform(delete("/api/orders/test-order-id-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("test-order-id-001"));
    }

    @Test
    @DisplayName("DELETE /api/orders/{id} should return 400 for terminal state")
    void cancelOrderShouldReturn400ForTerminalState() throws Exception {
        when(orderService.cancelOrder(anyString())).thenThrow(
                new IllegalStateException("Cannot cancel order in status: FILLED"));

        mockMvc.perform(delete("/api/orders/test-order-id-001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("Cannot cancel")));
    }

    @Test
    @DisplayName("DELETE /api/orders/{id} should return 404 when order not found")
    void cancelOrderShouldReturn404WhenNotFound() throws Exception {
        when(orderService.cancelOrder("missing")).thenThrow(
                new IllegalArgumentException("Order not found: missing"));

        mockMvc.perform(delete("/api/orders/missing"))
                .andExpect(status().isNotFound());
    }

    // ──────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────

    private PlaceOrderRequest buildLimitBuyRequest() {
        PlaceOrderRequest req = new PlaceOrderRequest();
        req.setSymbol("AAPL");
        req.setSide(OrderSide.BUY);
        req.setType(OrderType.LIMIT);
        req.setQuantity(100);
        req.setPrice(BigDecimal.valueOf(150.00));
        return req;
    }
}
