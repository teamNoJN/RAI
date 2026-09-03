package com.rai.drug.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(ServiceUrlProperties.class)
public class DrugServiceConfig {

    /**
     * chat-service 내부 호출용. 대시보드 배지 하나 때문에 화면 전체가 멈추면 안 되므로
     * 타임아웃을 짧게 둔다 (호출부에서 실패를 흡수한다).
     */
    @Bean
    public RestClient chatRestClient(ServiceUrlProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(3));
        return RestClient.builder()
                .baseUrl(properties.chatUrl())
                .requestFactory(factory)
                .build();
    }
}
