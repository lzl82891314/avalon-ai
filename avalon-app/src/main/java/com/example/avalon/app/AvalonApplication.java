package com.example.avalon.app;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.example.avalon")
@EnableJpaRepositories(basePackages = "com.example.avalon.persistence.repository")
@EntityScan(basePackages = "com.example.avalon.persistence.entity")
public class AvalonApplication {
    public static void main(String[] args) {
        org.springframework.boot.SpringApplication.run(AvalonApplication.class, AvalonLaunchMode.resolve(args).launchArgs(args));
    }
}
