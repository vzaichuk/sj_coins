package com.softjourn.coin.server.exceptions;


public class ErisAccountNotFoundException extends RuntimeException {

    public ErisAccountNotFoundException() {
        super("Eris account not found");
    }
}
