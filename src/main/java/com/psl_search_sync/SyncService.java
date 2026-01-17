package com.psl_search_sync;

import org.apache.commons.codec.language.DoubleMetaphone;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.typesense.api.Client;
import org.typesense.api.Configuration;
import org.typesense.model.ImportDocumentsParameters;
import org.typesense.resources.Node;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class SyncService {
    @Autowired
    private RestHighLevelClient elasticClient;
    @Autowired
    private Client typeSenseClient;
    @Value("${ES_INDEX}")
    private String ES_INDEX;
    @Value("${BATCH_SIZE}")
    private int BATCH_SIZE;
    @Value("${TYPESENSE_COLLECTION}")
    private String TYPESENSE_COLLECTION;
    private static final DoubleMetaphone DM = new DoubleMetaphone();


    public static final Set<String> predefinedColor = Set.of(
            "magenta", "pink", "rose gold", "yellow", "midnight blue", "turquoise",
            "multi", "gold", "red", "fuchsia", "mint", "mustard", "white", "coral",
            "blush pink", "ivory", "golden", "copper", "powder blue", "black & white",
            "colourless", "burgundy", "green", "lime", "black", "peach", "violet",
            "brown", "olive green", "grey", "bronze", "beige", "orange", "blue",
            "cobalt blue", "purple", "silver", "nude", "maroon", "mauve", "wine"
    );


    private static final Set<String> STOP_WORDS = Set.of(
            "for", "with", "and", "or", "the", "a", "an", "of", "in", "on",
            "to", "by", "from", "at", "is", "are", "this", "that"
    );

    public boolean isRunning = false;

    public void startFullSync() throws Exception {
        if (!isRunning) {
            migrate(elasticClient, typeSenseClient);
            System.out.println("✅ Migration completed successfully");
            isRunning = false;
        }
    }


    private void migrate(RestHighLevelClient esClient, Client typesenseClient) throws Exception {
        SearchRequest searchRequest = new SearchRequest(ES_INDEX);
        searchRequest.scroll(TimeValue.timeValueMinutes(2));

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
                .query(QueryBuilders.matchAllQuery())
                .size(BATCH_SIZE)
                .fetchSource(
                        new String[]{
                                "sku",
                                "type_id",
                                "name",
                                "short_description",
                                "color.label_lower",
                                "category_product.label",
                                "created_at",
                                "id",
                                "by_gender.url_key",
                                "meta_title",
                                "meta_description",
                                "image",
                                "small_image",
                                "thumbnail",
                                "url_key",
                                "price",
                                "has_square_images",
                                "price_by_currency",
                                "price_for_other",
                                "stock",
                                "configurable_options",
                                "special_price",
                                "special_price_by_currency",
                                "special_price_for_other",
                                "is_rts",
                                "special_price_start_date",
                                "special_price_end_date",
                                "ship_in_days",
                                "order_qty",
                                "discount",
                                "discount_us",
                                "category",
                                "configurable_children",
                                "icon_html",
                                "ship_in_info",
                                "is_rts",
                                "disable_na",
                                "price_on_request",
                                "discount_row"
                        },
                        null
                );


        searchRequest.source(sourceBuilder);

        SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);

        String scrollId = response.getScrollId();
        SearchHit[] hits = response.getHits().getHits();

        while (hits != null && hits.length > 0) {

            List<Map<String, Object>> batch = new ArrayList<>();

            for (SearchHit hit : hits) {
                Map<String, Object> src = hit.getSourceAsMap();
                Map<String, Object> doc = new HashMap<>();
                String type = (String) src.get("type_id");

                if (type.equals("giftcards")) continue;

                String sku = ((String) src.get("sku")).toLowerCase();
                try {

                    doc.put("id", src.get("id"));
                    doc.put("sku", sku);
                    doc.put("brand", ((String) src.get("name")).toLowerCase());
                    doc.put("brand_phonetic", phonetic(((String) src.get("name")).toLowerCase()));
                    doc.put("short_description", (normalizeShortDescription(src.get("short_description")).toLowerCase()));
                    doc.put("short_description_phonetic", phonetic((normalizeShortDescription(src.get("short_description")).toLowerCase())));
                    doc.put("categories", extractCategories(src));
                    doc.put("categories_phonetic", phoneticCategories(extractCategories(src)));
                    doc.put("color", extractColors(src, ((String) src.get("short_description")).toLowerCase()));
                    doc.put("created_at", toEpochSeconds((String) src.get("created_at")));
                    doc.put("gender", mapGender(extractGender(src)).gender);
                    doc.put("gender_rank", mapGender(extractGender(src)).genderValue);
                    doc.put("price", extractPrice(src));
                    doc.put("price_by_currency", extractFloatWithFallback(src, "price_by_currency", "price"));
                    doc.put("price_for_other", extractFloatWithFallback(src, "price_for_other", "price"));
                    doc.put("special_price", extractFloatWithFallback(src, "special_price", "price"));
                    doc.put("special_price_by_currency", extractFloatWithFallback(src, "special_price_by_currency", "price"));
                    doc.put("special_price_for_other", extractFloatWithFallback(src, "special_price_for_other", "price"));
                    doc.put("customizable_for_kids", 0);
                    doc.put("has_square_images", src.get("has_square_images"));
                    doc.put("hideDiscountFlag", 0);
                    doc.put("hover_image", buildImageUrl(src, "thumbnail"));
                    doc.put("image_url", buildImageUrl(src, "image"));
                    doc.put("image_label", "");
                    doc.put("img", buildImageUrl(src, "image"));
                    doc.put("isAvailable", extractIsAvailable(src));
                    doc.put("is_online", 1);
                    doc.put("meta_description", extractStringOrEmpty(src, "meta_description"));
                    doc.put("meta_keyword", extractStringOrEmpty(src, "meta_keyword"));
                    doc.put("meta_title", extractStringOrEmpty(src, "meta_title"));
                    doc.put("product_type", extractStringOrEmpty(src, "type_id"));
                    doc.put("url", extractStringOrEmpty(src, "url_key"));
                    doc.put("show_rts_button", 0);
                    doc.put("soldOut", extractSoldOut(src) ? 1 : 0);
                    doc.put("showInterest", extractSoldOut(src) ? 1 : 0);
                    doc.put("type", "product");
                    ShippingInfo shippingInfo = extractShippingInfo(src);
                    doc.put("ship_in_info", prepareShipInDays(src.get("ship_in_days")));
                    doc.put("available_sizes", shippingInfo.availableSizes);
                    doc.put("readyToShip", shippingInfo.readyToShip ? 1 : 0);
                    doc.put("readyToShip_24hr", shippingInfo.readyToShip24hr ? 1 : 0);
                    doc.put("readyToShipIcon", shippingInfo.readyToShipIcon);
                    doc.put("readyToShip_24hr_text", shippingInfo.readyToShipText);
                    doc.put("discount", extractDiscount(src, "discount"));
                    doc.put("discount_us", extractDiscount(src, "discount_us"));
                    doc.put("discount_row", extractDiscount(src, "discount_row"));
                    doc.put("discount_range", prepareDiscountRange(extractDiscount(src, "discount")));
                    doc.put("discount_range_us", prepareDiscountRange(extractDiscount(src, "discount_us")));
                    doc.put("discount_range_row", prepareDiscountRange(extractDiscount(src, "discount_row")));
                    doc.put("tag", extractTagFromHtml(src.get("icon_html")));
                    doc.put("price_on_request", extractPriceOnRequest(src, extractSoldOut(src)));
                    batch.add(doc);
                } catch (Exception ex) {
                    System.out.println("Error in " + sku + "  " + ex.getMessage());
                }

            }

            var a = typesenseClient
                    .collections(TYPESENSE_COLLECTION)
                    .documents()
                    .import_(
                            batch,
                            new ImportDocumentsParameters()
                                    .action(
                                            "upsert"
                                    )
                    );

            System.out.println("Indexed batch: " + batch.size());

            SearchScrollRequest scrollRequest =
                    new SearchScrollRequest(scrollId);
            scrollRequest.scroll(TimeValue.timeValueMinutes(2));

            response = esClient.scroll(
                    scrollRequest,
                    RequestOptions.DEFAULT
            );

            scrollId = response.getScrollId();
            hits = response.getHits().getHits();
        }

        ClearScrollRequest clearScrollRequest =
                new ClearScrollRequest();
        clearScrollRequest.addScrollId(scrollId);

        esClient.clearScroll(
                clearScrollRequest,
                RequestOptions.DEFAULT
        );
    }

    @SuppressWarnings("unchecked")
    private static List<String> extractColors(Map<String, Object> src, String desc) {

        // Use Set to avoid duplicates + preserve order
        Set<String> colors = new LinkedHashSet<>();

        // ---------- 1. Structured colors from ES ----------
        Object colorObj = src.get("color");
        if (colorObj instanceof List) {
            List<Map<String, Object>> colorList =
                    (List<Map<String, Object>>) colorObj;

            for (Map<String, Object> colorMap : colorList) {
                Object value = colorMap.get("label_lower");
                if (value != null) {
                    colors.add(value.toString().toLowerCase().trim());
                }
            }
        }

        // ---------- 2. Enrich from description ----------
        if (desc != null && !desc.isBlank()) {
            String lowerDesc = desc.toLowerCase();

            for (String predefinedColor : predefinedColor) {
                if (containsWord(lowerDesc, predefinedColor)) {
                    colors.add(predefinedColor);
                }
            }
        }

        return new ArrayList<>(colors);
    }

    private static boolean containsWord(String text, String word) {
        return Pattern
                .compile("\\b" + Pattern.quote(word) + "\\b")
                .matcher(text)
                .find();
    }


    @SuppressWarnings("unchecked")
    private static List<String> extractCategories(Map<String, Object> src) {
        Object colorObj = src.get("category_product");
        if (!(colorObj instanceof List)) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> colorList =
                (List<Map<String, Object>>) colorObj;

        List<String> colors = new ArrayList<>();
        for (Map<String, Object> colorMap : colorList) {
            Object value = colorMap.get("label");
            if (value != null) {
                colors.add(value.toString().toLowerCase());
            }
        }
        return colors;
    }

    public static String phonetic(String text) {

        if (text == null || text.isBlank()) {
            return "";
        }

        StringBuilder result = new StringBuilder();

        String[] tokens = text
                .toLowerCase()
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim()
                .split(" ");

        for (String token : tokens) {

            if (token.length() < 3) continue;
            if (STOP_WORDS.contains(token)) continue;
            if (token.matches("\\d+")) continue; // skip pure numbers

            // Primary + Secondary metaphone
            String primary = DM.doubleMetaphone(token, false);
            String secondary = DM.doubleMetaphone(token, true);

            if (primary != null && !primary.isBlank()) {
                result.append(primary).append(" ");
            }

            if (secondary != null
                    && !secondary.isBlank()
                    && !secondary.equals(primary)) {
                result.append(secondary).append(" ");
            }
        }

        return result.toString().trim();
    }

    private static List<String> phoneticCategories(List<String> categories) {

        if (categories == null || categories.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> phonetics = new ArrayList<>();

        for (String category : categories) {
            if (category == null || category.isBlank()) continue;

            // normalize first
            String normalized = category.toLowerCase().trim();

            // phonetic per category
            String phonetic = phonetic(normalized);

            if (!phonetic.isBlank()) {
                phonetics.add(phonetic);
            }
        }

        return phonetics;
    }

    public static long toEpochSeconds(String createdAt) {

        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        LocalDateTime dateTime =
                LocalDateTime.parse(createdAt, formatter);

        return dateTime
                .atZone(ZoneId.systemDefault())
                .toEpochSecond();
    }

    @SuppressWarnings("unchecked")
    private static String extractGender(Map<String, Object> src) {

        Object genderObj = src.get("by_gender");

        if (!(genderObj instanceof Map)) {
            return null;
        }

        Map<String, Object> genderMap = (Map<String, Object>) genderObj;

        Object urlKey = genderMap.get("url_key");

        if (urlKey == null) {
            return null;
        }

        return urlKey.toString().toLowerCase();
    }

    static class GenderResult {
        String gender;
        int genderValue;

        GenderResult(String gender, int genderValue) {
            this.gender = gender;
            this.genderValue = genderValue;
        }
    }

    private static GenderResult mapGender(String gender) {

        if (gender == null || gender.isBlank()) {
            return new GenderResult(null, 5); // default
        }

        gender = gender.toLowerCase();

        int genderValue = 5; // default

        switch (gender) {
            case "women":
                genderValue = 1;
                break;

            case "men":
                genderValue = 2;
                break;

            case "girl143":
                genderValue = 3;
                gender = "girl";
                break;

            case "boys143":
                genderValue = 4;
                gender = "boy";
                break;

            default:
                genderValue = 5;
                break;
        }

        return new GenderResult(gender, genderValue);
    }

    private static float extractPrice(Map<String, Object> src) {

        Object priceObj = src.get("price");

        if (priceObj == null) {
            return 0.0f;
        }

        try {
            if (priceObj instanceof Number) {
                return ((Number) priceObj).floatValue();
            }

            String priceStr = priceObj.toString().trim();
            if (priceStr.isEmpty()) {
                return 0.0f;
            }

            return Float.parseFloat(priceStr);

        } catch (Exception e) {
            return 0.0f;
        }
    }

    private static float extractFloatWithFallback(
            Map<String, Object> src,
            String primaryKey,
            String fallbackKey
    ) {

        Float primary = extractNullableFloat(src.get(primaryKey));
        if (primary != null) {
            return primary;
        }

        Float fallback = extractNullableFloat(src.get(fallbackKey));
        return fallback != null ? fallback : 0.0f;
    }

    private static Float extractNullableFloat(Object value) {

        if (value == null) {
            return null;
        }

        try {
            if (value instanceof Number) {
                return ((Number) value).floatValue();
            }

            String s = value.toString().trim();
            if (s.isEmpty()) {
                return null;
            }

            return Float.parseFloat(s);

        } catch (Exception e) {
            return null;
        }
    }


    private static final String IMAGE_BASE_URL =
            "https://img.perniaspopupshop.com/catalog/product";

    private static final String IMAGE_POLICY =
            "?impolicy=listingimage";

    private static String buildImageUrl(Map<String, Object> src, String key) {

        Object value = src.get(key);

        if (value == null) {
            return "";
        }

        String path = value.toString().trim();

        if (path.isEmpty()) {
            return "";
        }

        return IMAGE_BASE_URL + path + IMAGE_POLICY;
    }

    private static int extractIsAvailable(Map<String, Object> src) {

        Object disableNaObj = src.get("disable_na");

        if (disableNaObj == null) {
            return 1; // default available
        }

        String value = disableNaObj.toString().trim();

        return "1".equals(value) ? 0 : 1;
    }


    private static String extractStringOrEmpty(Map<String, Object> src, String key) {

        Object value = src.get(key);

        if (value == null) {
            return "";
        }

        String str = value.toString().trim();
        return str.isEmpty() ? "" : str;
    }

    @SuppressWarnings("unchecked")
    private static boolean extractSoldOut(Map<String, Object> src) {

        Object stockObj = src.get("stock");

        if (!(stockObj instanceof Map)) {
            return true; // no stock info → sold out
        }

        Map<String, Object> stockMap = (Map<String, Object>) stockObj;
        Object statusObj = stockMap.get("stock_status");

        if (statusObj == null) {
            return true; // missing status → sold out
        }

        // Handle Boolean
        if (statusObj instanceof Boolean) {
            return !((Boolean) statusObj);
        }

        // Handle Number (0 / 1)
        if (statusObj instanceof Number) {
            return ((Number) statusObj).intValue() == 0;
        }

        // Handle String ("1", "0", "true", "false")
        String value = statusObj.toString().trim().toLowerCase();

        return !(value.equals("1") || value.equals("true"));
    }

    static class ShippingInfo {
        boolean readyToShip = false;
        boolean readyToShip24hr = false;
        String readyToShipIcon = "";
        String readyToShipText = "";
        List<String> availableSizes = new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private static ShippingInfo extractShippingInfo(Map<String, Object> src) {

        ShippingInfo info = new ShippingInfo();

        boolean readyToShip48Hours = false;
        boolean shipIn7Days = false;
        boolean shipIn10Days = false;
        boolean shipIn14Days = false;

        Object childrenObj = src.get("configurable_children");
        if (!(childrenObj instanceof List)) {
            return info;
        }

        List<Map<String, Object>> children =
                (List<Map<String, Object>>) childrenObj;

        for (Map<String, Object> child : children) {

            // ---------- sizes ----------
            Object sizeObj = child.get("size");
            if (sizeObj != null) {
                info.availableSizes.add(sizeObj.toString());
            }

            // ---------- stock.qty ----------
            Float qty = extractNullableFloat(
                    ((Map<String, Object>) child.get("stock")).get("qty")
            );

            if (qty == null || qty <= 0) continue;

            // ---------- ready_to_ship_qty ----------
            Float rtsQty = extractNullableFloat(child.get("ready_to_ship_qty"));
            if (rtsQty == null || rtsQty <= 0) continue;

            // ---------- rts24hrs ----------
            Float rts24 = extractNullableFloat(child.get("rts24hrs"));
            if (rts24 != null && rts24 > 0) {
                readyToShip48Hours = true;
                continue;
            }

            // ---------- ship_in_days ----------
            Float shipDays = extractNullableFloat(child.get("ship_in_days"));
            if (shipDays == null) continue;

            if (shipDays == 7) {
                shipIn7Days = true;
            } else if (shipDays == 10) {
                shipIn10Days = true;
            } else if (shipDays == 14) {
                shipIn14Days = true;
            }
        }

        // ---------- FINAL PRIORITY LOGIC ----------
        if (shipIn14Days) {
            applyShipping(info, "SHIPS IN 14 DAYS");
        } else if (shipIn10Days) {
            applyShipping(info, "SHIPS IN 10 DAYS");
        } else if (shipIn7Days) {
            applyShipping(info, "SHIPS IN 7 DAYS");
        } else if (readyToShip48Hours) {
            applyShipping(info, "SHIPS IN 48 HOURS");
        }

        return info;
    }

    static void applyShipping(ShippingInfo info, String text) {

        info.readyToShip = true;
        info.readyToShip24hr = true;
        info.readyToShipText = text;
        info.readyToShipIcon =
                "https://img.perniaspopupshop.com/em/megamenupro/icon/RTS2.png";
    }


    private static String prepareShipInDays(Object daysObj) {

        int days = 50; // default

        if (daysObj != null) {
            try {
                String s = daysObj.toString().trim();
                if (!s.isEmpty()) {
                    days = Integer.parseInt(s);
                }
            } catch (Exception ignored) {
                days = 50;
            }
        }

        if (days <= 2) {
            return "2_2";
        }
        if (days > 3 && days <= 7) {
            return "7_7";
        }
        if (days == 10) {
            return "10_10";
        }
        if (days == 14) {
            return "14_14";
        }
        if (days > 7 && days <= 14) {
            return "7_14";
        }
        if (days > 14 && days <= 21) {
            return "14_21";
        }
        if (days > 21 && days <= 28) {
            return "21_28";
        }
        if (days > 28 && days <= 35) {
            return "28_35";
        }
        return "36_365";
    }

    private static int extractDiscount(Map<String, Object> src, String key) {
        Object value = src.get(key);
        if (value == null) {
            return 0;
        }
        try {
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            String s = value.toString().trim();
            if (s.isEmpty()) {
                return 0;
            }

            return (int) Float.parseFloat(s);
        } catch (Exception e) {
            return 0;
        }
    }

    private static String prepareDiscountRange(int discountPercentage) {
        if (discountPercentage >= 1 && discountPercentage <= 20) {
            return "1_20";
        }
        if (discountPercentage >= 21 && discountPercentage <= 30) {
            return "21_30";
        }
        if (discountPercentage >= 31 && discountPercentage <= 40) {
            return "31_40";
        }
        if (discountPercentage >= 41 && discountPercentage <= 50) {
            return "41_50";
        }
        if (discountPercentage >= 51 && discountPercentage <= 60) {
            return "51_60";
        }
        if (discountPercentage > 60) {
            return "above_60";
        }
        return "na";
    }

    private static String extractTagFromHtml(Object htmlObj) {
        if (htmlObj == null) {
            return "";
        }
        String html = htmlObj.toString().trim();
        if (html.isEmpty()) {
            return "";
        }
        try {
            Document doc = Jsoup.parse(html);
            Element div = doc.getElementById("bstsller");
            if (div == null) {
                return "";
            }
            return div.text().trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static int extractPriceOnRequest(Map<String, Object> src, boolean soldOut) {
        Object priceOnReqObj = src.get("price_on_request");
        float priceVal = 0.0f;
        try {
            if (priceOnReqObj instanceof Number) {
                priceVal = ((Number) priceOnReqObj).floatValue();
            } else if (priceOnReqObj != null) {
                String s = priceOnReqObj.toString().trim();
                if (!s.isEmpty()) {
                    priceVal = Float.parseFloat(s);
                }
            }
        } catch (Exception ignored) {
            priceVal = 0.0f;
        }
        return (soldOut && priceVal > 0) ? 1 : 0;
    }

    private String normalizeShortDescription(Object value) {
        if (value == null) {
            return "";
        }

        return value.toString()
                .toLowerCase()
                .replace("-", " ")     // hyphen → space (IMPORTANT)
                .replace("&", " ")     // & → space
                .replaceAll("\\s+", " ")
                .trim();
    }

}
