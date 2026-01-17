package com.psl_search_sync;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class EmbiddingApiClient {

    private final WebClient webClient;

    public EmbiddingApiClient(WebClient embeddingWebClient) {
        this.webClient = embeddingWebClient;
    }

    public float[] embed(String text) {

        EmbeddingRequestDto request =
                new EmbeddingRequestDto(text);

        EmbeddingResponseDto response = webClient
                .post()
                .uri("/embed")
                .bodyValue(request)
                .retrieve()
                .onStatus(
                        status -> status.is4xxClientError() || status.is5xxServerError(),
                        resp -> resp.bodyToMono(String.class)
                                .map(msg -> new RuntimeException(
                                        "Embedding API error: " + msg))
                )
                .bodyToMono(EmbeddingResponseDto.class)
                .block();

        if (response == null || response.getEmbedding() == null) {
            return new float[0];
        }

        // Convert List<Double> → float[]
        float[] vector = new float[response.getEmbedding().size()];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = response.getEmbedding().get(i).floatValue();
        }

        return vector;
    }
}
