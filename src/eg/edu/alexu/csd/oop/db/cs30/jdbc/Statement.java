package eg.edu.alexu.csd.oop.db.cs30.jdbc;

import eg.edu.alexu.csd.oop.db.Database;
import eg.edu.alexu.csd.oop.db.cs30.DataBaseGenerator;
import eg.edu.alexu.csd.oop.db.cs30.queries.ExtractData;
import eg.edu.alexu.csd.oop.db.cs30.queries.Query;
import eg.edu.alexu.csd.oop.db.cs30.queries.QueryBuilder;

import java.sql.*;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.*;

public class Statement implements java.sql.Statement {

    private String path;
    private Database database;
    private Queue<String> batches;
    private Connection connection;
    private int timeoutSeconds;
    private SQLException sqlException;

    Statement(String path, Connection connection) {
        this.path = path;
        this.batches = new LinkedList<>();
        this.database = DataBaseGenerator.makeInstance();
        this.connection = connection;
        timeoutSeconds = Integer.MAX_VALUE;
    }

    @Override
    public void addBatch(String sql) throws SQLException {
        batches.offer(sql);
    }

    @Override
    public void clearBatch() throws SQLException {
        batches.clear();
    }

    @Override
    public int[] executeBatch() throws SQLException {
        // Execute each query in batches
        int[] executeBatches = new int[batches.size()];

        // There's a timeout
        ExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        Future<String> handler = executorService.submit(() ->
        {
            for (int i = 0, length = batches.size(); i < length && !Thread.interrupted(); i++)
            {
                executeBatches[i] = Statement.this.executeBatchQuery();
            }
            return null;
        });

        try {
            handler.get(timeoutSeconds, TimeUnit.SECONDS);
        }
        catch (Exception e) {
            handler.cancel(true);
            this.handleException(e);
        }

        executorService.shutdownNow();
        this.checkException();

        return executeBatches;
    }

    private int executeBatchQuery() throws SQLException {
        Query query = QueryBuilder.buildQuery(batches.element());

        // Structure query
        if (query.getId() == 0 || query.getId() == 1 ||query.getId() == 2 || query.getId() == 3)
        {
            try {
                if (database.executeStructureQuery(batches.remove()))
                {
                    return Statement.SUCCESS_NO_INFO;
                }
                else
                {
                    return Statement.EXECUTE_FAILED;
                }
            }
            catch (SQLTimeoutException e) {
                throw e;
            }
            catch (SQLException e) {
                return Statement.EXECUTE_FAILED;
            }
        }
        // Insert, delete and update
        else if (query.getId() == 4 || query.getId() == 5 || query.getId() == 6)
        {
            try {
                return database.executeUpdateQuery(batches.remove());
            }
            catch (SQLTimeoutException e) {
                throw e;
            }
            catch (SQLException e) {
                return Statement.EXECUTE_FAILED;
            }
        }
        // Select
        else
        {
            try {
                database.executeQuery(batches.remove());
                return Statement.SUCCESS_NO_INFO;
            }
            catch (SQLTimeoutException e) {
                throw e;
            }
            catch (SQLException e) {
                return Statement.EXECUTE_FAILED;
            }
        }
    }

    @Override
    public boolean execute(String sql) throws SQLException {
        Query query = QueryBuilder.buildQuery(sql);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Boolean> handler = executor.submit(() -> query.executeWithoutPrinting(database, sql));

        boolean flag = false;
        try{
            flag = handler.get(getQueryTimeout(), TimeUnit.SECONDS);
        }
        catch (Exception e) {
            handler.cancel(true);
            this.handleException(e);
        }

        executor.shutdownNow();

        // If an exception was thrown
        this.checkException();

        return flag;
    }

    @Override
    public ResultSet executeQuery(String sql) throws SQLException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<ResultSet> handler = executor.submit(() -> this.executeQueryInThread(sql));

        ResultSet resultSet = null;
        try{
            resultSet = handler.get(getQueryTimeout(), TimeUnit.SECONDS);
        }
        catch (Exception e) {
            handler.cancel(true);
            this.handleException(e);
        }

