package com.rai.chat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** chat-service (:8083) — 대화 세션 · 메시지. */
@SpringBootApplication(scanBasePackages = {"com.rai.chat", "com.rai.common"})
public class RaiChatServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RaiChatServiceApplication.class, args);
    }
}
