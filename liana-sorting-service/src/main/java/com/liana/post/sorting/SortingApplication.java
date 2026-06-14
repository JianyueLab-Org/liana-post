package com.liana.post.sorting;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@MapperScan("com.liana.post.sorting.mapper")
@EnableFeignClients(basePackages = "com.liana.post.sorting.client")
public class SortingApplication {
    public static void main(String[] args) {
        SpringApplication.run(SortingApplication.class, args);
    }
}
