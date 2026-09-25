package com.shopkart.service;

import com.shopkart.dao.UserDao;
import com.shopkart.db.Database;
import com.shopkart.model.User;
import com.shopkart.service.ShopException.Kind;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Registration, login and opaque session tokens. */
public final class AuthService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Database db;
    private final UserDao users = new UserDao();
    private final Map<String, Long> sessions = new ConcurrentHashMap<>();

    public AuthService(Database db) {
        this.db = db;
    }

    public User register(String email, String fullName, String password) {
        String normalised = email == null ? "" : email.trim().toLowerCase();
        if (!EMAIL.matcher(normalised).matches()) {
            throw new ShopException(Kind.BAD_REQUEST, "Please enter a valid email address");
        }
        if (fullName == null || fullName.isBlank()) {
            throw new ShopException(Kind.BAD_REQUEST, "Name is required");
        }
        if (password == null || password.length() < 8) {
            throw new ShopException(Kind.BAD_REQUEST, "Password must be at least 8 characters");
        }
        try (Connection conn = db.getConnection()) {
            if (users.findCredentialsByEmail(conn, normalised).isPresent()) {
                throw new ShopException(Kind.CONFLICT, "An account with this email already exists");
            }
            long id = users.insert(conn, normalised, fullName.trim(), PasswordHasher.hash(password));
            return new User(id, normalised, fullName.trim());
        } catch (SQLException e) {
            throw new DataAccessException("register failed", e);
        }
    }

    /** Returns a session token. The error message is identical for unknown email and wrong password. */
    public String login(String email, String password) {
        String normalised = email == null ? "" : email.trim().toLowerCase();
        try (Connection conn = db.getConnection()) {
            Optional<UserDao.Credentials> creds = users.findCredentialsByEmail(conn, normalised);
            if (creds.isEmpty() || password == null || !PasswordHasher.verify(password, creds.get().passwordHash())) {
                throw new ShopException(Kind.UNAUTHORIZED, "Invalid email or password");
            }
            byte[] raw = new byte[24];
            RANDOM.nextBytes(raw);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
            sessions.put(token, creds.get().user().userId());
            return token;
        } catch (SQLException e) {
            throw new DataAccessException("login failed", e);
        }
    }

    public void logout(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    /** Resolves a token to a user, or throws UNAUTHORIZED. */
    public User requireUser(String token) {
        Long userId = token == null ? null : sessions.get(token);
        if (userId == null) {
            throw new ShopException(Kind.UNAUTHORIZED, "Please log in");
        }
        try (Connection conn = db.getConnection()) {
            return users.findById(conn, userId)
                    .orElseThrow(() -> new ShopException(Kind.UNAUTHORIZED, "Please log in"));
        } catch (SQLException e) {
            throw new DataAccessException("session lookup failed", e);
        }
    }
}
