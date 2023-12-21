package cn.paper_card.player_last_quit;

import cn.paper_card.database.api.DatabaseApi;
import cn.paper_card.player_last_quit.api.PlayerLastQuitApi2;
import cn.paper_card.player_last_quit.api.QuitInfo;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.sql.SQLException;
import java.util.UUID;

public final class PlayerLastQuit extends JavaPlugin implements Listener {


    private PlayerLastQuitApiImpl playerLastQuitApi = null;


    @Override
    public void onLoad() {
        final DatabaseApi api = this.getServer().getServicesManager().load(DatabaseApi.class);
        if (api == null) throw new RuntimeException("无法连接到" + DatabaseApi.class.getSimpleName());

        final DatabaseApi.MySqlConnection connectionUnimportant = api.getRemoteMySQL().getConnectionUnimportant();
        this.playerLastQuitApi = new PlayerLastQuitApiImpl(connectionUnimportant);

        getSLF4JLogger().info("注册%s...".formatted(PlayerLastQuitApi2.class.getSimpleName()));
        this.getServer().getServicesManager().register(PlayerLastQuitApi2.class, this.playerLastQuitApi, this, ServicePriority.Highest);
    }

    @Override
    public void onEnable() {
        this.getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {

        if (this.playerLastQuitApi != null) {
            try {
                this.playerLastQuitApi.close();
            } catch (SQLException e) {
                this.getSLF4JLogger().error("close", e);
            }
            this.playerLastQuitApi = null;
        }

        this.getServer().getServicesManager().unregisterAll(this);
    }

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        final PlayerLastQuitApiImpl api = this.playerLastQuitApi;

        if (api == null) return;
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

            final QuitInfo info = new QuitInfo(id, name, ip, time);

            final boolean added;
            try {
                added = api.addOrUpdateByUuid(info);
            } catch (SQLException e) {
                getSLF4JLogger().error("", e);
                return;
            }
            getSLF4JLogger().info("%s了玩家%s的退出信息".formatted((added ? "添加" : "更新"), name));
        });
    }
}
