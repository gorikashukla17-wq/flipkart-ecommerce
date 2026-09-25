package com.shopkart.model;

import java.sql.Timestamp;
import java.util.List;

public record Order(long orderId,
                    long userId,
                    String status,
                    long totalPaise,
                    String shippingAddress,
                    Timestamp createdAt,
                    List<OrderItem> items) {
}
