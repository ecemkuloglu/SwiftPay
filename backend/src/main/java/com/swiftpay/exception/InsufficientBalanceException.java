package com.swiftpay.exception;

public class InsufficientBalanceException extends BusinessException {

    public InsufficientBalanceException() {
        super(ErrorCode.INSUFFICIENT_BALANCE);
    }
}
