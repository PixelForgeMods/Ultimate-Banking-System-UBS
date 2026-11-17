package net.austizz.ultimatebankingsystem.job;

/** Pure validation for a manifest item held at a Courier destination. */
final class CourierDeliveryMatcher {
    private static final String ACCEPTED = "ACCEPTED";

    private CourierDeliveryMatcher() {
    }

    static boolean matches(String orderStatus, String orderItemId, int orderQuantity,
                           String heldItemId, int heldCount) {
        return ACCEPTED.equals(orderStatus)
                && orderItemId != null && heldItemId != null
                && orderQuantity > 0 && heldCount >= orderQuantity
                && orderItemId.equals(heldItemId);
    }
}
