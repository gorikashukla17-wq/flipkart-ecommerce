package com.shopkart.dao;

import com.shopkart.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

public final class UserDao {

    /** Row plus the stored hash, used only by the authentication service. */
    public record Credentials(User user, String passwordHash) { }

    public long insert(Connection conn, String email, String fullName, String passwordHash) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO users (email, full_name, password_hash) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, email);
            ps.setString(2, fullName);
            ps.setString(3, passwordHash);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    public Optional<Credentials> findCredentialsByEmail(Connection conn, String email) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT user_id, email, full_name, password_hash FROM users WHERE email = ?")) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Credentials(
                        new User(rs.getLong(1), rs.getString(2), rs.getString(3)), rs.getString(4)));
            }
        }
    }

    public Optional<User> findById(Connection conn, long userId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT user_id, email, full_name FROM users WHERE user_id = ?")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next()
                        ? Optional.of(new User(rs.getLong(1), rs.getString(2), rs.getString(3)))
                        : Optional.empty();
            }
        }
    }
}
