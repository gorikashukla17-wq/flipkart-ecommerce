package com.shopkart.model;

/** A line on a placed order: name and price are snapshots taken at checkout time. */
public record OrderItem(long productId, String productName, long unitPricePaise, int quantity) {
    public long lineTotalPaise() {
        return unitPricePaise * quantity;
    }
}
