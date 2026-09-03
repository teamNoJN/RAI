package com.rai.chat.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(ServiceUrlProperties.class)
public class ChatServiceConfig {

    @Bean
    public RestClient drugRestClient(ServiceUrlProperties properties) {
        return client(properties.drugUrl());
    }

    /** 규제 KB. 지금은 모놀리식 앱이 갖고 있고, ai-service 가 분리되면 URL 만 바뀐다. */
    @Bean
    public RestClient regulationRestClient(ServiceUrlProperties properties) {
        return client(properties.regulationUrl());
    }

    private RestClient client(String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(3));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}
