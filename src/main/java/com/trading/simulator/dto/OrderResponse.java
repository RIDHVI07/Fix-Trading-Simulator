package com.trading.simulator.dto;

import com.trading.simulator.model.Order;
import com.trading.simulator.model.OrderSide;
import com.trading.simulator.model.OrderStatus;
import com.trading.simulator.model.OrderType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class OrderResponse {

    private String orderId;
    private String symbol;
    private OrderSide side;
    private OrderType type;
    private int quantity;
    private int filledQty;
    private BigDecimal price;
    private BigDecimal avgFillPrice;
    private OrderStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static OrderResponse from(Order order) {
        OrderResponse r = new OrderResponse();
        r.orderId      = order.getOrderId();
        r.symbol       = order.getSymbol();
        r.side         = order.getSide();
        r.type         = order.getType();
        r.quantity     = order.getQuantity();
        r.filledQty    = order.getFilledQty();
        r.price        = order.getPrice();
        r.avgFillPrice = order.getAvgFillPrice();
        r.status       = order.getStatus();
        r.createdAt    = order.getCreatedAt();
        r.updatedAt    = order.getUpdatedAt();
        return r;
    }

    public String getOrderId()           { return orderId; }
    public String getSymbol()            { return symbol; }
    public OrderSide getSide()           { return side; }
    public OrderType getType()           { return type; }
    public int getQuantity()             { return quantity; }
    public int getFilledQty()            { return filledQty; }
    public BigDecimal getPrice()         { return price; }
    public BigDecimal getAvgFillPrice()  { return avgFillPrice; }
    public OrderStatus getStatus()       { return status; }
    public LocalDateTime getCreatedAt()  { return createdAt; }
    public LocalDateTime getUpdatedAt()  { return updatedAt; }
}
