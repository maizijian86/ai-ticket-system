package com.aiticket.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
@ComponentScan(basePackages = {"com.aiticket.user", "com.aiticket.common"})
public class AiTicketUserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiTicketUserServiceApplication.class, args);
    }
}
