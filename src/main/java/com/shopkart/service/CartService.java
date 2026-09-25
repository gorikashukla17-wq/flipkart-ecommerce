package com.shopkart.service;

import com.shopkart.dao.CartDao;
import com.shopkart.dao.ProductDao;
import com.shopkart.db.Database;
import com.shopkart.model.Cart;
import com.shopkart.model.CartItem;
import com.shopkart.model.Product;
import com.shopkart.service.ShopException.Kind;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CartService {

    /** Per-line purchase limit, as on most marketplaces. */
    public static final int MAX_QTY_PER_ITEM = 10;

    private final Database db;
    private final CartDao carts = new CartDao();
    private final ProductDao products;

    public CartService(Database db) {
        this.db = db;
        this.products = new ProductDao(db.dialect());
    }

    public Cart add(long userId, long productId, int quantity) {
        try (Connection conn = db.getConnection()) {
            int current = carts.quantity(conn, userId, productId);
            return setQuantity(conn, userId, productId, current + quantity);
        } catch (SQLException e) {
            throw new DataAccessException("add to cart failed", e);
        }
    }

    public Cart update(long userId, long productId, int quantity) {
        try (Connection conn = db.getConnection()) {
            return setQuantity(conn, userId, productId, quantity);
        } catch (SQLException e) {
            throw new DataAccessException("update cart failed", e);
        }
    }

    public Cart remove(long userId, long productId) {
        return update(userId, productId, 0);
    }

    public Cart view(long userId) {
        try (Connection conn = db.getConnection()) {
            return load(conn, userId);
        } catch (SQLException e) {
            throw new DataAccessException("view cart failed", e);
        }
    }

    private Cart setQuantity(Connection conn, long userId, long productId, int quantity) throws SQLException {
        if (quantity > 0) {
            Product p = products.findById(conn, productId)
                    .orElseThrow(() -> new ShopException(Kind.NOT_FOUND, "Product not found"));
            if (quantity > MAX_QTY_PER_ITEM) {
                throw new ShopException(Kind.BAD_REQUEST, "You can buy at most " + MAX_QTY_PER_ITEM + " of this item");
            }
            if (quantity > p.stock()) {
                throw new ShopException(Kind.CONFLICT, p.stock() == 0
                        ? p.name() + " is out of stock"
                        : "Only " + p.stock() + " left of " + p.name());
            }
        }
        carts.setQuantity(conn, userId, productId, quantity);
        return load(conn, userId);
    }

    Cart load(Connection conn, long userId) throws SQLException {
        List<CartItem> items = new ArrayList<>();
        for (Map.Entry<Long, Integer> line : carts.lines(conn, userId).entrySet()) {
            products.findById(conn, line.getKey()).ifPresent(p -> items.add(new CartItem(p, line.getValue())));
        }
        return new Cart(items);
    }
}
