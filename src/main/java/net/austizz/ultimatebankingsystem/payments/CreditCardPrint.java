package net.austizz.ultimatebankingsystem.payments;

import net.minecraft.nbt.CompoundTag;

import java.util.Locale;

public record CreditCardPrint(String bank, String number, String holder, String expiry, String cvc) {
    public static CreditCardPrint fromTag(CompoundTag tag) {
        String digits = tag.getString(CreditCardService.TAG_CARD_NUMBER).replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return new CreditCardPrint("", "", "", "", "");
        digits = digits.substring(0, Math.min(19, digits.length()));
        String cvc = tag.getString(CreditCardService.TAG_CVC).replaceAll("[^0-9]", "");
        return new CreditCardPrint(clean(tag.getString(CreditCardService.TAG_BANK_NAME)),
                digits.replaceAll("(.{4})(?!$)", "$1 "),
                clean(tag.getString(CreditCardService.TAG_HOLDER_NAME)).toUpperCase(Locale.ROOT),
                CreditCardService.formatExpiryMonthYear(tag.getLong(CreditCardService.TAG_EXPIRY_AT)),
                cvc.substring(0, Math.min(4, cvc.length())));
    }

    private static String clean(String text) {
        String value = text.replaceAll("§.|\\p{Cntrl}", "").strip();
        return value.substring(0, Math.min(64, value.length()));
    }
}
