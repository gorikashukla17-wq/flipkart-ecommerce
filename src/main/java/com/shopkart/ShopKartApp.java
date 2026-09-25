package com.shopkart;

import com.shopkart.db.Database;
import com.shopkart.db.SeedData;
import com.shopkart.service.AuthService;
import com.shopkart.service.CartService;
import com.shopkart.service.CatalogService;
import com.shopkart.service.OrderService;
import com.shopkart.web.ApiServer;

/**
 * Entry point. Uses in-memory Derby unless SHOPKART_DB_URL points at MySQL.
 * Open http://localhost:8080 after starting.
 */
public final class ShopKartApp {

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

        Database db = Database.fromEnvironment();
        db.initSchema();
        SeedData.loadIfEmpty(db);

        CartService cart = new CartService(db);
        ApiServer server = new ApiServer(new AuthService(db), new CatalogService(db), cart, new OrderService(db, cart));
        int bound = server.start(port);
        System.out.println("ShopKart running at http://localhost:" + bound + "  (database: " + db.dialect() + ")");
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
    }
}
