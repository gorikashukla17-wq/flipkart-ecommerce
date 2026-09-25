package com.shopkart.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Cart rows: (user, product) -> quantity. */
public final class CartDao {

    public int quantity(Connection conn, long userId, long productId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT quantity FROM cart_items WHERE user_id = ? AND product_id = ?")) {
            ps.setLong(1, userId);
            ps.setLong(2, productId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** Sets the quantity for a product; zero removes the line. */
    public void setQuantity(Connection conn, long userId, long productId, int quantity) throws SQLException {
        if (quantity <= 0) {
            remove(conn, userId, productId);
            return;
        }
        try (PreparedStatement upd = conn.prepareStatement(
                "UPDATE cart_items SET quantity = ? WHERE user_id = ? AND product_id = ?")) {
            upd.setInt(1, quantity);
            upd.setLong(2, userId);
            upd.setLong(3, productId);
            if (upd.executeUpdate() == 1) {
                return;
            }
        }
        try (PreparedStatement ins = conn.prepareStatement(
                "INSERT INTO cart_items (user_id, product_id, quantity) VALUES (?, ?, ?)")) {
            ins.setLong(1, userId);
            ins.setLong(2, productId);
            ins.setInt(3, quantity);
            ins.executeUpdate();
        }
    }

    public void remove(Connection conn, long userId, long productId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM cart_items WHERE user_id = ? AND product_id = ?")) {
            ps.setLong(1, userId);
            ps.setLong(2, productId);
            ps.executeUpdate();
        }
    }

    public void clear(Connection conn, long userId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM cart_items WHERE user_id = ?")) {
            ps.setLong(1, userId);
            ps.executeUpdate();
        }
    }

    /** productId -> quantity, oldest line first. */
    public Map<Long, Integer> lines(Connection conn, long userId) throws SQLException {
        Map<Long, Integer> out = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT product_id, quantity FROM cart_items WHERE user_id = ? ORDER BY added_at, product_id")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getLong(1), rs.getInt(2));
                }
            }
        }
        return out;
    }
}
