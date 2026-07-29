package com.trading.simulator.fix;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import quickfix.*;
import quickfix.field.*;
import quickfix.fix44.ExecutionReport;
import quickfix.fix44.NewOrderSingle;
import quickfix.fix44.OrderCancelRequest;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Simulates the exchange side using a FIX 4.4 SocketAcceptor.
 *
 * On receiving a NewOrderSingle it:
 *   1. Immediately sends back an ExecutionReport (OrdStatus=NEW)
 *   2. After a configurable delay, sends a FILLED ExecutionReport
 *
 * On receiving an OrderCancelRequest it sends back an ExecutionReport
 * with OrdStatus=CANCELLED.
 */
@Component
public class MockExchangeAcceptor implements Application {

    private static final Logger log = LoggerFactory.getLogger(MockExchangeAcceptor.class);

    @Value("${fix.acceptor.port:9878}")
    private int acceptorPort;

    @Value("${fix.fill.delay.seconds:2}")
    private int fillDelaySeconds;

    private SocketAcceptor acceptor;
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(2);

    // ──────────────────────────────────────────────
    //  Lifecycle
    // ──────────────────────────────────────────────

    @PostConstruct
    public void start() throws Exception {
        SessionSettings settings = buildAcceptorSettings();
        MessageStoreFactory  storeFactory   = new MemoryStoreFactory();
        LogFactory           logFactory     = new ScreenLogFactory(settings);
        MessageFactory       messageFactory = new DefaultMessageFactory();

        acceptor = new SocketAcceptor(this, storeFactory, settings, logFactory, messageFactory);
        acceptor.start();
        log.info("MockExchangeAcceptor started on port {}", acceptorPort);
    }

    @PreDestroy
    public void stop() {
        scheduler.shutdownNow();
        if (acceptor != null) {
            acceptor.stop();
            log.info("MockExchangeAcceptor stopped");
        }
    }

    // ──────────────────────────────────────────────
    //  quickfix.Application interface
    // ──────────────────────────────────────────────

    @Override
    public void onCreate(SessionID sessionID) {
        log.debug("Acceptor session created: {}", sessionID);
    }

    @Override
    public void onLogon(SessionID sessionID) {
        log.info("Client logged on to exchange: {}", sessionID);
    }

    @Override
    public void onLogout(SessionID sessionID) {
        log.info("Client logged off from exchange: {}", sessionID);
    }

    @Override
    public void toAdmin(Message message, SessionID sessionID) {}

    @Override
    public void fromAdmin(Message message, SessionID sessionID) {}

    @Override
    public void toApp(Message message, SessionID sessionID) throws DoNotSend {}

    @Override
    public void fromApp(Message message, SessionID sessionID)
            throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {

        String msgType = message.getHeader()
                .getString(MsgType.FIELD);

        if (MsgType.ORDER_SINGLE.equals(msgType)) {
            handleNewOrder(new NewOrderSingle(), message, sessionID);
        } else if (MsgType.ORDER_CANCEL_REQUEST.equals(msgType)) {
            handleCancelRequest(new OrderCancelRequest(), message, sessionID);
        } else {
            log.warn("Acceptor received unhandled message type: {}", msgType);
        }
    }

    // ──────────────────────────────────────────────
    //  Message Handlers
    // ──────────────────────────────────────────────

    private void handleNewOrder(NewOrderSingle nos, Message raw, SessionID sessionID)
            throws FieldNotFound {

        String clOrdId = raw.getString(ClOrdID.FIELD);
        String symbol  = raw.getString(Symbol.FIELD);
        char   side    = raw.getChar(Side.FIELD);
        int    qty     = (int) raw.getDouble(OrderQty.FIELD);
        char   ordType = raw.getChar(OrdType.FIELD);
        double price   = (ordType == OrdType.LIMIT)
                ? raw.getDouble(Price.FIELD) : 0.0;

        log.info("Exchange received NewOrderSingle — clOrdId={}, symbol={}, side={}, qty={}",
                clOrdId, symbol, side, qty);

        // 1. Acknowledge: send ExecutionReport(OrdStatus=NEW)
        sendExecutionReport(clOrdId, symbol, side, qty, 0,
                OrdStatus.NEW, ExecType.NEW, 0.0, sessionID);

        // 2. After delay: send ExecutionReport(OrdStatus=FILLED)
        double fillPrice = (ordType == OrdType.LIMIT) ? price : simulateMarketPrice(symbol);
        scheduler.schedule(() ->
                sendExecutionReport(clOrdId, symbol, side, qty, qty,
                        OrdStatus.FILLED, ExecType.TRADE, fillPrice, sessionID),
                fillDelaySeconds, TimeUnit.SECONDS);
    }

