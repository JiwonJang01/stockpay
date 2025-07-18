package com.project.stockpay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@ComponentScan(basePackages = {
    "com.project.stockpay"
})
public class StockpayApplication {

  public static void main(String[] args) {

    SpringApplication.run(StockpayApplication.class, args);
  }

}
