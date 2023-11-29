package cn.paper_card.player_last_quit;

import cn.paper_card.database.api.DatabaseApi;
import cn.paper_card.database.api.Util;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

public final class PlayerLastQuit extends JavaPlugin implements PlayerLastQuitApi, Listener {

    private Connection connection = null;
    private Table table = null;

    private final @NotNull Object lock = new Object();


    private @NotNull Table getTable() throws Exception {
        if (this.table == null) {
            this.table = new Table(this.connection);
        }
        return this.table;
    }

    @Override
    public void onLoad() {
        final DatabaseApi api = this.getServer().getServicesManager().load(DatabaseApi.class);
        if (api == null) throw new RuntimeException("无法连接到" + DatabaseApi.class.getSimpleName());

        try {
            this.connection = api.getLocalSQLite().connectUnimportant();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onEnable() {
        this.getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        synchronized (lock) {
            if (this.table != null) {
                try {
                    this.table.close();
                } catch (SQLException e) {
                    getLogger().severe(e.toString());
                    e.printStackTrace();
                }
                this.table = null;
            }

            if (this.connection != null) {
                try {
                    this.connection.close();
                } catch (SQLException e) {
                    getLogger().severe(e.toString());
                    e.printStackTrace();
                }
                this.connection = null;
            }
        }
    }

    @Override
    public @NotNull List<Info> queryByIp(@NotNull String ip) throws Exception {
        synchronized (lock) {
            final Table t = this.getTable();
            return t.queryByIp(ip);
        }
    }

    @Override
    public @NotNull List<Info> queryByName(@NotNull String name) throws Exception {
        synchronized (lock) {
            final Table t = this.getTable();
            return t.queryByName(name);
        }
    }

    @Override
    public @Nullable Info queryByUuid(@NotNull UUID uuid) throws Exception {
        synchronized (lock) {
            final Table t = this.getTable();
            final List<Info> list = t.queryByUuid(uuid);
            final int size = list.size();
            if (size == 1) return list.get(0);
            if (size == 0) return null;
            throw new Exception("根据一个UUID查询到了%d条数据！".formatted(size));
        }
    }

    private boolean addOrUpdateByUuid(@NotNull Info info) throws Exception {
        synchronized (lock) {
            final Table t = this.getTable();
            final int updated = t.updateByUuid(info);
            if (updated == 0) {
                final int insert = t.insert(info);
                if (insert != 1) throw new Exception("插入了%d条数据！".formatted(insert));
                return true;
            }

            if (updated == 1) return false;

            throw new Exception("根据一个UUID更新了%d条数据！".formatted(updated));
        }
    }

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        this.getServer().getAsyncScheduler().runNow(this, scheduledTask -> {

            final Player p = event.getPlayer();


            final InetSocketAddress address = p.getAddress();
            if (address == null) return;
            final InetAddress address1 = address.getAddress();
            if (address1 == null) return;
            final String ip = address1.getHostAddress();


            final String name = p.getName();
            final UUID id = p.getUniqueId();
            final long time = System.currentTimeMillis();

            final Info info = new Info(name, id, ip, time);

            final boolean added;
            try {
                added = this.addOrUpdateByUuid(info);
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }

            getLogger().info("%s了玩家[%s]的退出信息".formatted((added ? "添加" : "更新"), name));
        });
    }

    private static class Table {
        private final static String NAME = "player_last_quit";

        private final PreparedStatement statementInsert;
        private final PreparedStatement statementUpdateByUuid;

        private final PreparedStatement statementQueryByIp;

        private final PreparedStatement statementQueryByName;

        private final PreparedStatement statementQueryByUuid;


        Table(@NotNull Connection connection) throws SQLException {
            this.create(connection);
            try {
                this.statementInsert = connection.prepareStatement("""
                        INSERT INTO %s (name, uid1, uid2, ip, time)
                        VALUES (?, ?, ?, ?, ?)
                        """.formatted(NAME)
                );

                this.statementUpdateByUuid = connection.prepareStatement("""
                        UPDATE %s SET name=?, ip=?, time=? WHERE uid1=? AND uid2=?
                        """.formatted(NAME)
                );

                this.statementQueryByIp = connection.prepareStatement("""
                        SELECT name, uid1, uid2, ip, time FROM %s WHERE ip=?
                        """.formatted(NAME)
                );

                this.statementQueryByName = connection.prepareStatement("""
                        SELECT name, uid1, uid2, ip, time FROM %s WHERE name=?
                        """.formatted(NAME));

                this.statementQueryByUuid = connection.prepareStatement("""
                        SELECT name, uid1, uid2, ip, time FROM %s WHERE uid1=? AND uid2=?
                        """.formatted(NAME));

            } catch (SQLException e) {
                try {
                    this.close();
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }

        private void create(@NotNull Connection connection) throws SQLException {
            final String sql = """
                    CREATE TABLE IF NOT EXISTS %s (
                        name    VARCHAR(64) NOT NULL,
                        uid1    INTEGER NOT NULL,
                        uid2    INTEGER NOT NULL,
                        ip      CHAR(24) NOT NULL,
                        time    INTEGER NOT NULL
                    )
                    """.formatted(NAME);

            Util.executeSQL(connection, sql);
        }

        void close() throws SQLException {
            Util.closeAllStatements(this.getClass(), this);
        }

        int insert(@NotNull Info info) throws SQLException {
            final PreparedStatement ps = this.statementInsert;
            ps.setString(1, info.name());
            ps.setLong(2, info.uuid().getMostSignificantBits());
            ps.setLong(3, info.uuid().getLeastSignificantBits());
            ps.setString(4, info.ip());
            ps.setLong(5, info.time());
            return ps.executeUpdate();
        }

        int updateByUuid(@NotNull Info info) throws SQLException {
            final PreparedStatement ps = this.statementUpdateByUuid;
            ps.setString(1, info.name());
            ps.setString(2, info.ip());
            ps.setLong(3, info.time());
            ps.setLong(4, info.uuid().getMostSignificantBits());
            ps.setLong(5, info.uuid().getLeastSignificantBits());
            return ps.executeUpdate();
        }

        private @NotNull List<Info> parseAll(@NotNull ResultSet resultSet) throws SQLException {
            final LinkedList<Info> list = new LinkedList<>();

            try {
                while (resultSet.next()) {
                    final String name = resultSet.getString(1);
                    final long uid1 = resultSet.getLong(2);
                    final long uid2 = resultSet.getLong(3);
                    final String ip = resultSet.getString(4);
                    final long time = resultSet.getLong(5);
                    final Info info = new Info(name, new UUID(uid1, uid2), ip, time);
                    list.add(info);

                }
            } catch (SQLException e) {
                try {
                    resultSet.close();
                } catch (SQLException ignored) {
                }
                throw e;
            }

            resultSet.close();

            return list;
        }

        @NotNull List<Info> queryByIp(@NotNull String ip) throws SQLException {
            final PreparedStatement ps = this.statementQueryByIp;
            ps.setString(1, ip);
            final ResultSet resultSet = ps.executeQuery();
            return this.parseAll(resultSet);
        }

        @NotNull List<Info> queryByName(@NotNull String name) throws SQLException {
            final PreparedStatement ps = this.statementQueryByName;
            ps.setString(1, name);
            final ResultSet resultSet = ps.executeQuery();
            return this.parseAll(resultSet);
        }

        @NotNull List<Info> queryByUuid(@NotNull UUID uuid) throws SQLException {
            final PreparedStatement ps = this.statementQueryByUuid;
            ps.setLong(1, uuid.getMostSignificantBits());
            ps.setLong(2, uuid.getLeastSignificantBits());
            final ResultSet resultSet = ps.executeQuery();
            return this.parseAll(resultSet);
        }
    }
}