    private void handleCancelRequest(OrderCancelRequest ocr, Message raw, SessionID sessionID)
            throws FieldNotFound {

        String clOrdId     = raw.getString(ClOrdID.FIELD);
        String origClOrdId = raw.getString(OrigClOrdID.FIELD);
        String symbol      = raw.getString(Symbol.FIELD);
        char   side        = raw.getChar(Side.FIELD);
        int    qty         = (int) raw.getDouble(OrderQty.FIELD);

        log.info("Exchange received OrderCancelRequest — origClOrdId={}", origClOrdId);

        // Acknowledge cancellation immediately
        sendExecutionReport(origClOrdId, symbol, side, qty, 0,
                OrdStatus.CANCELLED, ExecType.CANCELED, 0.0, sessionID);
    }

    // ──────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────

    private void sendExecutionReport(String clOrdId, String symbol, char side,
                                     int orderQty, int cumQty, char ordStatus,
                                     char execType, double avgPx, SessionID sessionID) {
        try {
            ExecutionReport report = new ExecutionReport(
                    new OrderID(UUID.randomUUID().toString()),
                    new ExecID(UUID.randomUUID().toString()),
                    new ExecType(execType),
                    new OrdStatus(ordStatus),
                    new Side(side),
                    new LeavesQty(orderQty - cumQty),
                    new CumQty(cumQty),
                    new AvgPx(avgPx)
            );
            report.set(new ClOrdID(clOrdId));
            report.set(new Symbol(symbol));
            report.set(new OrderQty(orderQty));

            Session.sendToTarget(report, sessionID);
            log.info("Exchange sent ExecutionReport — clOrdId={}, ordStatus={}, cumQty={}",
                    clOrdId, ordStatus, cumQty);

        } catch (SessionNotFound e) {
            log.error("Could not send ExecutionReport — session not found: {}", sessionID, e);
        }
    }

    /**
     * Simulate a plausible market price for well-known symbols.
     * In a real system this would come from a market data feed.
     */
    private double simulateMarketPrice(String symbol) {
        return switch (symbol.toUpperCase()) {
            case "AAPL"  -> 178.25 + (Math.random() * 2 - 1);
            case "MSFT"  -> 415.10 + (Math.random() * 2 - 1);
            case "GOOGL" -> 172.50 + (Math.random() * 2 - 1);
            case "TSLA"  -> 175.80 + (Math.random() * 2 - 1);
            default      -> 100.00 + (Math.random() * 50);
        };
    }

    // ──────────────────────────────────────────────
    //  Session settings (programmatic — no .cfg file)
    // ──────────────────────────────────────────────

    private SessionSettings buildAcceptorSettings() throws ConfigError {
        SessionSettings settings = new SessionSettings();

        Dictionary defaults = new Dictionary();
        defaults.setString("ConnectionType",   "acceptor");
        defaults.setString("HeartBtInt",       "30");
        defaults.setString("SocketAcceptPort", String.valueOf(acceptorPort));
        defaults.setString("StartTime",        "00:00:00");
        defaults.setString("EndTime",          "00:00:00");
        defaults.setString("SenderCompID",     "EXCHANGE");
        defaults.setString("TargetCompID",     "CLIENT");
        defaults.setString("BeginString",      "FIX.4.4");
        settings.set(defaults);

        SessionID sessionID = new SessionID("FIX.4.4", "EXCHANGE", "CLIENT");
        Dictionary session  = new Dictionary();
        session.setString("BeginString",  "FIX.4.4");
        session.setString("SenderCompID", "EXCHANGE");
        session.setString("TargetCompID", "CLIENT");
        settings.set(sessionID, session);

        return settings;
    }
}
