package com.psl_search_sync;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
class EmbeddingResponseDto {
    private String embeddingText;
    private List<Double> embedding;
}

