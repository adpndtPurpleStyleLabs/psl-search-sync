package com.psl_search_sync;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient embeddingWebClient() {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer ->
                        configurer.defaultCodecs()
                                .maxInMemorySize(10 * 1024 * 10024) // 10 MB
                )
                .build();
        return WebClient.builder()
                .baseUrl("http://141.94.137.133:6645")
                .exchangeStrategies(strategies)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
