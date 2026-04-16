package com.aiticket.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.aiticket.ai", "com.aiticket.common"})
public class AiTicketServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiTicketServiceApplication.class, args);
    }
}
