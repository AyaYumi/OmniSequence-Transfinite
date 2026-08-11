package com.atir.molecularmanipulator.crafting;

import com.atir.molecularmanipulator.api.crafting.OmniBatchDelivery;
import com.atir.molecularmanipulator.api.crafting.OmniBatchRequest;

import java.util.Objects;

/** Server-thread implementation of the public single-use delivery handshake. */
public final class MolecularOmniBatchDelivery implements OmniBatchDelivery {
    private final OmniBatchRequest request;
    private final Thread ownerThread = Thread.currentThread();
    private State state = State.OPEN;
    private Receipt receipt;
    private Rejection rejection;
    private boolean sealed;

    public MolecularOmniBatchDelivery(OmniBatchRequest request) {
        this.request = Objects.requireNonNull(request, "request");
    }

    @Override
    public OmniBatchRequest request() {
        requireOpenThread();
        return request;
    }

    @Override
    public void accept(Receipt receipt) {
        complete(State.ACCEPTED, Objects.requireNonNull(receipt, "receipt"), null);
    }

    @Override
    public void reject(Rejection rejection) {
        complete(State.REJECTED, null, Objects.requireNonNull(rejection, "rejection"));
    }

    public void seal() {
        requireOwnerThread();
        sealed = true;
        if (state == State.OPEN) {
            state = State.REJECTED;
            rejection = Rejection.reject(RejectReason.INTERNAL_ERROR);
        }
    }

    public boolean accepted() {
        requireOwnerThread();
        return state == State.ACCEPTED;
    }

    public Receipt receipt() {
        requireOwnerThread();
        return receipt;
    }

    public Rejection rejection() {
        requireOwnerThread();
        return rejection;
    }

    private void complete(State completed, Receipt acceptedReceipt,
            Rejection rejectedReason) {
        requireOpenThread();
        if (state != State.OPEN) {
            throw new IllegalStateException("Omni batch delivery was already completed");
        }
        state = completed;
        receipt = acceptedReceipt;
        rejection = rejectedReason;
    }

    private void requireOpenThread() {
        requireOwnerThread();
        if (sealed) {
            throw new IllegalStateException("Omni batch delivery is no longer active");
        }
    }

    private void requireOwnerThread() {
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException("Omni batch delivery must complete synchronously");
        }
    }

    private enum State {
        OPEN,
        ACCEPTED,
        REJECTED
    }
}
