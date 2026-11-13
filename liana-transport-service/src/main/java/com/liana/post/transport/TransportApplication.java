package com.liana.post.transport;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.liana.post")
@MapperScan("com.liana.post.transport.mapper")
@EnableFeignClients(basePackages = "com.liana.post.transport.client")
@EnableScheduling
public class TransportApplication {
    public static void main(String[] args) {
        SpringApplication.run(TransportApplication.class, args);
    }
}
