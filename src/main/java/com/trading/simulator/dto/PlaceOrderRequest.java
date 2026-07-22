package com.trading.simulator.dto;

import com.trading.simulator.model.OrderSide;
import com.trading.simulator.model.OrderType;

import java.math.BigDecimal;

public class PlaceOrderRequest {

    private String symbol;
    private OrderSide side;
    private OrderType type;
    private int quantity;
    private BigDecimal price;

    public PlaceOrderRequest() {}

    public String getSymbol()       { return symbol; }
    public OrderSide getSide()      { return side; }
    public OrderType getType()      { return type; }
    public int getQuantity()        { return quantity; }
    public BigDecimal getPrice()    { return price; }

    public void setSymbol(String symbol)       { this.symbol = symbol; }
    public void setSide(OrderSide side)        { this.side = side; }
    public void setType(OrderType type)        { this.type = type; }
    public void setQuantity(int quantity)      { this.quantity = quantity; }
    public void setPrice(BigDecimal price)     { this.price = price; }
}
