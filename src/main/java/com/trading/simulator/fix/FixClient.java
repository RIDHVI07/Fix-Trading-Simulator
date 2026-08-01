package com.trading.simulator.fix;

import com.trading.simulator.model.Order;
import com.trading.simulator.model.OrderSide;
import com.trading.simulator.model.OrderStatus;
import com.trading.simulator.service.OrderService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import quickfix.*;
import quickfix.field.*;
import quickfix.fix44.ExecutionReport;
import quickfix.fix44.NewOrderSingle;
import quickfix.fix44.OrderCancelRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * FIX 4.4 initiator (CLIENT side).
 * Connects to MockExchangeAcceptor, sends orders, and processes execution reports.
 *
 * @Lazy on OrderService breaks the circular dependency:
 *   FixClient → OrderService → FixClient
 */
@Component
public class FixClient implements Application {

    private static final Logger log = LoggerFactory.getLogger(FixClient.class);

    @Value("${fix.initiator.host:localhost}")
    private String exchangeHost;

    @Value("${fix.acceptor.port:9878}")
    private int exchangePort;

    private SocketInitiator initiator;
    private SessionID       sessionID;

    /** @Lazy prevents circular dependency with OrderService */
    private final OrderService orderService;

    @Autowired
    public FixClient(@Lazy OrderService orderService) {
        this.orderService = orderService;
    }

    // ──────────────────────────────────────────────
    //  Lifecycle
    // ──────────────────────────────────────────────

    @PostConstruct
    public void start() throws Exception {
        SessionSettings  settings       = buildInitiatorSettings();
        MessageStoreFactory storeFactory = new MemoryStoreFactory();
        LogFactory       logFactory     = new ScreenLogFactory(settings);
        MessageFactory   messageFactory = new DefaultMessageFactory();

        initiator = new SocketInitiator(this, storeFactory, settings, logFactory, messageFactory);
        initiator.start();
        log.info("FixClient initiator started — connecting to {}:{}", exchangeHost, exchangePort);
    }

    @PreDestroy
    public void stop() {
        if (initiator != null) {
            initiator.stop();
            log.info("FixClient initiator stopped");
        }
    }

    // ──────────────────────────────────────────────
    //  quickfix.Application interface
    // ──────────────────────────────────────────────

    @Override
    public void onCreate(SessionID sid) {
        this.sessionID = sid;
        log.debug("FIX session created: {}", sid);
    }

    @Override
    public void onLogon(SessionID sid) {
        this.sessionID = sid;
        log.info("FIX session logged on: {}", sid);
    }

    @Override
    public void onLogout(SessionID sid) {
        log.info("FIX session logged out: {}", sid);
    }

    @Override
    public void toAdmin(Message message, SessionID sid) {}

    @Override
    public void fromAdmin(Message message, SessionID sid) {}

    @Override
    public void toApp(Message message, SessionID sid) throws DoNotSend {
        log.debug("Sending FIX message: {}", message);
    }

    /**
     * Handle inbound application messages — primarily ExecutionReports.
     */
    @Override
    public void fromApp(Message message, SessionID sid)
            throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {

        String msgType = message.getHeader().getString(MsgType.FIELD);

        if (MsgType.EXECUTION_REPORT.equals(msgType)) {
            handleExecutionReport(message);
        } else {
            log.warn("FixClient received unhandled message type: {}", msgType);
        }
    }

    // ──────────────────────────────────────────────
    //  Outbound — send orders
    // ──────────────────────────────────────────────

    /**
     * Send FIX 4.4 NewOrderSingle to the exchange.
     */
    public void sendNewOrderSingle(Order order) {
        if (!isConnected()) {
            log.warn("FIX session not connected — order queued: {}", order.getClOrdId());
            return;
        }

        try {
            char sideChar = order.getSide() == OrderSide.BUY ? Side.BUY : Side.SELL;
            char typeChar = order.getType() == com.trading.simulator.model.OrderType.MARKET
                    ? OrdType.MARKET : OrdType.LIMIT;

            NewOrderSingle nos = new NewOrderSingle(
                    new ClOrdID(order.getClOrdId()),
                    new Side(sideChar),
                    new TransactTime(toDate(LocalDateTime.now())),
                    new OrdType(typeChar)
            );

            nos.set(new Symbol(order.getSymbol()));
            nos.set(new OrderQty(order.getQuantity()));

            if (order.getType() == com.trading.simulator.model.OrderType.LIMIT
                    && order.getPrice() != null) {
                nos.set(new Price(order.getPrice().doubleValue()));
            }

            Session.sendToTarget(nos, sessionID);
            log.info("Sent NewOrderSingle — clOrdId={}, symbol={}, side={}, qty={}",
                    order.getClOrdId(), order.getSymbol(), order.getSide(), order.getQuantity());

        } catch (SessionNotFound e) {
            log.error("FIX session not found for order: {}", order.getClOrdId(), e);
        }
    }

