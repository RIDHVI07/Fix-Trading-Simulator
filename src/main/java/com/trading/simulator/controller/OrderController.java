package com.trading.simulator.controller;

import com.trading.simulator.dto.OrderResponse;
import com.trading.simulator.dto.PlaceOrderRequest;
import com.trading.simulator.model.Order;
import com.trading.simulator.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    @Autowired
    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // ──────────────────────────────────────────────
    //  POST /api/orders — place a new order
    // ──────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(
            @RequestBody PlaceOrderRequest request) {

        Order order = orderService.placeOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(OrderResponse.from(order));
    }

    // ──────────────────────────────────────────────
    //  GET /api/orders — list all orders
    // ──────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getAllOrders(
            @RequestParam(required = false) String symbol) {

        List<OrderResponse> orders = orderService.getAllOrders(symbol)
                .stream()
                .map(OrderResponse::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(orders);
    }

    // ──────────────────────────────────────────────
    //  GET /api/orders/{orderId} — get single order
    // ──────────────────────────────────────────────

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(
            @PathVariable String orderId) {

        Order order = orderService.getOrder(orderId);
        return ResponseEntity.ok(OrderResponse.from(order));
    }

    // ──────────────────────────────────────────────
    //  Exception handling
    // ──────────────────────────────────────────────

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorBody> handleNotFound(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorBody(ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorBody> handleBadState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorBody(ex.getMessage()));
    }

    record ErrorBody(String error) {}
}
