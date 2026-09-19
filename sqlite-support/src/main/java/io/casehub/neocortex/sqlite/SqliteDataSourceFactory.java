package io.casehub.neocortex.sqlite;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

public final class SqliteDataSourceFactory {

    private SqliteDataSourceFactory() {}

    public static HikariDataSource create(String path, int maxPoolSize, int busyTimeoutMs) {
        return create(path, maxPoolSize, busyTimeoutMs, -1);
    }

    public static HikariDataSource create(String path, int maxPoolSize,
                                           int busyTimeoutMs, int cacheSize) {
        boolean isMemory = ":memory:".equals(path) || path == null || path.isBlank();
        int effectivePoolSize = isMemory ? 1 : maxPoolSize;

        SQLiteConfig sqLiteConfig = new SQLiteConfig();
        if (!isMemory) {
            sqLiteConfig.setJournalMode(SQLiteConfig.JournalMode.WAL);
        }
        sqLiteConfig.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
        sqLiteConfig.setBusyTimeout(busyTimeoutMs);
        if (cacheSize > 0) {
            sqLiteConfig.setCacheSize(cacheSize);
        }

        SQLiteDataSource sqLiteDataSource = new SQLiteDataSource(sqLiteConfig);
        sqLiteDataSource.setUrl("jdbc:sqlite:" + (isMemory ? ":memory:" : path));

        HikariConfig hikari = new HikariConfig();
        hikari.setDataSource(sqLiteDataSource);
        hikari.setMaximumPoolSize(effectivePoolSize);
        hikari.setMinimumIdle(1);

        return new HikariDataSource(hikari);
    }

    public static void migrate(HikariDataSource ds, String flywayLocation) {
        Flyway.configure()
            .dataSource(ds)
            .locations(flywayLocation)
            .load()
            .migrate();
    }
}
