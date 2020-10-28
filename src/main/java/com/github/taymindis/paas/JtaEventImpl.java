package com.github.taymindis.paas;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.jsp.PageContext;
import javax.sql.DataSource;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;


/**
 * dispatching in between web container with transactional
 * sample configuration before use
 */
//     under tomcat's context.xml <Context /> block
//    <ResourceLink name="jdbc/DBLink"
//    global="jdbc/globalDs"
//    auth="Container"
//    type="javax.sql.DataSource" />
//    under tomcat's server.xml <GlobalNamingResources />
//
//     <Resource name="jdbc/globalDs"
//    global="jdbc/globalDs"
//    auth="Container"
//    singleton="true"
//    defaultAutoCommit="false"
//    type="javax.sql.DataSource"
//    driverClassName="com.microsoft.sqlserver.jdbc.SQLServerDriver"
//    url="jdbc:sqlserver://localhost:1433;databaseName=myDB;"
//    username="taymindis"
//    password="MypassworD"
//    maxActive="100"
//    maxIdle="20"
//    minIdle="5"
//    maxWait="10000"/>
public class JtaEventImpl extends Paas implements JtaEvent {
    private Object result;
    private Connection connection = null;
    private DataSource _ds;
    private JtaEventLogger logger;
//    private static final EventTransactionLogger defaultLog;
    //    private Savepoint _sp = null;
    private static final ConcurrentHashMap<String, DataSource> dsMaps = new ConcurrentHashMap<>();
    private boolean hasReleased;
    private boolean rollbackOnError;

//    static {
//        defaultLog = new EventTransactionLogger() {
//            @Override
//            public void log(String sql, Object ...args) {
//
//            }
//        };
//    }

    /**
     * dispatching between the file via web container
     *
     * @param jspPath resource path
     * @return OJHDispatcher
     * @throws IOException      IOException
     * @throws ServletException ServletException
     */
    public JtaEventImpl dispatch(String jspPath) throws ServletException, IOException {
        clearPreviousStatus();
        this._pageContext.include(resourcePath + jspPath.replace(splitter, "/") + suffix);

        return this;
    }

    public void setResult(Object rs) {
        this.result = rs;
    }

    public <T extends Object> T  getResult() {
        return (T) this.result;
    }

    @Override
    public <T extends Object> T  getResult(long timeout, TimeUnit unit) {
        return (T) this.result;
    }

