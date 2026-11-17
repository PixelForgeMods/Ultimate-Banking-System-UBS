package net.austizz.ultimatebankingsystem.job;

import java.util.Locale;
import java.util.Objects;

/** Immutable metadata for a registered UBS job. */
public record JobDefinition(String id, String displayName, String description, String iconItemId,
                            String buyerName, String catalogHeading, String activityLabel,
                            int baseValueCents) {
    public JobDefinition(String id, String displayName, String description) {
        this(id, displayName, description, "minecraft:paper");
    }

    public JobDefinition(String id, String displayName, String description, String iconItemId) {
        this(id, displayName, description, iconItemId,
                (displayName == null || displayName.isBlank() ? id : displayName.trim()) + " Buyer",
                (displayName == null || displayName.isBlank() ? id : displayName.trim()) + " Catalog",
                "job activities", 500);
    }

    public JobDefinition {
        id = normalize(id);
        displayName = displayName == null || displayName.isBlank() ? id : displayName.trim();
        description = description == null ? "" : description.trim();
        iconItemId = iconItemId == null || iconItemId.isBlank() ? "minecraft:paper" : iconItemId.trim();
        buyerName = buyerName == null || buyerName.isBlank() ? displayName + " Buyer" : buyerName.trim();
        catalogHeading = catalogHeading == null || catalogHeading.isBlank()
                ? displayName + " Catalog" : catalogHeading.trim();
        activityLabel = activityLabel == null || activityLabel.isBlank() ? "job activities" : activityLabel.trim();
        baseValueCents = Math.max(1, baseValueCents);
        Objects.requireNonNull(id, "id");
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
