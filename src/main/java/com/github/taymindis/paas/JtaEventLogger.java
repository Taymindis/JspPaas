package com.github.taymindis.paas;

public interface JtaEventLogger {

    void log(String sql, Object... args);

    void error(Exception e);

}
