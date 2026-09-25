package com.shopkart.web;

import com.shopkart.model.ProductQuery;
import com.shopkart.model.User;
import com.shopkart.service.AuthService;
import com.shopkart.service.CartService;
import com.shopkart.service.CatalogService;
import com.shopkart.service.OrderService;
import com.shopkart.service.ShopException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HTTP front controller built on the JDK's {@code com.sun.net.httpserver}: serves the
 * single-page UI and a small JSON API. Request bodies are form-encoded.
 */
public final class ApiServer {

    private static final Logger LOG = Logger.getLogger(ApiServer.class.getName());

    private final AuthService auth;
    private final CatalogService catalog;
    private final CartService cart;
    private final OrderService orders;
    private HttpServer server;

    public ApiServer(AuthService auth, CatalogService catalog, CartService cart, OrderService orders) {
        this.auth = auth;
        this.catalog = catalog;
        this.cart = cart;
        this.orders = orders;
    }

    /** Starts on the given port (0 = any free port) and returns the bound port. */
    public int start(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/", this::handleApi);
        server.createContext("/", this::handleStatic);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        return server.getAddress().getPort();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void handleApi(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();
        Map<String, String> query = parseForm(ex.getRequestURI().getRawQuery());
        Map<String, String> body = "POST".equals(method) ? parseForm(readBody(ex)) : Map.of();
        String token = ex.getRequestHeaders().getFirst("X-Auth-Token");
        try {
            String json = route(method, path, query, body, token);
            if (json == null) {
                send(ex, 404, Json.error("No such endpoint"));
            } else {
                send(ex, 200, json);
            }
        } catch (ShopException e) {
            int status = switch (e.kind()) {
                case BAD_REQUEST -> 400;
                case UNAUTHORIZED -> 401;
                case NOT_FOUND -> 404;
                case CONFLICT -> 409;
            };
            send(ex, status, Json.error(e.getMessage()));
        } catch (NumberFormatException e) {
            send(ex, 400, Json.error("Invalid number"));
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, method + " " + path + " failed", e);
            send(ex, 500, Json.error("Something went wrong. Please try again."));
        }
    }

    private String route(String method, String path, Map<String, String> q, Map<String, String> b, String token) {
        switch (method + " " + path) {
            case "GET /api/categories":
                return Json.array(catalog.categories(), Json::category);
            case "GET /api/products": {
                ProductQuery pq = new ProductQuery(
                        q.get("q"),
                        q.containsKey("category") && !q.get("category").isBlank() ? Long.valueOf(q.get("category")) : null,
                        parseSort(q.get("sort")),
                        q.containsKey("page") ? Integer.parseInt(q.get("page")) : 0,
                        q.containsKey("size") ? Integer.parseInt(q.get("size")) : 24);
                return Json.array(catalog.browse(pq), Json::product);
            }
            case "POST /api/register": {
                User u = auth.register(b.get("email"), b.get("name"), b.get("password"));
                String t = auth.login(b.get("email"), b.get("password"));
                return "{\"token\":" + Json.str(t) + ",\"user\":" + Json.user(u) + "}";
            }
            case "POST /api/login": {
                String t = auth.login(b.get("email"), b.get("password"));
                return "{\"token\":" + Json.str(t) + ",\"user\":" + Json.user(auth.requireUser(t)) + "}";
            }
            case "POST /api/logout":
                auth.logout(token);
                return "{\"ok\":true}";
            case "GET /api/me":
                return Json.user(auth.requireUser(token));
            case "GET /api/cart":
                return Json.cart(cart.view(auth.requireUser(token).userId()));
            case "POST /api/cart/add":
                return Json.cart(cart.add(auth.requireUser(token).userId(),
                        Long.parseLong(b.get("productId")), Integer.parseInt(b.getOrDefault("quantity", "1"))));
            case "POST /api/cart/update":
                return Json.cart(cart.update(auth.requireUser(token).userId(),
                        Long.parseLong(b.get("productId")), Integer.parseInt(b.get("quantity"))));
            case "POST /api/checkout":
                return Json.order(orders.checkout(auth.requireUser(token).userId(), b.get("address")));
            case "GET /api/orders":
                return Json.array(orders.history(auth.requireUser(token).userId()), Json::order);
            default:
                if ("GET".equals(method) && path.startsWith("/api/products/")) {
                    return Json.product(catalog.product(Long.parseLong(path.substring("/api/products/".length()))));
                }
                return null;
        }
    }

    private static ProductQuery.Sort parseSort(String s) {
        if (s == null || s.isBlank()) {
            return ProductQuery.Sort.RELEVANCE;
        }
        try {
            return ProductQuery.Sort.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ShopException(ShopException.Kind.BAD_REQUEST, "Unknown sort " + s);
        }
    }

    private void handleStatic(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/") || path.isEmpty()) {
            path = "/index.html";
        }
        if (path.contains("..")) {
            ex.sendResponseHeaders(400, -1);
            ex.close();
            return;
        }
        try (InputStream in = ApiServer.class.getResourceAsStream("/static" + path)) {
            if (in == null) {
                byte[] msg = "Not found".getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(404, msg.length);
                ex.getResponseBody().write(msg);
                ex.close();
                return;
            }
            byte[] bytes = in.readAllBytes();
            ex.getResponseHeaders().set("Content-Type", contentType(path));
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private static String contentType(String path) {
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".css")) return "text/css; charset=utf-8";
        if (path.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (path.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }

    private static void send(HttpExchange ex, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String readBody(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            return new String(in.readNBytes(64 * 1024), StandardCharsets.UTF_8);
        }
    }

    static Map<String, String> parseForm(String raw) {
        Map<String, String> out = new HashMap<>();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String k = eq < 0 ? pair : pair.substring(0, eq);
            String v = eq < 0 ? "" : pair.substring(eq + 1);
            out.put(URLDecoder.decode(k, StandardCharsets.UTF_8), URLDecoder.decode(v, StandardCharsets.UTF_8));
        }
        return out;
    }
}
