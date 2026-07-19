package com.trading.simulator.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Order {

    private String orderId;
    private String clOrdId;        // FIX ClOrdID — sent in the FIX message
    private String symbol;
    private OrderSide side;
    private OrderType type;
    private int quantity;
    private int filledQty;
    private BigDecimal price;      // null for MARKET orders
    private BigDecimal avgFillPrice;
    private OrderStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Order() {}

    public Order(String orderId, String clOrdId, String symbol,
                 OrderSide side, OrderType type,
                 int quantity, BigDecimal price) {
        this.orderId      = orderId;
        this.clOrdId      = clOrdId;
        this.symbol       = symbol;
        this.side         = side;
        this.type         = type;
        this.quantity     = quantity;
        this.filledQty    = 0;
        this.price        = price;
        this.status       = OrderStatus.PENDING;
        this.createdAt    = LocalDateTime.now();
        this.updatedAt    = LocalDateTime.now();
    }

    // ---- Getters ----

    public String getOrderId()           { return orderId; }
    public String getClOrdId()           { return clOrdId; }
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

    // ---- Setters ----

    public void setOrderId(String orderId)                   { this.orderId = orderId; }
    public void setClOrdId(String clOrdId)                   { this.clOrdId = clOrdId; }
    public void setSymbol(String symbol)                     { this.symbol = symbol; }
    public void setSide(OrderSide side)                      { this.side = side; }
    public void setType(OrderType type)                      { this.type = type; }
    public void setQuantity(int quantity)                    { this.quantity = quantity; }
    public void setFilledQty(int filledQty)                  { this.filledQty = filledQty; }
    public void setPrice(BigDecimal price)                   { this.price = price; }
    public void setAvgFillPrice(BigDecimal avgFillPrice)     { this.avgFillPrice = avgFillPrice; }
    public void setStatus(OrderStatus status)                { this.status = status; }
    public void setCreatedAt(LocalDateTime createdAt)        { this.createdAt = createdAt; }
    public void setUpdatedAt(LocalDateTime updatedAt)        { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return "Order{orderId='" + orderId + "', symbol='" + symbol +
               "', side=" + side + ", type=" + type +
               ", qty=" + quantity + ", status=" + status + "}";
    }
}
