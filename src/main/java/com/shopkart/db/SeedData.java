package com.shopkart.db;

import com.shopkart.dao.ProductDao;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Loads a demo catalogue (fictional brands) the first time the app starts on an empty database. */
public final class SeedData {

    private SeedData() { }

    private record P(String name, String brand, String desc, long price, long mrp, int stock, double rating) { }

    public static void loadIfEmpty(Database db) throws SQLException {
        ProductDao dao = new ProductDao(db.dialect());
        try (Connection conn = db.getConnection()) {
            if (dao.countProducts(conn) > 0) {
                return;
            }
            conn.setAutoCommit(false);
            try {
                for (Map.Entry<String, P[]> e : catalogue().entrySet()) {
                    long categoryId = dao.insertCategory(conn, e.getKey());
                    for (P p : e.getValue()) {
                        dao.insertProduct(conn, categoryId, p.name, p.brand, p.desc,
                                p.price * 100, p.mrp * 100, p.stock, p.rating);
                    }
                }
                conn.commit();
            } catch (SQLException | RuntimeException ex) {
                conn.rollback();
                throw ex;
            }
        }
    }

    private static Map<String, P[]> catalogue() {
        Map<String, P[]> m = new LinkedHashMap<>();
        m.put("Mobiles", new P[]{
                new P("Nova X5 (Midnight Blue, 128 GB)", "Nova", "6.5\" AMOLED, 50 MP triple camera, 5000 mAh battery, 5G", 17999, 21999, 42, 4.3),
                new P("Nova X5 Pro (Frost White, 256 GB)", "Nova", "120 Hz AMOLED, 108 MP camera, 67 W fast charging", 24999, 29999, 18, 4.4),
                new P("Pixelon 8a (Charcoal, 128 GB)", "Pixelon", "Clean Android, 7 years of updates, excellent low-light camera", 32999, 39999, 9, 4.5),
                new P("Zentro Z12 (Ocean Green, 64 GB)", "Zentro", "Budget 4G phone with 6000 mAh battery", 8499, 10999, 75, 4.0),
                new P("Zentro Z20 5G (Graphite, 128 GB)", "Zentro", "Dimensity chipset, 90 Hz display, dual SIM 5G", 12999, 15999, 0, 4.1),
        });
        m.put("Electronics", new P[]{
                new P("AirBeat Pro Wireless Earbuds", "AirBeat", "Active noise cancellation, 32 h playback, IPX5", 2999, 5999, 120, 4.2),
                new P("SoundCore 20 W Bluetooth Speaker", "SoundCore", "Portable, waterproof, 15 h battery", 1799, 3499, 64, 4.3),
                new P("VoltEdge 20000 mAh Power Bank", "VoltEdge", "22.5 W fast charging, USB-C in/out", 1499, 2499, 200, 4.4),
                new P("LumaBook 14 Laptop (i5, 16 GB, 512 GB SSD)", "Luma", "14\" FHD IPS, backlit keyboard, 1.4 kg", 54990, 68990, 11, 4.4),
                new P("Visio 43\" 4K Smart TV", "Visio", "HDR10, Dolby Audio, built-in streaming apps", 23999, 34999, 7, 4.2),
                new P("FitPulse 2 Smartwatch", "FitPulse", "AMOLED, SpO2, heart-rate, 14-day battery", 3499, 7999, 88, 4.0),
        });
        m.put("Fashion", new P[]{
                new P("Men's Slim Fit Cotton Shirt", "UrbanThread", "100% cotton, full sleeve, machine wash", 699, 1499, 150, 4.1),
                new P("Women's Printed Kurta", "Saanjh", "Rayon, straight fit, calf length", 849, 1999, 95, 4.3),
                new P("Running Shoes for Men", "StrideMax", "Lightweight mesh upper, cushioned sole", 1599, 3499, 60, 4.2),
                new P("Unisex Canvas Backpack 28 L", "TrekNest", "Laptop sleeve, water-resistant", 999, 2199, 70, 4.4),
                new P("Women's Analog Watch", "Chrona", "Stainless steel strap, 3 ATM water resistance", 1299, 2999, 33, 4.0),
        });
        m.put("Home & Kitchen", new P[]{
                new P("Non-stick Cookware Set (3 pcs)", "KitchenCraft", "Induction base, PFOA-free coating", 1499, 2999, 45, 4.2),
                new P("Stainless Steel Water Bottle 1 L", "HydroSteel", "Double-wall vacuum insulated, 24 h cold", 549, 999, 300, 4.5),
                new P("Cotton Double Bedsheet with 2 Pillow Covers", "HomeNest", "180 TC, floral print", 649, 1599, 110, 4.1),
                new P("LED Desk Lamp", "Brightly", "3 colour modes, USB charging, touch control", 899, 1799, 54, 4.3),
        });
        m.put("Appliances", new P[]{
                new P("1.5 Ton 5 Star Inverter Split AC", "CoolAir", "Copper condenser, 4-in-1 convertible", 36990, 54990, 5, 4.3),
                new P("7 kg Front Load Washing Machine", "WashPro", "Inverter motor, in-built heater, 15 wash programs", 27490, 38990, 8, 4.4),
                new P("Mixer Grinder 750 W (3 Jars)", "KitchenCraft", "Stainless steel jars, overload protection", 2799, 4999, 40, 4.1),
                new P("Air Fryer 4.2 L", "CrispPot", "Rapid air technology, 8 presets", 3999, 7999, 25, 4.3),
        });
        m.put("Books", new P[]{
                new P("Data Structures and Algorithms Made Simple", "CodePress", "Paperback, 2nd edition, interview-focused", 499, 799, 80, 4.6),
                new P("Database Systems: Concepts in Practice", "CodePress", "Paperback, normalisation, SQL, transactions", 649, 999, 35, 4.5),
                new P("The Quiet Mountain (Novel)", "Inkwell", "Paperback, literary fiction", 299, 450, 140, 4.2),
        });
        return m;
    }
}
