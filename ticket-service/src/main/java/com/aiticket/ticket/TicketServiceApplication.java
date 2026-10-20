package com.aiticket.ticket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

// @EnableFeignClients 已移除：它当初只为唯一那个 Feign 客户端 AiServiceClient 存在，
// 而那个客户端注入之后从未被调用过（它声明的 6 个 AI 端点在后端根本不存在）。
// 随之一起去掉的还有 spring-cloud-starter-openfeign 依赖。
@SpringBootApplication
@EnableJpaAuditing
@ComponentScan(basePackages = {"com.aiticket.ticket", "com.aiticket.common"})
public class TicketServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketServiceApplication.class, args);
    }
}
