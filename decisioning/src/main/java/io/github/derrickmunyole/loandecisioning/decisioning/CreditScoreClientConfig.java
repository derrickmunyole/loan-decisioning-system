package io.github.derrickmunyole.loandecisioning.decisioning;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
class CreditScoreClientConfig {

    @Bean
    WebClient creditScoreWebClient(@Value("${app.credit-score.base-url}") String baseUrl) {
        return WebClient.builder().baseUrl(baseUrl).build();
    }
}
