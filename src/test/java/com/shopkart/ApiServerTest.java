package com.shopkart;

import com.shopkart.db.Database;
import com.shopkart.db.SeedData;
import com.shopkart.service.AuthService;
import com.shopkart.service.CartService;
import com.shopkart.service.CatalogService;
import com.shopkart.service.OrderService;
import com.shopkart.web.ApiServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** End-to-end through real HTTP: register, browse, add to cart, checkout, view orders. */
public class ApiServerTest {

    private ApiServer server;
    private String base;
    private final HttpClient http = HttpClient.newBuilder().proxy(HttpClient.Builder.NO_PROXY).build();

    @Before
    public void start() throws Exception {
        Database db = Database.inMemory("api" + System.nanoTime());
        db.initSchema();
        SeedData.loadIfEmpty(db);
        CartService cart = new CartService(db);
        server = new ApiServer(new AuthService(db), new CatalogService(db), cart, new OrderService(db, cart));
        base = "http://127.0.0.1:" + server.start(0);
    }

    @After
    public void stop() {
        server.stop();
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path)).GET();
        if (token != null) b.header("X-Auth-Token", token);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String form, String token) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form));
        if (token != null) b.header("X-Auth-Token", token);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String extract(String json, String regex) {
        Matcher m = Pattern.compile(regex).matcher(json);
        assertTrue("pattern " + regex + " not in " + json, m.find());
        return m.group(1);
    }

    @Test
    public void fullShoppingFlowOverHttp() throws Exception {
        assertTrue(get("/", null).body().contains("ShopKart"));

        HttpResponse<String> products = get("/api/products?q=power+bank", null);
        assertEquals(200, products.statusCode());
        String productId = extract(products.body(), "\"productId\":(\\d+)");

        assertEquals(401, post("/api/cart/add", "productId=" + productId, null).statusCode());

        HttpResponse<String> reg = post("/api/register",
                "email=riya%40example.com&name=Riya+S&password=supersecret", null);
        assertEquals(200, reg.statusCode());
        String token = extract(reg.body(), "\"token\":\"([^\"]+)\"");

        HttpResponse<String> added = post("/api/cart/add", "productId=" + productId + "&quantity=2", token);
        assertEquals(200, added.statusCode());
        assertTrue(added.body().contains("\"itemCount\":2"));

        assertEquals(400, post("/api/checkout", "address=x", token).statusCode());
        HttpResponse<String> order = post("/api/checkout", "address=221+Anna+Salai%2C+Chennai+600002", token);
        assertEquals(200, order.statusCode());
        assertTrue(order.body().contains("\"status\":\"PLACED\""));

        assertTrue(get("/api/orders", token).body().contains("Power Bank"));
        assertTrue(get("/api/cart", token).body().contains("\"itemCount\":0"));
    }

    @Test
    public void errorsAreJsonWithProperStatusCodes() throws Exception {
        assertEquals(404, get("/api/products/999999", null).statusCode());
        assertEquals(400, get("/api/products?sort=bogus", null).statusCode());
        assertEquals(400, get("/api/products/abc", null).statusCode());
        HttpResponse<String> login = post("/api/login", "email=a%40b.com&password=wrongpass", null);
        assertEquals(401, login.statusCode());
        assertTrue(login.body().startsWith("{\"error\":"));
    }
}
