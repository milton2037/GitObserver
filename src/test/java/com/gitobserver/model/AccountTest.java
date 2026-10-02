package com.gitobserver.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class AccountTest {
    @Test
    void toStringDoesNotExposeTheToken() {
        Account account = new Account();
        account.setAlias("Trabajo");
        account.setToken("ghp_supersecretvalue");
        assertFalse(account.toString().contains("ghp_"));
        assertFalse(account.toString().contains("supersecret"));
    }
}
