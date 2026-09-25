package com.shopkart.dao;

import com.shopkart.db.Database;
import com.shopkart.model.Category;
import com.shopkart.model.Product;
import com.shopkart.model.ProductQuery;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Catalogue data access. Every query is a {@link PreparedStatement}; user input is only
 * ever bound as a parameter, never concatenated into SQL. The only dynamic SQL fragments
 * (ORDER BY, pagination) are chosen from fixed whitelists.
 */
public final class ProductDao {

    private static final String SELECT_PRODUCT =
            "SELECT p.product_id, p.category_id, c.name, p.name, p.brand, p.description, "
                    + "p.price_paise, p.mrp_paise, p.stock, p.rating "
                    + "FROM products p JOIN categories c ON c.category_id = p.category_id ";

    private final Database.Dialect dialect;

    public ProductDao(Database.Dialect dialect) {
        this.dialect = dialect;
    }

    public long insertCategory(Connection conn, String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO categories (name) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    public List<Category> categories(Connection conn) throws SQLException {
        List<Category> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT category_id, name FROM categories ORDER BY name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new Category(rs.getLong(1), rs.getString(2)));
            }
        }
        return out;
    }

    public long insertProduct(Connection conn, long categoryId, String name, String brand, String description,
                              long pricePaise, long mrpPaise, int stock, double rating) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO products (category_id, name, brand, description, price_paise, mrp_paise, stock, rating) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, categoryId);
            ps.setString(2, name);
            ps.setString(3, brand);
            ps.setString(4, description);
            ps.setLong(5, pricePaise);
            ps.setLong(6, mrpPaise);
            ps.setInt(7, stock);
            ps.setBigDecimal(8, java.math.BigDecimal.valueOf(rating));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    public Optional<Product> findById(Connection conn, long productId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SELECT_PRODUCT + "WHERE p.product_id = ?")) {
            ps.setLong(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /** Browse and search with optional text, category, sort and pagination. */
    public List<Product> find(Connection conn, ProductQuery q) throws SQLException {
        StringBuilder sql = new StringBuilder(SELECT_PRODUCT).append("WHERE 1 = 1 ");
        List<Object> params = new ArrayList<>();

        if (q.text() != null && !q.text().isBlank()) {
            for (String term : q.text().trim().toLowerCase().split("\\s+")) {
                String like = "%" + escapeLike(term) + "%";
                sql.append("AND (LOWER(p.name) LIKE ? ESCAPE '!' OR LOWER(p.brand) LIKE ? ESCAPE '!' "
                        + "OR LOWER(p.description) LIKE ? ESCAPE '!' OR LOWER(c.name) LIKE ? ESCAPE '!') ");
                params.add(like);
                params.add(like);
                params.add(like);
                params.add(like);
            }
        }
        if (q.categoryId() != null) {
            sql.append("AND p.category_id = ? ");
            params.add(q.categoryId());
        }
        sql.append(switch (q.sort()) {
            case PRICE_ASC -> "ORDER BY p.price_paise ASC, p.product_id ";
            case PRICE_DESC -> "ORDER BY p.price_paise DESC, p.product_id ";
            case RATING -> "ORDER BY p.rating DESC, p.product_id ";
            case DISCOUNT -> "ORDER BY (p.mrp_paise - p.price_paise) * 100 / p.mrp_paise DESC, p.product_id ";
            case RELEVANCE -> "ORDER BY p.stock DESC, p.rating DESC, p.product_id ";
        });
        sql.append(dialect == Database.Dialect.MYSQL
                ? "LIMIT ? OFFSET ?"
                : "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
        int offset = q.page() * q.pageSize();
        if (dialect == Database.Dialect.MYSQL) {
            params.add(q.pageSize());
            params.add(offset);
        } else {
            params.add(offset);
            params.add(q.pageSize());
        }

        List<Product> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
        }
        return out;
    }

    /**
     * Atomically reserves stock: succeeds only if enough units remain. Two shoppers racing for
     * the last unit cannot both win because the check and the decrement are one statement.
     */
    public boolean decrementStock(Connection conn, long productId, int quantity) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE products SET stock = stock - ? WHERE product_id = ? AND stock >= ?")) {
            ps.setInt(1, quantity);
            ps.setLong(2, productId);
            ps.setInt(3, quantity);
            return ps.executeUpdate() == 1;
        }
    }

    public void updatePrice(Connection conn, long productId, long pricePaise) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE products SET price_paise = ? WHERE product_id = ?")) {
            ps.setLong(1, pricePaise);
            ps.setLong(2, productId);
            ps.executeUpdate();
        }
    }

    public int countProducts(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM products");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** Escapes LIKE wildcards so a search for "50%" matches the literal text. */
    static String escapeLike(String s) {
        return s.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private static Product map(ResultSet rs) throws SQLException {
        return new Product(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5),
                rs.getString(6), rs.getLong(7), rs.getLong(8), rs.getInt(9), rs.getBigDecimal(10).doubleValue());
    }
}
