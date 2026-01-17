package com.psl_search_sync;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
class BulkEmbedResponse {
    private List<BulkEmbedResult> results;
}
