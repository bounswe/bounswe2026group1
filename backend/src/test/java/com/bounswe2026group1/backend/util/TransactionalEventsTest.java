package com.bounswe2026group1.backend.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransactionalEventsTest {

    @AfterEach
    void clearSync() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void runAfterCommit_withoutActiveTransaction_runsImmediately() {
        AtomicInteger counter = new AtomicInteger();

        TransactionalEvents.runAfterCommit(counter::incrementAndGet);

        assertEquals(1, counter.get(), "outside a transaction the action must run inline");
    }

    @Test
    void runAfterCommit_insideActiveTransaction_defersUntilAfterCommit() {
        AtomicInteger counter = new AtomicInteger();

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        TransactionalEvents.runAfterCommit(counter::incrementAndGet);

        assertEquals(0, counter.get(), "action must not run before commit");

        // Drive the registered synchronizations as Spring would on commit
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }

        assertEquals(1, counter.get(), "action must run exactly once after commit");
    }

    @Test
    void runAfterCommit_registersOneSynchronizationPerCall() {
        AtomicInteger counter = new AtomicInteger();

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        TransactionalEvents.runAfterCommit(counter::incrementAndGet);
        TransactionalEvents.runAfterCommit(counter::incrementAndGet);

        assertEquals(2, TransactionSynchronizationManager.getSynchronizations().size());
        assertEquals(0, counter.get());

        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }

        assertEquals(2, counter.get());
    }
}
