package com.shopkart;

import com.shopkart.dao.ProductDao;
import com.shopkart.db.Database;
import com.shopkart.db.SeedData;
import com.shopkart.model.Cart;
import com.shopkart.model.Order;
import com.shopkart.model.Product;
import com.shopkart.model.ProductQuery;
import com.shopkart.model.User;
import com.shopkart.service.AuthService;
import com.shopkart.service.CartService;
import com.shopkart.service.CatalogService;
import com.shopkart.service.OrderService;
import com.shopkart.service.PasswordHasher;
import com.shopkart.service.ShopException;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ShopKartTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    private Database db;
    private AuthService auth;
    private CatalogService catalog;
    private CartService cart;
    private OrderService orders;
    private User alice;

    @Before
    public void setUp() throws Exception {
        db = Database.inMemory("shop" + SEQ.incrementAndGet() + "_" + System.nanoTime());
        db.initSchema();
        SeedData.loadIfEmpty(db);
        auth = new AuthService(db);
        catalog = new CatalogService(db);
        cart = new CartService(db);
        orders = new OrderService(db, cart);
        alice = auth.register("alice@example.com", "Alice Rao", "correct-horse");
    }

    private Product productNamed(String fragment) {
        return catalog.browse(ProductQuery.search(fragment)).get(0);
    }

    private static void expect(ShopException.Kind kind, Runnable r) {
        try {
            r.run();
            fail("expected " + kind);
        } catch (ShopException e) {
            assertEquals(e.getMessage(), kind, e.kind());
        }
    }

    // ---------- auth ----------

    @Test
    public void passwordsAreHashedWithSaltAndVerify() {
        String h1 = PasswordHasher.hash("secret-123");
        String h2 = PasswordHasher.hash("secret-123");
        assertNotEquals("same password must hash differently (salt)", h1, h2);
        assertTrue(PasswordHasher.verify("secret-123", h1));
        assertFalse(PasswordHasher.verify("secret-124", h1));
        assertFalse(h1.contains("secret-123"));
    }

    @Test
    public void loginWorksAndRejectsBadCredentialsWithSameMessage() {
        String token = auth.login("ALICE@example.com ", "correct-horse");
        assertEquals(alice.userId(), auth.requireUser(token).userId());

        try { auth.login("alice@example.com", "wrong-pass"); fail(); }
        catch (ShopException wrongPw) {
            try { auth.login("nobody@example.com", "whatever1"); fail(); }
            catch (ShopException noUser) { assertEquals(wrongPw.getMessage(), noUser.getMessage()); }
        }
    }

    @Test
    public void duplicateEmailAndWeakPasswordAreRejected() {
        expect(ShopException.Kind.CONFLICT, () -> auth.register("alice@example.com", "Other", "password99"));
        expect(ShopException.Kind.BAD_REQUEST, () -> auth.register("bob@example.com", "Bob", "short"));
        expect(ShopException.Kind.BAD_REQUEST, () -> auth.register("not-an-email", "Bob", "password99"));
    }

    @Test
    public void logoutInvalidatesToken() {
        String token = auth.login("alice@example.com", "correct-horse");
        auth.logout(token);
        expect(ShopException.Kind.UNAUTHORIZED, () -> auth.requireUser(token));
    }

    // ---------- catalogue ----------

    @Test
    public void searchMatchesNameBrandAndCategoryCaseInsensitively() {
        assertFalse(catalog.browse(ProductQuery.search("EARBUDS")).isEmpty());
        assertTrue(catalog.browse(ProductQuery.search("nova")).stream().allMatch(p -> p.brand().equals("Nova")));
        assertFalse(catalog.browse(ProductQuery.search("mobiles")).isEmpty());
        assertTrue(catalog.browse(ProductQuery.search("no such thing")).isEmpty());
    }

    @Test
    public void multiWordSearchRequiresEveryTerm() {
        List<Product> r = catalog.browse(ProductQuery.search("nova pro"));
        assertEquals(1, r.size());
        assertTrue(r.get(0).name().contains("Pro"));
    }

    @Test
    public void sqlInjectionInSearchIsTreatedAsPlainText() throws Exception {
        List<Product> r = catalog.browse(ProductQuery.search("' OR '1'='1"));
        assertTrue(r.isEmpty());
        catalog.browse(ProductQuery.search("x'; DROP TABLE products; --"));
        try (Connection c = db.getConnection()) {
            assertTrue(new ProductDao(db.dialect()).countProducts(c) > 0);
        }
    }

    @Test
    public void likeWildcardsInSearchAreEscaped() {
        // "%" must match only the literal percent sign ("100% cotton"), not every product.
        List<Product> pct = catalog.browse(ProductQuery.search("%"));
        assertEquals(1, pct.size());
        assertTrue(pct.get(0).description().contains("100%"));
        assertTrue(catalog.browse(ProductQuery.search("_")).isEmpty());
    }

    @Test
    public void sortingAndPaginationWork() {
        List<Product> asc = catalog.browse(new ProductQuery(null, null, ProductQuery.Sort.PRICE_ASC, 0, 100));
        for (int i = 1; i < asc.size(); i++) {
            assertTrue(asc.get(i - 1).pricePaise() <= asc.get(i).pricePaise());
        }
        List<Product> page0 = catalog.browse(new ProductQuery(null, null, ProductQuery.Sort.PRICE_ASC, 0, 5));
        List<Product> page1 = catalog.browse(new ProductQuery(null, null, ProductQuery.Sort.PRICE_ASC, 1, 5));
        assertEquals(5, page0.size());
        assertEquals(asc.get(5).productId(), page1.get(0).productId());
    }

    @Test
    public void categoryFilterReturnsOnlyThatCategory() {
        long mobiles = catalog.categories().stream().filter(c -> c.name().equals("Mobiles")).findFirst().orElseThrow().categoryId();
        List<Product> r = catalog.browse(new ProductQuery(null, mobiles, null, 0, 50));
        assertEquals(5, r.size());
        assertTrue(r.stream().allMatch(p -> p.categoryName().equals("Mobiles")));
    }

    // ---------- cart ----------

    @Test
    public void addingSameProductTwiceIncrementsQuantity() {
        Product buds = productNamed("earbuds");
        cart.add(alice.userId(), buds.productId(), 1);
        Cart c = cart.add(alice.userId(), buds.productId(), 2);
        assertEquals(1, c.items().size());
        assertEquals(3, c.itemCount());
        assertEquals(3 * buds.pricePaise(), c.totalPaise());
    }

    @Test
    public void cannotAddMoreThanStockOrOutOfStockItems() {
        Product z20 = productNamed("Z20");           // seeded with stock 0
        expect(ShopException.Kind.CONFLICT, () -> cart.add(alice.userId(), z20.productId(), 1));
        Product ac = productNamed("Split AC");       // stock 5
        expect(ShopException.Kind.CONFLICT, () -> cart.add(alice.userId(), ac.productId(), 6));
        Product bottle = productNamed("Water Bottle");
        expect(ShopException.Kind.BAD_REQUEST, () -> cart.add(alice.userId(), bottle.productId(), 11));
    }

    @Test
    public void settingQuantityToZeroRemovesLine() {
        Product p = productNamed("Power Bank");
        cart.add(alice.userId(), p.productId(), 2);
        assertTrue(cart.update(alice.userId(), p.productId(), 0).isEmpty());
    }

    // ---------- checkout ----------

    @Test
    public void checkoutCreatesOrderReducesStockAndEmptiesCart() {
        Product p = productNamed("Power Bank");
        cart.add(alice.userId(), p.productId(), 2);
        Order o = orders.checkout(alice.userId(), "12 MG Road, Chennai 600001");

        assertEquals("PLACED", o.status());
        assertEquals(2 * p.pricePaise(), o.totalPaise());
        assertEquals(p.stock() - 2, catalog.product(p.productId()).stock());
        assertTrue(cart.view(alice.userId()).isEmpty());
        assertEquals(1, orders.history(alice.userId()).size());
    }

    @Test
    public void checkoutIsAllOrNothingWhenOneLineRunsOutOfStock() throws Exception {
        Product plentiful = productNamed("Power Bank");
        Product scarce = productNamed("Split AC");     // stock 5
        cart.add(alice.userId(), plentiful.productId(), 3);
        cart.add(alice.userId(), scarce.productId(), 2);

        // Someone else buys most of the AC stock after Alice filled her cart.
        try (Connection c = db.getConnection()) {
            assertTrue(new ProductDao(db.dialect()).decrementStock(c, scarce.productId(), 4));
        }
        expect(ShopException.Kind.CONFLICT, () -> orders.checkout(alice.userId(), "12 MG Road, Chennai 600001"));

        assertEquals("first line's stock reservation must be rolled back",
                plentiful.stock(), catalog.product(plentiful.productId()).stock());
        assertEquals(1, catalog.product(scarce.productId()).stock());
        assertEquals(2, cart.view(alice.userId()).items().size());
        assertTrue(orders.history(alice.userId()).isEmpty());
    }

    @Test
    public void orderHistoryKeepsPriceSnapshotAfterCatalogueChanges() throws Exception {
        Product p = productNamed("Desk Lamp");
        cart.add(alice.userId(), p.productId(), 1);
        orders.checkout(alice.userId(), "12 MG Road, Chennai 600001");

        try (Connection c = db.getConnection()) {
            new ProductDao(db.dialect()).updatePrice(c, p.productId(), p.pricePaise() / 2);
        }
        Order past = orders.history(alice.userId()).get(0);
        assertEquals(p.pricePaise(), past.items().get(0).unitPricePaise());
        assertEquals(p.pricePaise(), past.totalPaise());
    }

    @Test
    public void emptyCartAndShortAddressAreRejected() {
        expect(ShopException.Kind.BAD_REQUEST, () -> orders.checkout(alice.userId(), "12 MG Road, Chennai 600001"));
        cart.add(alice.userId(), productNamed("Power Bank").productId(), 1);
        expect(ShopException.Kind.BAD_REQUEST, () -> orders.checkout(alice.userId(), "short"));
    }
}
