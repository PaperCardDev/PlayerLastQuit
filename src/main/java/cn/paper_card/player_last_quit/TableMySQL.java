package cn.paper_card.player_last_quit;

import cn.paper_card.database.api.Parser;
import cn.paper_card.database.api.Util;
import cn.paper_card.player_last_quit.api.QuitInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

class TableMySQL extends Parser<QuitInfo> {

    private final static String NAME = "player_last_quit";

    private PreparedStatement statementInsert = null;
    private PreparedStatement statementUpdateByUuid = null;

    private PreparedStatement statementQueryByIpLatest = null;

    private PreparedStatement statementQueryTimeAfter = null;

    private PreparedStatement statementQueryLatest = null;

    private final @NotNull Connection connection;


    TableMySQL(@NotNull Connection connection) throws SQLException {
        this.connection = connection;
        this.create();
    }

    private void create() throws SQLException {
        final String sql = """
                CREATE TABLE IF NOT EXISTS %s (
                    name    VARCHAR(64) NOT NULL,
                    uid1    BIGINT NOT NULL,
                    uid2    BIGINT NOT NULL,
                    ip      CHAR(24) NOT NULL,
                    time    BIGINT NOT NULL,
                    PRIMARY KEY(uid1, uid2)
                )""".formatted(NAME);

        Util.executeSQL(this.connection, sql);
    }

    void close() throws SQLException {
        Util.closeAllStatements(this.getClass(), this);
    }

    private @NotNull PreparedStatement getStatementInsert() throws SQLException {
        if (this.statementInsert == null) {
            this.statementInsert = this.connection.prepareStatement("""
                    INSERT INTO %s (name, uid1, uid2, ip, time)
                    VALUES (?, ?, ?, ?, ?)""".formatted(NAME)
            );

        }
        return this.statementInsert;
    }

    private @NotNull PreparedStatement getStatementUpdateByUuid() throws SQLException {
        if (this.statementUpdateByUuid == null) {
            this.statementUpdateByUuid = this.connection.prepareStatement("""
                    UPDATE %s SET name=?, ip=?, time=? WHERE uid1=? AND uid2=? LIMIT 1
                    """.formatted(NAME)
            );
        }
        return this.statementUpdateByUuid;
    }

    private @NotNull PreparedStatement getStatementQueryByIpLatest() throws SQLException {
        if (this.statementQueryByIpLatest == null) {
            this.statementQueryByIpLatest = this.connection.prepareStatement("""
                    SELECT name, uid1, uid2, ip, time FROM %s
                    WHERE ip = ? ORDER BY time DESC LIMIT 1
                    """.formatted(NAME));
        }
        return this.statementQueryByIpLatest;
    }

    private @NotNull PreparedStatement getStatementQueryTimeAfter() throws SQLException {
        if (this.statementQueryTimeAfter == null) {
            this.statementQueryTimeAfter = this.connection.prepareStatement("""
                    SELECT name, uid1, uid2, ip, time
                    FROM %s
                    WHERE time > ?
                    ORDER BY time;""".formatted(NAME));
        }
        return this.statementQueryTimeAfter;
    }

    private @NotNull PreparedStatement getStatementQueryLatest() throws SQLException {
        if (this.statementQueryLatest == null) {
            this.statementQueryLatest = this.connection.prepareStatement("""
                    SELECT name, uid1, uid2, ip, time
                    FROM %s
                    WHERE time > (SELECT max(time)
                                  FROM %s) - ?
                    ORDER BY time DESC;""".formatted(NAME, NAME));
        }
        return this.statementQueryLatest;
    }

    int insert(@NotNull QuitInfo info) throws SQLException {
        final PreparedStatement ps = this.getStatementInsert();
        ps.setString(1, info.name());
        ps.setLong(2, info.uuid().getMostSignificantBits());
        ps.setLong(3, info.uuid().getLeastSignificantBits());
        ps.setString(4, info.ip());
        ps.setLong(5, info.time());
        return ps.executeUpdate();
    }

    int updateByUuid(@NotNull QuitInfo info) throws SQLException {
        final PreparedStatement ps = this.getStatementUpdateByUuid();
        ps.setString(1, info.name());
        ps.setString(2, info.ip());
        ps.setLong(3, info.time());
        ps.setLong(4, info.uuid().getMostSignificantBits());
        ps.setLong(5, info.uuid().getLeastSignificantBits());
        return ps.executeUpdate();
    }

    @Override
    public @NotNull QuitInfo parseRow(@NotNull ResultSet resultSet) throws SQLException {
        final String name = resultSet.getString(1);
        final long uid1 = resultSet.getLong(2);
        final long uid2 = resultSet.getLong(3);
        final String ip = resultSet.getString(4);
        final long time = resultSet.getLong(5);
        return new QuitInfo(new UUID(uid1, uid2), name, ip, time);
    }

    @NotNull List<QuitInfo> queryLatest(long time) throws SQLException {
        final PreparedStatement ps = this.getStatementQueryLatest();
        ps.setLong(1, time);
        final ResultSet resultSet = ps.executeQuery();
        return this.parseAll(resultSet);
    }


    @Nullable QuitInfo queryByIpLatest(@NotNull String ip) throws SQLException {
        final PreparedStatement ps = this.getStatementQueryByIpLatest();
        ps.setString(1, ip);
        final ResultSet resultSet = ps.executeQuery();
        return this.parseOne(resultSet);
    }

    @NotNull List<QuitInfo> queryTimeAfter(long time) throws SQLException {
        final PreparedStatement ps = this.getStatementQueryTimeAfter();

        ps.setLong(1, time);
        final ResultSet resultSet = ps.executeQuery();

        return this.parseAll(resultSet);
    }
}