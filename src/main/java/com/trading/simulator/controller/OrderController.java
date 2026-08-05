package com.trading.simulator.controller;

import com.trading.simulator.dto.OrderResponse;
import com.trading.simulator.dto.PlaceOrderRequest;
import com.trading.simulator.model.Order;
import com.trading.simulator.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Orders", description = "FIX 4.4 Order Management API")
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
    @Operation(
        summary     = "Place a new order",
        description = "Submits a NewOrderSingle FIX 4.4 message to the mock exchange. "
                    + "The exchange acknowledges immediately (NEW) and fills after ~2 seconds (FILLED).",
        responses   = {
            @ApiResponse(responseCode = "201", description = "Order placed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload")
        }
    )
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
    @Operation(
        summary   = "List all orders",
        description = "Returns all orders. Optionally filter by symbol.",
        responses = {
            @ApiResponse(responseCode = "200", description = "List of orders")
        }
    )
    public ResponseEntity<List<OrderResponse>> getAllOrders(
            @Parameter(description = "Filter by ticker symbol (e.g. AAPL)")
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
    @Operation(
        summary   = "Get order by ID",
        responses = {
            @ApiResponse(responseCode = "200", description = "Order found"),
            @ApiResponse(responseCode = "404", description = "Order not found")
        }
    )
    public ResponseEntity<OrderResponse> getOrder(
            @Parameter(description = "Internal order ID (UUID)")
            @PathVariable String orderId) {

        Order order = orderService.getOrder(orderId);
        return ResponseEntity.ok(OrderResponse.from(order));
    }

    // ──────────────────────────────────────────────
    //  DELETE /api/orders/{orderId} — cancel an order
    // ──────────────────────────────────────────────

    @DeleteMapping("/{orderId}")
    @Operation(
        summary     = "Cancel an order",
        description = "Sends an OrderCancelRequest FIX message. "
                    + "The exchange confirms cancellation via ExecutionReport.",
        responses   = {
            @ApiResponse(responseCode = "200", description = "Cancel request accepted"),
            @ApiResponse(responseCode = "400", description = "Order is in a terminal state"),
            @ApiResponse(responseCode = "404", description = "Order not found")
        }
    )
    public ResponseEntity<OrderResponse> cancelOrder(
            @Parameter(description = "Internal order ID to cancel")
            @PathVariable String orderId) {

        Order order = orderService.cancelOrder(orderId);
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
