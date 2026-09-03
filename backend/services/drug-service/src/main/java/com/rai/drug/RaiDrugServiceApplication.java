package com.rai.drug;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** drug-service (:8082) — 제품(Drug) · 국가. */
@SpringBootApplication(scanBasePackages = {"com.rai.drug", "com.rai.common"})
public class RaiDrugServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RaiDrugServiceApplication.class, args);
    }
}
