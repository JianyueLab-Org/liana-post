package com.liana.post.postage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(basePackages = "com.liana.post.postage.client")
public class PostageApplication {

    public static void main(String[] args) {
        SpringApplication.run(PostageApplication.class, args);
    }
}
