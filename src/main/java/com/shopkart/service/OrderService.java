package com.shopkart.service;

import com.shopkart.dao.CartDao;
import com.shopkart.dao.OrderDao;
import com.shopkart.dao.ProductDao;
import com.shopkart.db.Database;
import com.shopkart.model.Cart;
import com.shopkart.model.CartItem;
import com.shopkart.model.Order;
import com.shopkart.model.OrderItem;
import com.shopkart.service.ShopException.Kind;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Checkout is one database transaction: reserve stock for every line, create the order and
 * its items (with price/name snapshots), empty the cart, then commit. If any line is out of
 * stock the whole transaction rolls back, so no stock is reserved for a failed order.
 */
public final class OrderService {

    private final Database db;
    private final ProductDao products;
    private final CartDao carts = new CartDao();
    private final OrderDao orders = new OrderDao();
    private final CartService cartService;

    public OrderService(Database db, CartService cartService) {
        this.db = db;
        this.products = new ProductDao(db.dialect());
        this.cartService = cartService;
    }

    public Order checkout(long userId, String shippingAddress) {
        if (shippingAddress == null || shippingAddress.trim().length() < 10) {
            throw new ShopException(Kind.BAD_REQUEST, "Please enter a complete shipping address");
        }
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Cart cart = cartService.load(conn, userId);
                if (cart.isEmpty()) {
                    throw new ShopException(Kind.BAD_REQUEST, "Your cart is empty");
                }
                for (CartItem item : cart.items()) {
                    if (!products.decrementStock(conn, item.product().productId(), item.quantity())) {
                        throw new ShopException(Kind.CONFLICT,
                                "Sorry, " + item.product().name() + " no longer has enough stock");
                    }
                }
                long orderId = orders.insertOrder(conn, userId, "PLACED", cart.totalPaise(), shippingAddress.trim());
                for (CartItem item : cart.items()) {
                    orders.insertItem(conn, orderId, new OrderItem(item.product().productId(),
                            item.product().name(), item.product().pricePaise(), item.quantity()));
                }
                carts.clear(conn, userId);
                Order placed = orders.findById(conn, orderId).orElseThrow();
                conn.commit();
                return placed;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("checkout failed", e);
        }
    }

    public List<Order> history(long userId) {
        try (Connection conn = db.getConnection()) {
            return orders.findByUser(conn, userId);
        } catch (SQLException e) {
            throw new DataAccessException("order history failed", e);
        }
    }
}
