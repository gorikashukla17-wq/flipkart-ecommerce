package com.shopkart.model;

/** Catalogue entry. Prices are in paise (1 rupee = 100 paise). */
public record Product(long productId,
                      long categoryId,
                      String categoryName,
                      String name,
                      String brand,
                      String description,
                      long pricePaise,
                      long mrpPaise,
                      int stock,
                      double rating) {

    /** Whole-number discount percentage off MRP, as shown on product cards. */
    public int discountPercent() {
        return mrpPaise == 0 ? 0 : (int) Math.round(100.0 * (mrpPaise - pricePaise) / mrpPaise);
    }

    public boolean inStock() {
        return stock > 0;
    }
}
