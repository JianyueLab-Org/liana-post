package com.liana.post.syncer.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConfigurationPropertiesScan
@MapperScan("com.liana.post.syncer.repository")
@EnableFeignClients(basePackages = "com.liana.post.syncer.client")
public class SyncerConfiguration {
}