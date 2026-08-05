package com.trading.simulator.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI fixTradingSimulatorOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("FIX Trading Simulator API")
                        .description(
                            "FIX 4.4 Trading Simulator built with QuickFIX/J and Spring Boot. "
                          + "Submit orders via REST — they are routed to a mock exchange over "
                          + "FIX 4.4 protocol. Execution reports are processed asynchronously "
                          + "and order state is updated in real time.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Ridhvi Kulshrestha")
                                .email("ridhvikul07@gmail.com")));
    }
}
