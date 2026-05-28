package com.happyim.api;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.happyim.api", "com.happyim.common"})
@MapperScan("com.happyim.common.mapper")
public class HappyIMApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(HappyIMApiApplication.class, args);
    }
}
