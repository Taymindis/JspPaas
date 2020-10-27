package com.github.taymindis.paas;

public class DefaultTransactionLogger implements EventTransactionLogger {
    @Override
    public void log(String sql, Object... args) { }

    @Override
    public void error(Exception e) { }

}
