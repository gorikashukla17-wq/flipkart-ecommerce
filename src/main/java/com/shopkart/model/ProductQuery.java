package com.shopkart.model;

/**
 * Browse / search parameters. All fields are optional.
 *
 * @param text       free-text search over name, brand and description
 * @param categoryId restrict to one category
 * @param sort       RELEVANCE, PRICE_ASC, PRICE_DESC, RATING, DISCOUNT
 */
public record ProductQuery(String text, Long categoryId, Sort sort, int page, int pageSize) {

    public enum Sort { RELEVANCE, PRICE_ASC, PRICE_DESC, RATING, DISCOUNT }

    public ProductQuery {
        if (sort == null) {
            sort = Sort.RELEVANCE;
        }
        if (page < 0) {
            page = 0;
        }
        if (pageSize <= 0 || pageSize > 100) {
            pageSize = 24;
        }
    }

    public static ProductQuery all() {
        return new ProductQuery(null, null, Sort.RELEVANCE, 0, 24);
    }

    public static ProductQuery search(String text) {
        return new ProductQuery(text, null, Sort.RELEVANCE, 0, 24);
    }
}
