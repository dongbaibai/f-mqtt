package com.fmqtt.subscription.exception;

public class SubscriptionLockTimeoutException extends RuntimeException {

    public SubscriptionLockTimeoutException(String message) {
        super(message);
    }
}