    /**
     * Send FIX 4.4 OrderCancelRequest to the exchange.
     */
    public void sendOrderCancelRequest(Order order) {
        if (!isConnected()) {
            log.warn("FIX session not connected — cancel not sent for: {}", order.getClOrdId());
            return;
        }

        try {
            char sideChar = order.getSide() == OrderSide.BUY ? Side.BUY : Side.SELL;

            OrderCancelRequest ocr = new OrderCancelRequest(
                    new OrigClOrdID(order.getClOrdId()),
                    new ClOrdID("CXLREQ-" + System.currentTimeMillis()),
                    new Symbol(order.getSymbol()),
                    new Side(sideChar),
                    new TransactTime(toDate(LocalDateTime.now()))
            );
            ocr.set(new OrderQty(order.getQuantity()));

            Session.sendToTarget(ocr, sessionID);
            log.info("Sent OrderCancelRequest — origClOrdId={}", order.getClOrdId());

        } catch (SessionNotFound e) {
            log.error("FIX session not found for cancel: {}", order.getClOrdId(), e);
        }
    }

    // ──────────────────────────────────────────────
    //  Inbound — handle execution reports
    // ──────────────────────────────────────────────

    private void handleExecutionReport(Message message) throws FieldNotFound {
        String clOrdId  = message.getString(ClOrdID.FIELD);
        char   ordStatus = message.getChar(OrdStatus.FIELD);
        int    cumQty   = (int) message.getDouble(CumQty.FIELD);
        double avgPx    = message.getDouble(AvgPx.FIELD);

        OrderStatus status = mapOrdStatus(ordStatus);

        log.info("Received ExecutionReport — clOrdId={}, ordStatus={}, cumQty={}, avgPx={}",
                clOrdId, ordStatus, cumQty, avgPx);

        orderService.updateOrderStatus(
                clOrdId, status, cumQty,
                avgPx > 0 ? BigDecimal.valueOf(avgPx) : null);
    }

    // ──────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────

    public boolean isConnected() {
        return sessionID != null
                && !initiator.getSessions().isEmpty()
                && initiator.isLoggedOn();
    }

    private OrderStatus mapOrdStatus(char qfjStatus) {
        return switch (qfjStatus) {
            case OrdStatus.NEW              -> OrderStatus.NEW;
            case OrdStatus.PARTIALLY_FILLED -> OrderStatus.PARTIALLY_FILLED;
            case OrdStatus.FILLED           -> OrderStatus.FILLED;
            case OrdStatus.CANCELLED        -> OrderStatus.CANCELLED;
            case OrdStatus.REJECTED         -> OrderStatus.REJECTED;
            default -> {
                log.warn("Unknown OrdStatus char: {}", qfjStatus);
                yield OrderStatus.NEW;
            }
        };
    }

    private Date toDate(LocalDateTime ldt) {
        return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
    }

    private SessionSettings buildInitiatorSettings() throws ConfigError {
        SessionSettings settings = new SessionSettings();

        Dictionary defaults = new Dictionary();
        defaults.setString("ConnectionType",    "initiator");
        defaults.setString("HeartBtInt",        "30");
        defaults.setString("ReconnectInterval", "5");
        defaults.setString("StartTime",         "00:00:00");
        defaults.setString("EndTime",           "00:00:00");
        settings.set(defaults);

        SessionID sid     = new SessionID("FIX.4.4", "CLIENT", "EXCHANGE");
        Dictionary session = new Dictionary();
        session.setString("BeginString",        "FIX.4.4");
        session.setString("SenderCompID",       "CLIENT");
        session.setString("TargetCompID",       "EXCHANGE");
        session.setString("SocketConnectHost",  exchangeHost);
        session.setString("SocketConnectPort",  String.valueOf(exchangePort));
        settings.set(sid, session);

        return settings;
    }
}
