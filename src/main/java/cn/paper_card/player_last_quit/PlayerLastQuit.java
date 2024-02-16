package cn.paper_card.player_last_quit;

import cn.paper_card.database.api.DatabaseApi;
import cn.paper_card.player_last_quit.api.PlayerLastQuitApi2;
import cn.paper_card.player_last_quit.api.QuitInfo;
import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
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

    private TaskScheduler taskScheduler = null;


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
        this.taskScheduler = UniversalScheduler.getScheduler(this);

        new MainCommand(this);
    }

    @Override
    public void onDisable() {

        if (this.taskScheduler != null) {
            this.taskScheduler.cancelTasks(this);
            this.taskScheduler = null;
        }


        if (this.playerLastQuitApi != null) {
            // 保存所有玩家的退出信息
            for (final Player p : getServer().getOnlinePlayers()) {
                final InetSocketAddress address = p.getAddress();
                if (address == null) continue;
                final InetAddress address1 = address.getAddress();
                if (address1 == null) continue;
                final String hostAddress = address1.getHostAddress();
                if (hostAddress == null) continue;

                try {
                    this.playerLastQuitApi.addOrUpdateByUuid(new QuitInfo(p.getUniqueId(), p.getName(), hostAddress, System.currentTimeMillis()));
                } catch (SQLException e) {
                    getSLF4JLogger().error("", e);
                }
            }

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
        final TaskScheduler scheduler = this.taskScheduler;

        if (api == null) return;
        if (scheduler == null) return;


        scheduler.runTaskAsynchronously(() -> {

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

    @NotNull TaskScheduler getTaskScheduler() {
        return this.taskScheduler;
    }

    @NotNull PlayerLastQuitApiImpl getPlayerLastQuitApi() {
        return this.playerLastQuitApi;
    }

    void sendError(@NotNull CommandSender sender, @NotNull String error) {
        final TextComponent.Builder t = Component.text();
        this.appendPrefix(t);
        t.appendSpace();
        t.append(Component.text(error).color(NamedTextColor.RED));
        sender.sendMessage(t.build());
    }

    void sendException(@NotNull CommandSender sender, @NotNull Throwable e) {
        final TextComponent.Builder text = Component.text();

        this.appendPrefix(text);
        text.append(Component.text(" ==== 异常信息 ====").color(NamedTextColor.DARK_RED));

        for (Throwable t = e; t != null; t = t.getCause()) {
            text.appendNewline();
            text.append(Component.text(t.toString()).color(NamedTextColor.RED));
        }
        sender.sendMessage(text.build());
    }

    void appendPrefix(@NotNull TextComponent.Builder text) {
        text.append(Component.text("[").color(NamedTextColor.GRAY));
        text.append(Component.text(this.getName()).color(NamedTextColor.DARK_AQUA));
        text.append(Component.text("]").color(NamedTextColor.GRAY));
    }
}
