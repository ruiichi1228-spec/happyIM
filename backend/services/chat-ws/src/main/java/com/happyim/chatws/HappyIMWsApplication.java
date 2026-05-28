package com.happyim.chatws;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableRabbit
@ComponentScan(basePackages = {"com.happyim.ws", "com.happyim.common"})
@MapperScan("com.happyim.common.mapper")
public class HappyIMWsApplication {
    public static void main(String[] args) {
        SpringApplication.run(HappyIMWsApplication.class, args);
    }
}
