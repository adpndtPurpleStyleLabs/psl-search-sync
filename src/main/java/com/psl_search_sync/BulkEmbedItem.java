package com.psl_search_sync;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
class BulkEmbedItem {
    private String sku;
    private String text;
}
