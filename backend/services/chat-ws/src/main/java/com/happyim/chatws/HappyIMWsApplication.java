package com.happyim.chatws;

import com.happyim.common.security.JwtUtil;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.web.socket.config.annotation.EnableWebSocket;

@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    DataSourceTransactionManagerAutoConfiguration.class
})
@EnableRabbit
@EnableWebSocket
@ComponentScan(basePackages = "com.happyim.chatws")
@Import(JwtUtil.class)
public class HappyIMWsApplication {
    public static void main(String[] args) {
        SpringApplication.run(HappyIMWsApplication.class, args);
    }
}
