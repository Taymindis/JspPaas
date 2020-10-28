package com.github.taymindis.paas;

public class DefaultJtaEventLogger implements JtaEventLogger {
    @Override
    public void log(String sql, Object... args) { }

    @Override
    public void error(Exception e) { }

}
