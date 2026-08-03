package com.trading.simulator;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    // Use a different FIX port for tests so CI doesn't conflict
    "fix.acceptor.port=19878",
    "fix.fill.delay.seconds=1"
})
class TradingSimulatorApplicationTests {

    @Test
    void contextLoads() {
        // Verifies the entire Spring context assembles without errors.
        // If there are bean wiring issues or circular dependencies they surface here.
    }
}
