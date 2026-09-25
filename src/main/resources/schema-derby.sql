-- ShopKart schema for embedded Apache Derby (demo + tests). Same model as schema-mysql.sql.

CREATE TABLE users (
    user_id        BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email          VARCHAR(120) NOT NULL UNIQUE,
    full_name      VARCHAR(100) NOT NULL,
    password_hash  VARCHAR(200) NOT NULL,           -- PBKDF2 salt:hash, never plain text
    created_at     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE categories (
    category_id    BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name           VARCHAR(60)  NOT NULL UNIQUE
);

CREATE TABLE products (
    product_id     BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    category_id    BIGINT       NOT NULL,
    name           VARCHAR(150) NOT NULL,
    brand          VARCHAR(60)  NOT NULL,
    description    VARCHAR(1000),
    price_paise    BIGINT       NOT NULL,
    mrp_paise      BIGINT       NOT NULL,
    stock          INT          NOT NULL,
    rating         DECIMAL(2,1) DEFAULT 0 NOT NULL,
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES categories (category_id),
    CONSTRAINT chk_stock CHECK (stock >= 0),
    CONSTRAINT chk_price CHECK (price_paise > 0 AND price_paise <= mrp_paise)
);

CREATE TABLE cart_items (
    user_id        BIGINT       NOT NULL,
    product_id     BIGINT       NOT NULL,
    quantity       INT          NOT NULL,
    added_at       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, product_id),
    CONSTRAINT fk_cart_user    FOREIGN KEY (user_id)    REFERENCES users (user_id),
    CONSTRAINT fk_cart_product FOREIGN KEY (product_id) REFERENCES products (product_id),
    CONSTRAINT chk_cart_qty CHECK (quantity > 0)
);

CREATE TABLE orders (
    order_id         BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          BIGINT       NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    total_paise      BIGINT       NOT NULL,
    shipping_address VARCHAR(300) NOT NULL,
    created_at       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES users (user_id)
);

-- product_name and unit_price_paise are snapshots taken at checkout, so order history
-- stays correct even if the catalogue entry is later renamed or repriced.
CREATE TABLE order_items (
    order_id          BIGINT       NOT NULL,
    product_id        BIGINT       NOT NULL,
    product_name      VARCHAR(150) NOT NULL,
    unit_price_paise  BIGINT       NOT NULL,
    quantity          INT          NOT NULL,
    PRIMARY KEY (order_id, product_id),
    CONSTRAINT fk_item_order   FOREIGN KEY (order_id)   REFERENCES orders (order_id),
    CONSTRAINT fk_item_product FOREIGN KEY (product_id) REFERENCES products (product_id)
);

CREATE INDEX idx_products_category ON products (category_id);
CREATE INDEX idx_products_name ON products (name);
CREATE INDEX idx_orders_user ON orders (user_id, created_at);
