package com.shopkart.service;

import com.shopkart.dao.ProductDao;
import com.shopkart.db.Database;
import com.shopkart.model.Category;
import com.shopkart.model.Product;
import com.shopkart.model.ProductQuery;
import com.shopkart.service.ShopException.Kind;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public final class CatalogService {

    private final Database db;
    private final ProductDao products;

    public CatalogService(Database db) {
        this.db = db;
        this.products = new ProductDao(db.dialect());
    }

    public List<Product> browse(ProductQuery query) {
        try (Connection conn = db.getConnection()) {
            return products.find(conn, query);
        } catch (SQLException e) {
            throw new DataAccessException("browse failed", e);
        }
    }

    public Product product(long productId) {
        try (Connection conn = db.getConnection()) {
            return products.findById(conn, productId)
                    .orElseThrow(() -> new ShopException(Kind.NOT_FOUND, "Product not found"));
        } catch (SQLException e) {
            throw new DataAccessException("product lookup failed", e);
        }
    }

    public List<Category> categories() {
        try (Connection conn = db.getConnection()) {
            return products.categories(conn);
        } catch (SQLException e) {
            throw new DataAccessException("categories failed", e);
        }
    }
}
