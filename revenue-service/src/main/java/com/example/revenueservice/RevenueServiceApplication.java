package com.example.revenueservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SpringBootApplication
public class RevenueServiceApplication {

    private static final Logger logger = LoggerFactory.getLogger(RevenueServiceApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(RevenueServiceApplication.class, args);
        logger.info("[REVENUE_APPLICATION_START] RevenueServiceApplication started");
    }

}
