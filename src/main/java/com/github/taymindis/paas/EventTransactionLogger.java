package com.github.taymindis.paas;

public interface EventTransactionLogger  {

    void log(String sql, Object... args);

    void error(Exception e);

}
