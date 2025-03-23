package it.quick.azgangs.database;

import it.quick.azgangs.AZGangs;
import it.quick.azgangs.models.Gang;

import java.sql.*;
import java.util.*;

public class DatabaseManager {

    private final AZGangs plugin;
    private Connection connection;
    private final String tablePrefix;
    private final String gangsTable;
    private final String membersTable;

    public DatabaseManager(AZGangs plugin) {
        this.plugin = plugin;
        this.tablePrefix = plugin.getConfigManager().getDatabasePrefix();
        this.gangsTable = tablePrefix + "gangs";
        this.membersTable = tablePrefix + "members";
    }

    public void initialize() {
        try {
            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException e) {
                plugin.getLogger().severe("SQLite JDBC driver non trovato: " + e.getMessage());
                throw new SQLException("SQLite driver non trovato", e);
            }

            connectToDatabase();
            createTables();
        } catch (SQLException e) {
            plugin.getLogger().severe("Errore nell'inizializzazione del database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void connectToDatabase() throws SQLException {
        String type = plugin.getConfigManager().getDatabaseType();

        if (type.equalsIgnoreCase("mysql")) {
            String host = plugin.getConfigManager().getDatabaseHost();
            int port = plugin.getConfigManager().getDatabasePort();
            String database = plugin.getConfigManager().getDatabaseName();
            String username = plugin.getConfigManager().getDatabaseUsername();
            String password = plugin.getConfigManager().getDatabasePassword();

            String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false";

            try {
                Class.forName("com.mysql.jdbc.Driver");
            } catch (ClassNotFoundException e) {
                plugin.getLogger().severe("Driver JDBC MySQL non trovato: " + e.getMessage());
                throw new SQLException("Driver MySQL non trovato", e);
            }

            connection = DriverManager.getConnection(url, username, password);
            plugin.getLogger().info("Connessione al database MySQL avvenuta con successo!");
        } else if (type.equalsIgnoreCase("sqlite")) {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            String url = "jdbc:sqlite:" + plugin.getDataFolder().getAbsolutePath() + "/azgangs.db";
            connection = DriverManager.getConnection(url);
            plugin.getLogger().info("Connessione al database SQLite avvenuta con successo!");
        } else {
            plugin.getLogger().severe("Tipo di database sconosciuto: " + type + ". Utilizzo SQLite come fallback.");
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            String url = "jdbc:sqlite:" + plugin.getDataFolder().getAbsolutePath() + "/azgangs.db";
            connection = DriverManager.getConnection(url);
        }
    }

    private void createTables() throws SQLException {
        if (connection == null) {
            plugin.getLogger().severe("Errore nella creazioni delle tabelle");
            return;
        }

        try (Statement statement = connection.createStatement()) {
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
        }
    }

    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("Connessione al database chiusa.");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Errore nella chiusura del database: " + e.getMessage());
        }
    }

    private boolean isMySql() {
        return plugin.getConfigManager().getDatabaseType().equalsIgnoreCase("mysql");
    }

    public boolean createGang(String name, UUID ownerUUID) {
        try (PreparedStatement statement = connection.prepareStatement(
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
        try (PreparedStatement statement = connection.prepareStatement(
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
        try (PreparedStatement statement = connection.prepareStatement(
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
        if (connection == null) {
            plugin.getLogger().severe("Errore nell'ottenimento della gang");
            return null;
        }

        try (PreparedStatement statement = connection.prepareStatement(
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
        if (connection == null) {
            plugin.getLogger().severe("Impossibile recuperare i dati della gang.");
            return null;
        }

        try (PreparedStatement statement = connection.prepareStatement(
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
        if (connection == null) {
            plugin.getLogger().severe("Errore nell'ottenimento della gang");
            return null;
        }

        try (PreparedStatement statement = connection.prepareStatement(
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

        if (connection == null) {
            plugin.getLogger().severe("Errore nell'ottenere la gang.");
            return gangs;
        }

        try (Statement statement = connection.createStatement();
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
        if (connection == null) {
            plugin.getLogger().severe("Errore: database is null");
            return false;
        }

        try (PreparedStatement statement = connection.prepareStatement(
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
        if (connection == null) {
            plugin.getLogger().severe("Impossibile rimuovere utenti dalla gang: database null");
            return false;
        }

        try (PreparedStatement statement = connection.prepareStatement(
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

        if (connection == null) {
            plugin.getLogger().severe("Errore nell'ottenere i players");
            return members;
        }

        try (PreparedStatement statement = connection.prepareStatement(
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
        if (connection == null) {
            plugin.getLogger().severe("Impossibile trovare il numero dei membri.");
            return 0;
        }

        try (PreparedStatement statement = connection.prepareStatement(
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
        if (connection == null) {
            plugin.getLogger().severe("Errore!");
            return false;
        }

        try (PreparedStatement statement = connection.prepareStatement(
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