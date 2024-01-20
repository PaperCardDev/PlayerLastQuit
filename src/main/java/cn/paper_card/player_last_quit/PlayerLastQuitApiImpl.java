package cn.paper_card.player_last_quit;

import cn.paper_card.database.api.DatabaseApi;
import cn.paper_card.player_last_quit.api.PlayerLastQuitApi2;
import cn.paper_card.player_last_quit.api.QuitInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

class PlayerLastQuitApiImpl implements PlayerLastQuitApi2 {

    private final @NotNull DatabaseApi.MySqlConnection mySqlConnection;
    private Connection connection = null;

    private TableMySQL table = null;

    PlayerLastQuitApiImpl(@NotNull DatabaseApi.MySqlConnection mySqlConnection) {
        this.mySqlConnection = mySqlConnection;
    }

    private @NotNull TableMySQL getTable() throws SQLException {
        final Connection newCon = this.mySqlConnection.getRawConnection();

        if (this.connection != null && this.connection == newCon) return this.table;

        if (this.table != null) this.table.close();
        this.table = new TableMySQL(newCon);
        this.connection = newCon;

        return this.table;
    }

    void close() throws SQLException {
        synchronized (this.mySqlConnection) {
            final TableMySQL t = this.table;
            this.connection = null;
            this.table = null;
            if (t != null) t.close();
        }
    }

    @Override
    public boolean addOrUpdateByUuid(@NotNull QuitInfo quitInfo) throws SQLException {
        synchronized (this.mySqlConnection) {
            try {
                final TableMySQL t = this.getTable();
                final int updated = t.updateByUuid(quitInfo);
                this.mySqlConnection.setLastUseTime();

                if (updated == 0) {
                    final int inserted = t.insert(quitInfo);
                    this.mySqlConnection.setLastUseTime();
                    if (inserted != 1) throw new RuntimeException("插入了%d条数据！".formatted(inserted));
                    return true;
                }

                if (updated != 1) throw new RuntimeException("更新了%d条数据！".formatted(updated));

                return false;
            } catch (SQLException e) {
                try {
                    this.mySqlConnection.handleException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }
    }

    @Override
    public @Nullable QuitInfo queryByIpLatest(@NotNull String ip) throws SQLException {
        synchronized (this.mySqlConnection) {
            try {
                final TableMySQL t = this.getTable();
                final QuitInfo quitInfo = t.queryByIpLatest(ip);
                this.mySqlConnection.setLastUseTime();
                return quitInfo;
            } catch (SQLException e) {
                try {
                    this.mySqlConnection.handleException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }
    }

    @Override
    public @NotNull List<QuitInfo> queryTimeAfter(long time) throws SQLException {
        synchronized (this.mySqlConnection) {
            try {
                final TableMySQL t = this.getTable();

                final List<QuitInfo> list = t.queryTimeAfter(time);
                this.mySqlConnection.setLastUseTime();

                return list;
            } catch (SQLException e) {
                try {
                    this.mySqlConnection.handleException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }
    }
}
