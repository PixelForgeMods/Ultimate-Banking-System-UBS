package net.austizz.ultimatebankingsystem.events;

import net.austizz.ultimatebankingsystem.account.AccountHolder;

import java.math.BigDecimal;
import java.util.Objects;

/** Balance-change event published by banking code. */
public final class UbsBalanceChangedEvent {
    private final AccountHolder account;
    private final BigDecimal newBalance;
    private final BigDecimal changeAmount;
    private final boolean positive;

    public UbsBalanceChangedEvent(AccountHolder account, BigDecimal newBalance, BigDecimal changeAmount,
                                  boolean positive) {
        this.account = Objects.requireNonNull(account, "account");
        this.newBalance = Objects.requireNonNull(newBalance, "newBalance");
        this.changeAmount = Objects.requireNonNull(changeAmount, "changeAmount");
        this.positive = positive;
    }

    public AccountHolder getAccount() {
        return account;
    }

    public BigDecimal getNewBalance() {
        return newBalance;
    }

    public BigDecimal getChangeAmount() {
        return changeAmount;
    }

    public boolean isPositiveNumber() {
        return positive;
    }
}
