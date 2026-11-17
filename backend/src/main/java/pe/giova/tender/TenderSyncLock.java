package pe.giova.tender;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import javax.sql.DataSource;

public final class TenderSyncLock implements AutoCloseable {
    private final Connection connection;
    private final String key;
    private boolean closed;

    private TenderSyncLock(Connection connection, String key) {
        this.connection = connection;
        this.key = key;
    }

    public static Optional<TenderSyncLock> tryAcquire(DataSource dataSource, String key) {
        try {
            Connection connection = dataSource.getConnection();
            try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?, 0)")) {
                statement.setString(1, key);
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next() && result.getInt(1) == 1) {
                        return Optional.of(new TenderSyncLock(connection, key));
                    }
                }
            }
            connection.close();
            return Optional.empty();
        } catch (SQLException exception) {
            throw new IllegalStateException("No se pudo adquirir el lock de sincronización", exception);
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try (PreparedStatement statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            statement.setString(1, key);
            statement.executeQuery().close();
        } catch (SQLException ignored) {
            // Closing the connection also releases a MariaDB advisory lock.
        } finally {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // The connection pool owns the cleanup after the task ends.
            }
        }
    }
}
