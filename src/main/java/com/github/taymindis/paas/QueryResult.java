package com.github.taymindis.paas;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class QueryResult  {

    private final Map<String, List<Object>> vMap;
    private int index = -1;
    private final int size;

    public QueryResult(Map<String, List<Object>> vMap, int totalSize) {
        this.vMap = vMap;
        this.size = totalSize;
    }

    public Object get(String colName) {
        return vMap.get(colName).get(index);
    }

    /**
     * @return first column value
     */
    public Object get() {
        List<Object> objects = vMap.values().iterator().next();
        if(objects != null && objects.size() > 0) {
            return objects.get(index);
        }
        return null;
    }

    /**
     * @return all the columns name
     */
    public Set<String> getAllColumns() {
        return vMap.keySet();
    }

    public boolean next() {
        return ++index < this.size;
    }

    public int getSize() {
        return size;
    }
}
