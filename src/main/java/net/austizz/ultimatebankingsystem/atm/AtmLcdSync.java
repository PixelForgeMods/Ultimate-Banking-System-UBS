package net.austizz.ultimatebankingsystem.atm;

import net.minecraft.nbt.CompoundTag;

/** Encodes only display information, keeping both entered PINs server-private. */
public final class AtmLcdSync {
    private AtmLcdSync() {}
    public static CompoundTag save(AtmLcdState.Display display) {
        var tag = new CompoundTag();
        tag.putString("status", display.status().name()); tag.putString("language", display.language().name());
        tag.putInt("pin_length", display.pinLength()); tag.putString("pin_message", display.pinMessage());
        tag.putString("amount", display.amount()); tag.putString("balance", display.balance()); tag.putString("details", display.details());
        tag.putString("action", display.action().name()); tag.putBoolean("success", display.success()); tag.putString("notice", display.notice()); tag.putInt("bill_page", display.billPage());
        tag.putInt("limit_mode",display.limitMode()); tag.putInt("limit_duration",display.limitDurationMinutes());
        return tag;
    }
    public static AtmLcdState.Display load(CompoundTag tag) {
        AtmLcdState.Status status; AtmLcdState.Language language; AtmLcdState.Action action;
        try { status = AtmLcdState.Status.valueOf(tag.getString("status")); } catch (IllegalArgumentException ex) { status = AtmLcdState.Status.WELCOME; }
        try { language = AtmLcdState.Language.valueOf(tag.getString("language")); } catch (IllegalArgumentException ex) { language = AtmLcdState.Language.ENGLISH; }
        try { action = AtmLcdState.Action.valueOf(tag.getString("action")); } catch (IllegalArgumentException ex) { action = AtmLcdState.Action.NONE; }
        return new AtmLcdState.Display(status, language, Math.clamp(tag.getInt("pin_length"), 0, 4), tag.getString("pin_message"),
                tag.getString("amount"), tag.getString("balance"), tag.getString("details"), action, tag.getBoolean("success"), tag.getString("notice"), Math.max(0, tag.getInt("bill_page")), tag.getInt("limit_mode"), tag.getInt("limit_duration"));
    }
}
