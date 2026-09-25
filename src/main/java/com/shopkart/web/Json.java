package com.shopkart.web;

import com.shopkart.model.Cart;
import com.shopkart.model.CartItem;
import com.shopkart.model.Category;
import com.shopkart.model.Order;
import com.shopkart.model.OrderItem;
import com.shopkart.model.Product;
import com.shopkart.model.User;

import java.util.Collection;
import java.util.Iterator;
import java.util.function.Function;

/** Tiny dependency-free JSON serialiser for the handful of types the API returns. */
public final class Json {

    private Json() { }

    public static String str(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '<' -> sb.append("\\u003c"); // keeps JSON safe to embed in HTML
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    public static <T> String array(Collection<T> items, Function<T, String> fn) {
        StringBuilder sb = new StringBuilder("[");
        Iterator<T> it = items.iterator();
        while (it.hasNext()) {
            sb.append(fn.apply(it.next()));
            if (it.hasNext()) {
                sb.append(',');
            }
        }
        return sb.append(']').toString();
    }

    public static String error(String message) {
        return "{\"error\":" + str(message) + "}";
    }

    public static String user(User u) {
        return "{\"userId\":" + u.userId() + ",\"email\":" + str(u.email()) + ",\"fullName\":" + str(u.fullName()) + "}";
    }

    public static String category(Category c) {
        return "{\"categoryId\":" + c.categoryId() + ",\"name\":" + str(c.name()) + "}";
    }

    public static String product(Product p) {
        return "{\"productId\":" + p.productId()
                + ",\"categoryId\":" + p.categoryId()
                + ",\"category\":" + str(p.categoryName())
                + ",\"name\":" + str(p.name())
                + ",\"brand\":" + str(p.brand())
                + ",\"description\":" + str(p.description())
                + ",\"pricePaise\":" + p.pricePaise()
                + ",\"mrpPaise\":" + p.mrpPaise()
                + ",\"discountPercent\":" + p.discountPercent()
                + ",\"stock\":" + p.stock()
                + ",\"rating\":" + p.rating() + "}";
    }

    public static String cart(Cart c) {
        return "{\"items\":" + array(c.items(), Json::cartItem)
                + ",\"itemCount\":" + c.itemCount()
                + ",\"totalPaise\":" + c.totalPaise()
                + ",\"totalMrpPaise\":" + c.totalMrpPaise() + "}";
    }

    private static String cartItem(CartItem i) {
        return "{\"product\":" + product(i.product()) + ",\"quantity\":" + i.quantity()
                + ",\"lineTotalPaise\":" + i.lineTotalPaise() + "}";
    }

    public static String order(Order o) {
        return "{\"orderId\":" + o.orderId()
                + ",\"status\":" + str(o.status())
                + ",\"totalPaise\":" + o.totalPaise()
                + ",\"shippingAddress\":" + str(o.shippingAddress())
                + ",\"createdAt\":" + str(String.valueOf(o.createdAt()))
                + ",\"items\":" + array(o.items(), Json::orderItem) + "}";
    }

    private static String orderItem(OrderItem i) {
        return "{\"productId\":" + i.productId() + ",\"productName\":" + str(i.productName())
                + ",\"unitPricePaise\":" + i.unitPricePaise() + ",\"quantity\":" + i.quantity() + "}";
    }
}
