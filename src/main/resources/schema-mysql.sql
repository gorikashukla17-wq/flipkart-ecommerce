-- ShopKart schema for MySQL 8. Normalised to 3NF; money stored as BIGINT paise.

CREATE TABLE IF NOT EXISTS users (
    user_id        BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    email          VARCHAR(120) NOT NULL UNIQUE,
    full_name      VARCHAR(100) NOT NULL,
    password_hash  VARCHAR(200) NOT NULL,           -- PBKDF2 salt:hash, never plain text
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS categories (
    category_id    BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name           VARCHAR(60)  NOT NULL UNIQUE
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS products (
    product_id     BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    category_id    BIGINT       NOT NULL,
    name           VARCHAR(150) NOT NULL,
    brand          VARCHAR(60)  NOT NULL,
    description    VARCHAR(1000),
    price_paise    BIGINT       NOT NULL,
    mrp_paise      BIGINT       NOT NULL,
    stock          INT          NOT NULL,
    rating         DECIMAL(2,1) NOT NULL DEFAULT 0,
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES categories (category_id),
    CONSTRAINT chk_stock CHECK (stock >= 0),
    CONSTRAINT chk_price CHECK (price_paise > 0 AND price_paise <= mrp_paise),
    INDEX idx_products_category (category_id),
    INDEX idx_products_name (name)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS cart_items (
    user_id        BIGINT       NOT NULL,
    product_id     BIGINT       NOT NULL,
    quantity       INT          NOT NULL,
    added_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, product_id),
    CONSTRAINT fk_cart_user    FOREIGN KEY (user_id)    REFERENCES users (user_id),
    CONSTRAINT fk_cart_product FOREIGN KEY (product_id) REFERENCES products (product_id),
    CONSTRAINT chk_cart_qty CHECK (quantity > 0)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS orders (
    order_id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT       NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    total_paise      BIGINT       NOT NULL,
    shipping_address VARCHAR(300) NOT NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    INDEX idx_orders_user (user_id, created_at)
) ENGINE = InnoDB;

-- product_name and unit_price_paise are snapshots taken at checkout, so order history
-- stays correct even if the catalogue entry is later renamed or repriced.
CREATE TABLE IF NOT EXISTS order_items (
    order_id          BIGINT       NOT NULL,
    product_id        BIGINT       NOT NULL,
    product_name      VARCHAR(150) NOT NULL,
    unit_price_paise  BIGINT       NOT NULL,
    quantity          INT          NOT NULL,
    PRIMARY KEY (order_id, product_id),
    CONSTRAINT fk_item_order   FOREIGN KEY (order_id)   REFERENCES orders (order_id),
    CONSTRAINT fk_item_product FOREIGN KEY (product_id) REFERENCES products (product_id)
) ENGINE = InnoDB;
