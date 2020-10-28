package com.github.taymindis.paas;

public class ArgHandler {
    public ArgHandler(ArgEnum e) {
        this.argEnum = e;
    }
    public ArgHandler(ArgEnum e, String argKey) {
        this.argEnum = e;
        this.argKey = argKey;
    }
    protected ArgEnum argEnum;
    protected String argKey;
}
