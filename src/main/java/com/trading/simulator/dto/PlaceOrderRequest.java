package com.trading.simulator.dto;

import com.trading.simulator.model.OrderSide;
import com.trading.simulator.model.OrderType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Request payload for placing a new order")
public class PlaceOrderRequest {

    @Schema(description = "Ticker symbol", example = "AAPL", required = true)
    private String symbol;

    @Schema(description = "BUY or SELL", example = "BUY", required = true)
    private OrderSide side;

    @Schema(description = "MARKET or LIMIT", example = "LIMIT", required = true)
    private OrderType type;

    @Schema(description = "Number of shares", example = "100", required = true)
    private int quantity;

    @Schema(description = "Limit price (required for LIMIT orders)", example = "150.50")
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
