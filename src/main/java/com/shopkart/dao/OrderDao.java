package com.shopkart.dao;

import com.shopkart.model.Order;
import com.shopkart.model.OrderItem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class OrderDao {

    public long insertOrder(Connection conn, long userId, String status, long totalPaise, String address)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO orders (user_id, status, total_paise, shipping_address) VALUES (?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, userId);
            ps.setString(2, status);
            ps.setLong(3, totalPaise);
            ps.setString(4, address);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    public void insertItem(Connection conn, long orderId, OrderItem item) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO order_items (order_id, product_id, product_name, unit_price_paise, quantity) "
                        + "VALUES (?, ?, ?, ?, ?)")) {
            ps.setLong(1, orderId);
            ps.setLong(2, item.productId());
            ps.setString(3, item.productName());
            ps.setLong(4, item.unitPricePaise());
            ps.setInt(5, item.quantity());
            ps.executeUpdate();
        }
    }

    public List<Order> findByUser(Connection conn, long userId) throws SQLException {
        List<Order> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT order_id FROM orders WHERE user_id = ? ORDER BY created_at DESC, order_id DESC")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    findById(conn, rs.getLong(1)).ifPresent(out::add);
                }
            }
        }
        return out;
    }

    public Optional<Order> findById(Connection conn, long orderId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT order_id, user_id, status, total_paise, shipping_address, created_at FROM orders WHERE order_id = ?")) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Order(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getLong(4),
                        rs.getString(5), rs.getTimestamp(6), items(conn, orderId)));
            }
        }
    }

    private List<OrderItem> items(Connection conn, long orderId) throws SQLException {
        List<OrderItem> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT product_id, product_name, unit_price_paise, quantity FROM order_items "
                        + "WHERE order_id = ? ORDER BY product_id")) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new OrderItem(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getInt(4)));
                }
            }
        }
        return out;
    }
}
