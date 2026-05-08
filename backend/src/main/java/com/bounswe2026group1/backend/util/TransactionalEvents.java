package com.bounswe2026group1.backend.util;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Helper for deferring side effects (typically SSE broadcasts) until after the surrounding
 * transaction commits. Outside a transaction the action runs immediately so callers don't
 * need to special-case test paths or non-transactional callers.
 */
public final class TransactionalEvents {

    private TransactionalEvents() {}

    public static void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
