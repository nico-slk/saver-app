package com.saver.account;

/** CHECKING = cuenta de uso diario; SAVINGS e INVESTMENT cuentan como ahorro. */
public enum AccountKind {
    CHECKING, CASH, WALLET, SAVINGS, INVESTMENT;

    public boolean isSavings() {
        return this == SAVINGS || this == INVESTMENT;
    }
}
