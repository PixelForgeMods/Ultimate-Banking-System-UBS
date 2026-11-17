package net.austizz.ultimatebankingsystem.api.job;

import org.jetbrains.annotations.ApiStatus;

/** Immutable public metadata for one registered UBS career. */
@ApiStatus.AvailableSince("2.2.0")
public record ApiJobDefinition(
        String id,
        String displayName,
        String description,
        String iconItemId,
        String buyerName,
        String catalogHeading,
        String activityLabel,
        int baseValueCents
) {
    public ApiJobDefinition {
        id = id == null ? "" : id;
        displayName = displayName == null ? "" : displayName;
        description = description == null ? "" : description;
        iconItemId = iconItemId == null ? "" : iconItemId;
        buyerName = buyerName == null ? "" : buyerName;
        catalogHeading = catalogHeading == null ? "" : catalogHeading;
        activityLabel = activityLabel == null ? "" : activityLabel;
        baseValueCents = Math.max(0, baseValueCents);
    }
}
