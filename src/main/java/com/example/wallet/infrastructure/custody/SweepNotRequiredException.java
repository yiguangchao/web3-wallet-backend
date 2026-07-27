package com.example.wallet.infrastructure.custody;

public class SweepNotRequiredException extends RuntimeException {

    public SweepNotRequiredException(String message) {
        super(message);
    }
}
