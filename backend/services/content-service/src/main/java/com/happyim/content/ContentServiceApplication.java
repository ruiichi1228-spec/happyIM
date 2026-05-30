package com.happyim.content;

import com.happyim.contracts.feign.UserFeignClient;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {"com.happyim.content", "com.happyim.common"})
@MapperScan("com.happyim.common.mapper")
@EnableRabbit
@EnableFeignClients(clients = {UserFeignClient.class})
public class ContentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ContentServiceApplication.class, args);
    }
}
