package com.psl_search_sync;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.typesense.api.Client;
import org.typesense.api.Configuration;
import org.typesense.resources.Node;

import java.time.Duration;
import java.util.Collections;

@org.springframework.context.annotation.Configuration
public class TypesenseConf {
    @Value("${TYPESENSE_API_KEY}")
    private String TYPESENSE_API_KEY;

    @Value("${TYPESENSE_HOST}")
    private String TYPESENSE_HOST;

    @Value("${TYPESENSE_PORT}")
    private int TYPESENSE_PORT;

    @Bean
    public Client createTypesenseClient() {
        return new Client(
                new Configuration(
                        Collections.singletonList(
                                new Node(
                                        "http",
                                        TYPESENSE_HOST,
                                        String.valueOf(TYPESENSE_PORT)
                                )
                        )
                        , Duration.ofSeconds(3), TYPESENSE_API_KEY
                )
        );
    }
}
