package com.shopkart.model;

import java.util.List;

public record Cart(List<CartItem> items) {

    public long totalPaise() {
        return items.stream().mapToLong(CartItem::lineTotalPaise).sum();
    }

    public long totalMrpPaise() {
        return items.stream().mapToLong(i -> i.product().mrpPaise() * i.quantity()).sum();
    }

    public int itemCount() {
        return items.stream().mapToInt(CartItem::quantity).sum();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }
}
