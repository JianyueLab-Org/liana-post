package com.liana.post.facility;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan("com.liana.post.facility.mapper")
@EnableFeignClients(basePackages = "com.liana.post.facility.client")
public class FacilityApplication {
    public static void main(String[] args) {
        SpringApplication.run(FacilityApplication.class, args);
    }
}