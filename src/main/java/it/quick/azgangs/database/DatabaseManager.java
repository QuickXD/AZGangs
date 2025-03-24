package it.quick.azgangs.database;

import it.quick.azgangs.AZGangs;
import it.quick.azgangs.models.Gang;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DatabaseManager {

    private final AZGangs plugin;
    private HikariDataSource dataSource;
    private final String tablePrefix;
    private final String gangsTable;
    private final String membersTable;
    private final ExecutorService executorService;

    public DatabaseManager(AZGangs plugin) {
        this.plugin = plugin;
        this.tablePrefix = plugin.getConfigManager().getDatabasePrefix();
        this.gangsTable = tablePrefix + "gangs";
        this.membersTable = tablePrefix + "members";
        this.executorService = Executors.newFixedThreadPool(4);
    }

    public void initialize() {
        try {
            setupHikariDataSource();
            createTables();
        } catch (Exception e) {
            plugin.getLogger().severe("Errore nell'inizializzazione del database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void setupHikariDataSource() {
        HikariConfig config = new HikariConfig();
        String type = plugin.getConfigManager().getDatabaseType().toLowerCase();

        if (type.equals("mysql")) {
            setupMySqlConfig(config);
        } else {
            setupSqliteConfig(config);
        }

        config.setMaximumPoolSize(20);
        config.setMinimumIdle(10);
        config.setIdleTimeout(30000);
        config.setMaxLifetime(45000);
        config.setConnectionTimeout(10000);
        config.setPoolName("AZGangsConnectionPool");

        if (!type.equals("mysql")) {
            config.setConnectionTestQuery("SELECT 1");
        }

        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");

        dataSource = new HikariDataSource(config);
    }

    private void setupMySqlConfig(HikariConfig config) {
        String host = plugin.getConfigManager().getDatabaseHost();
        int port = plugin.getConfigManager().getDatabasePort();
        String database = plugin.getConfigManager().getDatabaseName();
        String username = plugin.getConfigManager().getDatabaseUsername();
        String password = plugin.getConfigManager().getDatabasePassword();

        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database +
                "?useSSL=false&serverTimezone=UTC&characterEncoding=UTF-8");
        config.setUsername(username);
        config.setPassword(password);
    }

    private void setupSqliteConfig(HikariConfig config) {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        String dbPath = plugin.getDataFolder().getAbsolutePath() + "/azgangs.db";
        config.setDriverClassName("org.sqlite.JDBC");
        config.setJdbcUrl("jdbc:sqlite:" + dbPath);
    }

    private void createTables() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + gangsTable + " (" +
                    "id INTEGER PRIMARY KEY " + (isMySql() ? "AUTO_INCREMENT" : "AUTOINCREMENT") + ", " +
                    "name VARCHAR(32) NOT NULL UNIQUE, " +
                    "owner_uuid VARCHAR(36) NOT NULL, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + membersTable + " (" +
                    "id INTEGER PRIMARY KEY " + (isMySql() ? "AUTO_INCREMENT" : "AUTOINCREMENT") + ", " +
                    "gang_id INTEGER NOT NULL, " +
                    "player_uuid VARCHAR(36) NOT NULL UNIQUE, " +
                    "joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (gang_id) REFERENCES " + gangsTable + "(id) ON DELETE CASCADE" +
                    ")");

            plugin.getLogger().info("Database tables created successfully!");
        } catch (SQLException e) {
            plugin.getLogger().severe("Errore nella creazione delle tabelle: " + e.getMessage());
        }
    }

    public void closeConnection() {
        if (dataSource != null) {
            dataSource.close();
            plugin.getLogger().info("Connessione al database chiusa.");
        }

        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(800, TimeUnit.MILLISECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }

    private boolean isMySql() {
        return plugin.getConfigManager().getDatabaseType().equalsIgnoreCase("mysql");
    }

    public boolean createGang(String name, UUID ownerUUID) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO " + gangsTable + " (name, owner_uuid) VALUES (?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {

            statement.setString(1, name);
            statement.setString(2, ownerUUID.toString());

            int affectedRows = statement.executeUpdate();

            if (affectedRows > 0) {
                try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int gangId = generatedKeys.getInt(1);
                        addMember(gangId, ownerUUID);
                        return true;
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Errore nella creazione di una gang: " + e.getMessage());
        }

        return false;
    }

    public boolean disbandGang(int gangId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM " + gangsTable + " WHERE id = ?")) {

            statement.setInt(1, gangId);

            int affectedRows = statement.executeUpdate();
            return affectedRows > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Errore nell'eliminazione di una gang: " + e.getMessage());
            return false;
        }
    }

    public boolean renameGang(int gangId, String newName) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE " + gangsTable + " SET name = ? WHERE id = ?")) {

            statement.setString(1, newName);
            statement.setInt(2, gangId);

            int affectedRows = statement.executeUpdate();
            return affectedRows > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Errore nella ridenominazione di una gang: " + e.getMessage());
            return false;
        }
    }

    public Gang getGangById(int gangId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM " + gangsTable + " WHERE id = ?")) {

            statement.setInt(1, gangId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return extractGangFromResultSet(resultSet);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Errore trovato: non è possibile trovare la gang tramite id " + e.getMessage());
        }

        return null;
    }

    public Gang getGangByName(String name) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM " + gangsTable + " WHERE LOWER(name) = LOWER(?)")) {

            statement.setString(1, name);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return extractGangFromResultSet(resultSet);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Errore nell'ottenimento della gang: " + e.getMessage());
        }

        return null;
    }

    public Gang getGangByPlayerUUID(UUID playerUUID) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT g.* FROM " + gangsTable + " g " +
                             "JOIN " + membersTable + " m ON g.id = m.gang_id " +
                             "WHERE m.player_uuid = ?")) {

            statement.setString(1, playerUUID.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return extractGangFromResultSet(resultSet);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Impossibile trovare la gang tramite UUID: " + e.getMessage());
        }

        return null;
    }

    public List<Gang> getAllGangs() {
        List<Gang> gangs = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT * FROM " + gangsTable)) {

            while (resultSet.next()) {
                gangs.add(extractGangFromResultSet(resultSet));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Errore nell'ottenere tutte le gang: " + e.getMessage());
        }

        return gangs;
    }

    private Gang extractGangFromResultSet(ResultSet resultSet) throws SQLException {
        int id = resultSet.getInt("id");
        String name = resultSet.getString("name");
        UUID ownerUUID = UUID.fromString(resultSet.getString("owner_uuid"));

        Gang gang = new Gang(id, name, ownerUUID);

        List<UUID> members = getGangMembers(id);
        gang.setMembers(members);

        return gang;
    }

    public boolean addMember(int gangId, UUID playerUUID) {
        removeMember(playerUUID);

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO " + membersTable + " (gang_id, player_uuid) VALUES (?, ?)")) {

            statement.setInt(1, gangId);
            statement.setString(2, playerUUID.toString());

            int affectedRows = statement.executeUpdate();
            return affectedRows > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Impossibile aggiungere membri alla gang: " + e.getMessage());
            return false;
        }
    }

    public boolean removeMember(UUID playerUUID) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM " + membersTable + " WHERE player_uuid = ?")) {

            statement.setString(1, playerUUID.toString());

            int affectedRows = statement.executeUpdate();
            return affectedRows > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Errore nella rimozione dei player dalla gang: " + e.getMessage());
            return false;
        }
    }

    public List<UUID> getGangMembers(int gangId) {
        List<UUID> members = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT player_uuid FROM " + membersTable + " WHERE gang_id = ?")) {

            statement.setInt(1, gangId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    members.add(UUID.fromString(resultSet.getString("player_uuid")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Errore nell'ottenere i players della gang: " + e.getMessage());
        }

        return members;
    }

    public int getGangMemberCount(int gangId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM " + membersTable + " WHERE gang_id = ?")) {

            statement.setInt(1, gangId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Errore nel trovare il numero dei membri della gang: " + e.getMessage());
        }

        return 0;
    }

    public boolean isPlayerInGang(UUID playerUUID) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM " + membersTable + " WHERE player_uuid = ?")) {

            statement.setString(1, playerUUID.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Errore nel check se il player è in una gang: " + e.getMessage());
        }

        return false;
    }
}
