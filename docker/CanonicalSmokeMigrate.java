import org.flywaydb.core.Flyway;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;

/**
 * Test-only canonical V1 migrator for the disposable canonical smoke stack.
 * Refuses any target other than the loopback smoke port and the expected owner database,
 * applies only {@code <owner>-service/src/main/resources/db/canonical} with the same Flyway settings
 * as the canonical acceptance tests, proves an immediate rerun is a no-op, then grants the
 * runtime role the same DML-only privileges (no Flyway history, read-only feature markers).
 * Passwords arrive through the environment, never arguments.
 * Run: java -cp "tools/supabase-runner/target/dependency-10.22.0/*" docker/CanonicalSmokeMigrate.java
 */
public class CanonicalSmokeMigrate {
    static final String[] OWNERS = {"user", "hotel", "booking", "revenue", "regulations"};

    public static void main(String[] args) throws Exception {
        for (String owner : OWNERS) {
            String db = "checkinn_" + owner;
            String url = "jdbc:postgresql://127.0.0.1:58432/" + db + "?currentSchema=public";
            String migrator = "smoke_" + owner + "_migrator", app = "smoke_" + owner + "_app";
            String password = System.getenv("SMOKE_" + owner.toUpperCase() + "_MIGRATOR_PASSWORD");
            if (password == null || password.length() < 24) throw new IllegalStateException("Missing generated migrator password for " + owner);
            Path location = Path.of(owner + "-service/src/main/resources/db/canonical").toAbsolutePath();
            if (!Files.isRegularFile(location.resolve("V1__canonical_schema.sql"))) throw new IllegalStateException("Canonical V1 missing: " + location);
            try (var c = DriverManager.getConnection(url, migrator, password); var s = c.createStatement()) {
                var r = s.executeQuery("SELECT current_database(), (SELECT count(*) FROM information_schema.tables WHERE table_schema='public'), "
                        + "(SELECT setting FROM pg_settings WHERE name='cluster_name')");
                r.next();
                if (!db.equals(r.getString(1))) throw new IllegalStateException("Unexpected database " + r.getString(1));
                if (r.getLong(2) != 0) throw new IllegalStateException(db + " is not empty; refuse to migrate a non-fresh database");
            }
            var flyway = Flyway.configure().dataSource(url, migrator, password).locations("filesystem:" + location)
                    .defaultSchema("public").schemas("public").baselineOnMigrate(false).cleanDisabled(true)
                    .outOfOrder(false).validateOnMigrate(true).load();
            int first = flyway.migrate().migrationsExecuted, second = flyway.migrate().migrationsExecuted;
            if (first != 1 || second != 0) throw new IllegalStateException(owner + " migrate counts " + first + "/" + second);
            flyway.validate();
            try (var c = DriverManager.getConnection(url, migrator, password); var s = c.createStatement()) {
                s.execute("GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA public TO " + app);
                s.execute("REVOKE ALL ON flyway_schema_history FROM " + app);
                s.execute("DO $$ BEGIN IF to_regclass('public.checkinn_feature_migrations') IS NOT NULL THEN "
                        + "REVOKE INSERT,UPDATE,DELETE ON checkinn_feature_migrations FROM " + app + "; END IF; END $$");
                s.execute("GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA public TO " + app);
                var r = s.executeQuery("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name<>'flyway_schema_history'");
                r.next();
                long tables = r.getLong(1);
                r = s.executeQuery("SELECT count(*) FROM flyway_schema_history WHERE version='1' AND success AND type='SQL'");
                r.next();
                System.out.println("MIGRATED " + owner + " tables=" + tables + " v1History=" + r.getLong(1) + " rerunExecuted=" + second);
            }
        }
    }
}