    @Override
    public boolean isDone() {
        return this.result != null;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    public String queryOne(final String sql, Object... sqlParams) throws SQLException {
        return query(sql, QueryReturnType.SINGLE_VALUE, sqlParams);
    }

    @Override
    public String queryValueRowByComma(String sql, Object... sqlParams) throws SQLException {
        return query(sql, QueryReturnType.SINGLE_LIST_VALUE_BY_COMMA, sqlParams);
    }

    @Override
    public String queryValueRowBySemicolon(String sql, Object... sqlParams) throws SQLException {
        return query(sql, QueryReturnType.SINGLE_LIST_VALUE_BY_SEMICOLON, sqlParams);
    }

    @Override
    public String queryValueRowByVertical(String sql, Object... sqlParams) throws SQLException {
        return query(sql, QueryReturnType.SINGLE_LIST_VALUE_BY_VERTICAL_LINE, sqlParams);
    }

    @Override
    public ArrayList queryToList(String sql, Object... sqlParams) throws SQLException {
        return query(sql, QueryReturnType.QUERY_TO_LIST, sqlParams);
    }

    @Override
    public ArrayList queryColumns(String sql, Object... sqlParams) throws SQLException {
        return query(sql, QueryReturnType.COLUMNS_ONLY, sqlParams);
    }

    @Override
    public QueryResult query(final String sql, Object... sqlParams) throws SQLException {
        return query(sql, QueryReturnType.ALL, sqlParams);
    }

    /**
     * @param sql       sql
     * @param sqlParams sqlParams
     * @return row affected
     * @throws SQLException SQLException
     */
    public int executeWithKey(String sql, Object... sqlParams) throws SQLException {
        int rowAffectedOrKey;
        ResultSet rs = null;
        Statement stmt = null;
        try {
            logger.log(sql, sqlParams);
            if (sqlParams.length > 0) {
                int i = 0;
                PreparedStatement pstmt = (PreparedStatement) (stmt = this.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS));
                for (Object o : sqlParams) {
                    pstmt.setObject(++i, o);
                }
                pstmt.executeUpdate();
                rs = stmt.getGeneratedKeys();
            } else {
                stmt = this.createStatement();
                stmt.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS);
                rs = stmt.getGeneratedKeys();
            }
            rowAffectedOrKey = rs.next() ? rs.getInt(1) : -1;
            return rowAffectedOrKey;
        } catch (SQLException throwables) {
            throw throwables;
        } finally {
            if (rs != null) {
                rs.close();
            }
            if (stmt != null) {
                stmt.close();
            }
//            this.commit();
        }
    }

    /**
     * @param sql sql
     * @return row affected
     * @throws SQLException SQLException
     */
    public int execute(String sql, Object... sqlParams) throws SQLException {
        int rowAffectedOrKey;
        Statement stmt = null;
        try {
            logger.log(sql, sqlParams);
            if (sqlParams.length > 0) {
                int i = 0;
                PreparedStatement pstmt = (PreparedStatement) (stmt = this.prepareStatement(sql));
                for (Object o : sqlParams) {
                    pstmt.setObject(++i, o);
                }
                rowAffectedOrKey = pstmt.executeUpdate();
            } else {
                stmt = this.createStatement();
                rowAffectedOrKey = stmt.executeUpdate(sql);
            }

            return rowAffectedOrKey;
        } catch (SQLException throwables) {
            throw throwables;
        } finally {
            if (stmt != null) {
                stmt.close();
            }

//            this.commit();
        }
    }

    public void rollback() throws SQLException {
        if (connection != null) {
//            if (this._sp != null) {
//                connection.rollback(this._sp);
//            } else {
            connection.rollback();
//            }
        }
    }

    public void commit() throws SQLException {
        if (connection != null) {
            connection.commit();
        }
    }

    public void release() throws SQLException {
        release(true);
    }

    public void close() throws SQLException {
        if (connection != null) {
            this.connection.close();
        }
    }

    public void release(boolean committable) throws SQLException {
        if(this.hasReleased) {
            return;
        }
        if(committable) {
            try {
                this.commit();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
        }
        this.close();
        this.hasReleased = true;
    }

    @Override
    public boolean isRollbackOnError() {
        return rollbackOnError;
    }

    @Override
    public void setRollbackOnError(boolean rollbackOnError) {
        this.rollbackOnError = rollbackOnError;
    }

    private Connection getConnection() throws SQLException {
        if (this.connection == null) {
            this.connection = _ds.getConnection();
//            this.connection.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
            if (this.connection.getAutoCommit()) {
                this.connection.setAutoCommit(false);
            }
//            this._sp = this.connection.setSavepoint();
        }

        return this.connection;
    }


    private PreparedStatement prepareStatement(String sql) throws SQLException {
        return this.getConnection().prepareStatement(sql);
    }

    private PreparedStatement prepareStatement(String sql, int returnGeneratedKeys) throws SQLException {
        return this.getConnection().prepareStatement(sql, returnGeneratedKeys);
    }

    private Statement createStatement() throws SQLException {
        return this.getConnection().createStatement();
    }

    private QueryResult toQueryResult(ResultSet rs) throws SQLException {
        return toQueryResult(rs, -1);
    }

    private QueryResult toQueryResult(ResultSet rs, int rowLimit) throws SQLException {
        Map<String, List<Object>> vMap = new HashMap<>();
        ResultSetMetaData rsmd = rs.getMetaData();
        int size;
        for (int i = 1, cc = rsmd.getColumnCount(); i <= cc; i++) {
            vMap.put(rsmd.getColumnName(i), new ArrayList());
        }
        Set<String> colNames = vMap.keySet();
        if (rowLimit == -1) {
            size = 0;
            while (rs.next()) {
                size++;
                for (String c : colNames) {
                    vMap.get(c).add(rs.getObject(c));
                }
            }
        } else {
            for (size = 0; size < rowLimit && rs.next(); size++) {
                for (String c : colNames) {
                    vMap.get(c).add(rs.getObject(c));
                }
            }
        }

        QueryResult queryResult = new QueryResult(vMap, size);
        return queryResult;
    }

    public <T extends Object> T  query(final String sql, QueryReturnType returnType, Object... sqlParams) throws SQLException {
        ResultSet rs = null;
        Statement stmt = null;
        try {
            logger.log(sql, sqlParams);
            switch (returnType) {
                case ALL:
                case COLUMNS_ONLY:
                case QUERY_TO_LIST:
                    if (sqlParams.length > 0) {
                        PreparedStatement pstmt = (PreparedStatement) (stmt = this.prepareStatement(sql));
                        int i = 1;
                        for (Object o : sqlParams) {
                            pstmt.setObject(i++, o);
                        }
                        rs = pstmt.executeQuery();
                    } else {
                        stmt = this.createStatement();
                        rs = stmt.executeQuery(sql);
                    }

                    switch (returnType) {
                        case QUERY_TO_LIST:
                            QueryResult qr = toQueryResult(rs);
                            List<Object> list = new ArrayList<>();
                            while (qr.next()) {
                                list.add(qr.get());
                            }
                            return (T) list;
                        case COLUMNS_ONLY:
                            return (T) new ArrayList<>(toQueryResult(rs).getAllColumns());
                        case ALL:
                        default:
                            return (T) toQueryResult(rs);
                    }
                case SINGLE_VALUE:
                    if (sqlParams.length > 0) {
                        PreparedStatement pstmt = (PreparedStatement) (stmt = this.prepareStatement(sql));
                        int i = 1;
                        for (Object o : sqlParams) {
                            pstmt.setObject(i++, o);
                        }
                        rs = pstmt.executeQuery();
                    } else {
                        stmt = this.createStatement();
                        rs = stmt.executeQuery(sql);
                    }
                    return (T) (rs.next() ? String.valueOf(rs.getObject(1)) : null);
                case SINGLE_LIST_VALUE:
                    if (sqlParams.length > 0) {
                        PreparedStatement pstmt = (PreparedStatement) (stmt = this.prepareStatement(sql));
                        int i = 1;
                        for (Object o : sqlParams) {
                            pstmt.setObject(i++, o);
                        }
                        rs = pstmt.executeQuery();
                    } else {
                        stmt = this.createStatement();
                        rs = stmt.executeQuery(sql);
                    }
                    List<String> list = new ArrayList<>();
                    while (rs.next()) {
                        list.add(String.valueOf(rs.getObject(1)));
                    }
                    return (T) list;
                case SINGLE_LIST_VALUE_BY_COMMA:
                case SINGLE_LIST_VALUE_BY_VERTICAL_LINE:
                case SINGLE_LIST_VALUE_BY_SEMICOLON:
                    String separator;
                    switch (returnType) {
                        case SINGLE_LIST_VALUE_BY_COMMA:
                            separator = ",";
                            break;
                        case SINGLE_LIST_VALUE_BY_VERTICAL_LINE:
                            separator = "|";
                            break;
                        case SINGLE_LIST_VALUE_BY_SEMICOLON:
                        default:
                            separator = ";";
                    }
                    StringBuilder vals = new StringBuilder();
                    if (sqlParams.length > 0) {
                        PreparedStatement pstmt = (PreparedStatement) (stmt = this.prepareStatement(sql));
                        int i = 1;
                        for (Object o : sqlParams) {
                            pstmt.setObject(i++, o);
                        }
                        rs = pstmt.executeQuery();
                    } else {
                        stmt = this.createStatement();
                        rs = stmt.executeQuery(sql);
                    }
                    while (rs.next()) {
                        vals.append(rs.getObject(1)).append(separator);
                    }
                    return (T) vals.toString();
            }
        } finally {
            if (rs != null) {
                rs.close();
            }
            if (stmt != null) {
                stmt.close();
            }
        }

        return null;

    }

    private void init(PageContext pc, String jndiResource, JtaEventLogger logger) throws NamingException {
        this.result = null;
        this.logger = logger;

        synchronized (dsMaps) {
            if (dsMaps.contains(jndiResource)) {
                _ds = dsMaps.get(jndiResource);
            } else {
//            DataSource ds = (DataSource) ctx.lookup("java:/comp/env/jdbc/DBLink");
//			XADataSource ds = (XADataSource) ctx.lookup("java:/comp/env/jdbc/DBLink");
//			XAConnection xaConn = ds.getXAConnection();
//			XAResource xaRes  = xaConn.getXAResource();
//			con = xaConn.getConnection();
/**            Or using below way also workable
 Context initCtx  = (Context) ctx.lookup("java:/comp/env");
 DataSource ds = (DataSource) initCtx.lookup("jdbc/DBLink");
 **/
                Context ctx = null;
                try {
                    ctx = new InitialContext();
                    _ds = (DataSource) ctx.lookup(jndiResource);
                } finally {
                    if (ctx != null) {
                        ctx.close();
                    }
                }
                dsMaps.put(jndiResource, _ds);
            }
            hasReleased = false;
        }
    }

    protected JtaEventImpl(PageContext pc, String jndiResource, JtaEventLogger log) throws NamingException {
        super(pc);
        init(pc, jndiResource, log);
    }

//    protected EventTransactionImpl(PageContext pc, String jndiResource) throws NamingException {
//        super(pc);
//        init(pc, jndiResource, defaultLog);
//    }
}
