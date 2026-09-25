package com.shopkart.model;

public record CartItem(Product product, int quantity) {
    public long lineTotalPaise() {
        return product.pricePaise() * quantity;
    }
}
