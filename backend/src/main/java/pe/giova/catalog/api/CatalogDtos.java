package pe.giova.catalog.api;

import java.util.List;

public final class CatalogDtos {
    private CatalogDtos() {
    }

    public record Brand(String id, String name, String slug, String logoUrl, String displayColor,
                        boolean featured, int displayOrder) {
    }

    public record Category(String id, String name, String slug, String iconKey, int displayOrder,
                           long productCount) {
    }

    public record ProductBrand(String id, String name, String slug) {
    }

    public record ProductCategory(String id, String name, String slug, String iconKey) {
    }

    public record Product(String id, String slug, String sku, String name, String description,
                          String badge, String imageUrl, ProductBrand brand, ProductCategory category) {
    }

    public record Page<T>(List<T> items, long totalItems, int page, int size, int totalPages) {
    }

    public record Problem(String type, String title, int status, String detail, String code) {
    }
}
