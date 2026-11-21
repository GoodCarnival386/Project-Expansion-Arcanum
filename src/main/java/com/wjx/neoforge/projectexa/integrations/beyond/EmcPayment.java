package com.wjx.neoforge.projectexa.integrations.beyond;

import java.math.BigInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Server-thread payment for a single delivery. Refund exactly this debit if delivery fails. */
public final class EmcPayment {
    private EmcPayment() {}

    public static void commit(BigInteger cost, Supplier<BigInteger> balance, Consumer<BigInteger> setBalance, Runnable deliver) {
        BigInteger before = balance.get();
        if (cost.signum() < 0 || cost.compareTo(before) > 0) throw new IllegalStateException("refresh the preview");
        if (cost.signum() == 0) { deliver.run(); return; }
        setBalance.accept(before.subtract(cost));
        try {
            deliver.run();
        } catch (RuntimeException | Error failure) {
            setBalance.accept(balance.get().add(cost));
            throw failure;
        }
    }
}