        executor.shutdownNow();

        // If an exception was thrown
        this.checkException();

        return resultSet;
    }

    private ResultSet executeQueryInThread(String sql) throws SQLException {
        ExtractData extractData = ExtractData.makeInstance();

        Object[][] selectedElements = database.executeQuery(sql);

        String tableName = extractData.getTableName(sql);
        String[]  columnNames = DataBaseGenerator.getSelectedColumnNames();
        Integer[] columnTypes = DataBaseGenerator.getSelectedColumnTypes();

        SelectInfo selectInfo = new SelectInfo(selectedElements, columnNames, columnTypes, tableName);

        return new eg.edu.alexu.csd.oop.db.cs30.jdbc.ResultSet(selectInfo, this);
    }

    @Override
    public int executeUpdate(String sql) throws SQLException {
        Integer result = 0;

        ExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        Future<Integer> handler = executorService.submit(() -> database.executeUpdateQuery(sql));

        try {
            result = handler.get(timeoutSeconds, TimeUnit.SECONDS);
        }
        catch (Exception e) {
            handler.cancel(true);
            this.handleException(e);
        }

        executorService.shutdownNow();

        // If an exception was thrown
        this.checkException();

        return result;
    }

    /**
     * Checks if an exception was thrown
     */
    private void checkException() throws SQLException {
        if (this.sqlException != null)
        {
            SQLException e = this.sqlException;

            // Reset sqlException
            this.sqlException = null;
            throw e;
        }
    }

    /**
     * Handle exceptions by future
     */
    private void handleException(Exception e) {
        if (e instanceof TimeoutException)
        {
            this.sqlException = new SQLTimeoutException("Execution exceeded time");
        }
        else if (e instanceof ExecutionException)
        {
            this.sqlException = new SQLException("An error occurred while executing query");
        }
        else if (e instanceof InterruptedException)
        {
            this.sqlException = new SQLTimeoutException("Thread was interrupted");
        }
        else
        {
            this.sqlException = new SQLException("An error occurred while executing query");
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        return this.connection;
    }

    @Override
    public int getQueryTimeout() throws SQLException {
        return this.timeoutSeconds;
    }

    @Override
    public void setQueryTimeout(int seconds) throws SQLException {

        if (seconds == 0)
            this.timeoutSeconds = Integer.MAX_VALUE;

        else
            this.timeoutSeconds = seconds;
    }

    @Override
    public void close() throws SQLException {
        this.database = null;
        this.batches = null;
        this.connection = null;
    }

    @Override
    public int getMaxFieldSize() throws SQLException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setMaxFieldSize(int max) throws SQLException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getMaxRows() throws SQLException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setMaxRows(int max) throws SQLException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setEscapeProcessing(boolean enable) throws SQLException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void cancel() throws SQLException {
        throw new UnsupportedOperationException();
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clearWarnings() throws SQLException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setCursorName(String name) throws SQLException {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResultSet getResultSet() throws SQLException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getUpdateCount() throws SQLException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean getMoreResults() throws SQLException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setFetchDirection(int direction) throws SQLException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getFetchDirection() throws SQLException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setFetchSize(int rows) throws SQLException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getFetchSize() throws SQLException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getResultSetConcurrency() throws SQLException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getResultSetType() throws SQLException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean getMoreResults(int current) throws SQLException {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResultSet getGeneratedKeys() throws SQLException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int executeUpdate(String sql, String[] columnNames) throws SQLException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean execute(String sql, int[] columnIndexes) throws SQLException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean execute(String sql, String[] columnNames) throws SQLException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getResultSetHoldability() throws SQLException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isClosed() throws SQLException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setPoolable(boolean poolable) throws SQLException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isPoolable() throws SQLException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void closeOnCompletion() throws SQLException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isCloseOnCompletion() throws SQLException {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        throw new UnsupportedOperationException();
    }
}
