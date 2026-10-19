package com.auction.demo;

import com.auction.dto.Dtos.AuctionRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Sample listings used to make a fresh install feel alive. Images come from picsum.photos (seeded, stable). */
final class DemoCatalog {

    private record Item(String title, String description, String category, String imageSeed, int start, int increment) {}

    private static final List<Item> ITEMS = List.of(
            new Item("Vintage Leica M3 Film Camera", "Classic 1950s rangefinder in excellent working condition. Includes leather case and 50mm f/2 Summicron lens.", "Electronics", "leica", 800, 25),
            new Item("Mid-Century Walnut Armchair", "Restored 1960s Danish lounge chair, original teak legs, new wool upholstery. A true statement piece.", "Furniture", "armchair", 250, 10),
            new Item("First Edition 'The Hobbit' (1937)", "Rare early printing with original dust jacket. Light foxing, binding tight. Comes with a certificate of authenticity.", "Collectibles", "hobbit-book", 1500, 50),
            new Item("Mechanical Automatic Dive Watch", "Swiss automatic movement, 300m water resistance, sapphire crystal. Serviced last year, running +2s/day.", "Watches", "divewatch", 450, 15),
            new Item("Abstract Oil Painting - 'Dusk'", "Original 90x120cm oil on canvas by an emerging local artist. Signed and framed in oak.", "Art", "oilpainting", 300, 20),
            new Item("Gaming Laptop RTX 4070", "16-inch 165Hz display, 32GB RAM, 1TB NVMe. Barely used, with box and warranty remaining.", "Electronics", "gaminglaptop", 900, 30),
            new Item("Signed Basketball Jersey", "Authentic game-style jersey signed by a Hall of Fame legend. Comes in a UV-protected display case.", "Collectibles", "jersey", 600, 25),
            new Item("Handmade Persian Rug 2x3m", "Hand-knotted wool and silk blend, deep reds and blues, circa 1980. Professionally cleaned.", "Home", "persianrug", 700, 30),
            new Item("Electric Guitar - Sunburst Stratocaster", "American-made 1998 model with maple neck. New strings and a fresh setup, hard case included.", "Music", "stratocaster", 650, 20),
            new Item("Antique Brass Telescope", "Victorian-era 3-draw brass telescope with leather grip, optics clear. Beautiful desk piece.", "Antiques", "telescope", 180, 10),
            new Item("Limited Edition Sneakers - Size 10", "Deadstock, never worn, original box and receipts. Highly sought-after colourway.", "Fashion", "sneakers", 320, 15),
            new Item("Drone with 4K Camera Bundle", "Foldable drone, 3 batteries, ND filters and carry bag. Flown fewer than 10 hours.", "Electronics", "drone", 400, 15));

    private DemoCatalog() {}

    static int size() {
        return ITEMS.size();
    }

    static AuctionRequest request(int index, int durationMinutes) {
        Item i = ITEMS.get(Math.floorMod(index, ITEMS.size()));
        return new AuctionRequest(
                i.title(),
                i.description(),
                "https://picsum.photos/seed/" + i.imageSeed() + "/800/600",
                i.category(),
                BigDecimal.valueOf(i.start()).setScale(2),
                BigDecimal.valueOf(i.increment()).setScale(2),
                durationMinutes);
    }

    static AuctionRequest random(int durationMinutes) {
        return request(ThreadLocalRandom.current().nextInt(ITEMS.size()), durationMinutes);
    }
}
