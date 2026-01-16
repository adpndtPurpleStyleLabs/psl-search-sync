package com.psl_search_sync;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticSearchConf {

    @Value("${ES_HOST}")
    private String ES_HOST;

    @Value("${ES_PORT}")
    private int ES_PORT;

    @Value("${ES_SCHEME}")
    private String ES_SCHEME;

    @Value("${ES_USER}")
    private String ES_USER;

    @Value("${ES_PASSWORD}")
    private String ES_PASSWORD;

    @Bean
    public RestHighLevelClient createElasticClient() {

        CredentialsProvider credentialsProvider =
                new BasicCredentialsProvider();

        credentialsProvider.setCredentials(
                AuthScope.ANY,
                new UsernamePasswordCredentials(ES_USER, ES_PASSWORD)
        );

        return new RestHighLevelClient(
                RestClient.builder(
                        new HttpHost(ES_HOST, ES_PORT, ES_SCHEME)
                ).setHttpClientConfigCallback(
                        httpClientBuilder ->
                                httpClientBuilder.setDefaultCredentialsProvider(
                                        credentialsProvider
                                )
                )
        );
    }

}
