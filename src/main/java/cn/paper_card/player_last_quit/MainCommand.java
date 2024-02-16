package cn.paper_card.player_last_quit;

import cn.paper_card.mc_command.TheMcCommand;
import cn.paper_card.player_last_quit.api.QuitInfo;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.permissions.Permission;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

class MainCommand extends TheMcCommand.HasSub {

    private final @NotNull Permission permission;

    private final @NotNull PlayerLastQuit plugin;

    public MainCommand(@NotNull PlayerLastQuit plugin) {
        super("player-last-quit");
        this.plugin = plugin;
        this.permission = Objects.requireNonNull(plugin.getServer().getPluginManager().getPermission("player-last-quit.command"));

        this.addSubCommand(new Latest());

        final PluginCommand c = plugin.getCommand(this.getLabel());
        assert c != null;
        c.setExecutor(this);
        c.setTabCompleter(this);
    }

    @Override
    protected boolean canNotExecute(@NotNull CommandSender commandSender) {
        return !commandSender.hasPermission(this.permission);
    }

    @NotNull Permission addPermission(@NotNull String name) {
        final Permission p = new Permission(name);
        plugin.getServer().getPluginManager().addPermission(p);
        return p;
    }


    class Latest extends TheMcCommand {

        private final @NotNull Permission permission;

        protected Latest() {
            super("latest");
            this.permission = addPermission(MainCommand.this.permission.getName() + "." + this.getLabel());
        }

        @Override
        protected boolean canNotExecute(@NotNull CommandSender commandSender) {
            return !commandSender.hasPermission(this.permission);
        }

        @Override
        public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            final String argTime = strings.length > 0 ? strings[0] : null;

            final long time;
            if (argTime == null) {
                time = 10 * 60 * 1000L;
            } else {
                try {
                    time = Long.parseLong(argTime);
                } catch (NumberFormatException e) {
                    plugin.sendError(commandSender, "%s 不是正确的时间".formatted(argTime));
                    return true;
                }
            }

            final TaskScheduler scheduler = plugin.getTaskScheduler();

            scheduler.runTaskAsynchronously(() -> {
                final PlayerLastQuitApiImpl api = plugin.getPlayerLastQuitApi();

                final List<QuitInfo> list;
                try {
                    list = api.queryLatest(time);
                } catch (SQLException e) {
                    plugin.getSLF4JLogger().error("", e);
                    plugin.sendException(commandSender, e);
                    return;
                }

                final TextComponent.Builder text = Component.text();
                plugin.appendPrefix(text);
                text.appendSpace();

                text.append(Component.text("==== 最近%s退出的玩家 ====".formatted(
                        Util.minutesAndSeconds(time / 1000L)
                )));

                final long cur = System.currentTimeMillis();

                for (QuitInfo i : list) {
                    text.appendNewline();

                    text.append(Component.text(i.name()));

                    text.append(Component.text(" | "));
                    final long delta = (cur - i.time()) / 1000L;
                    text.append(Component.text("%s前".formatted(Util.minutesAndSeconds(delta))));

                    text.append(Component.text(" | "));
                    text.append(Component.text(i.ip()));
                }
                
                commandSender.sendMessage(text.build().color(NamedTextColor.GREEN));
            });

            return true;
        }

        @Override
        public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            return null;
        }
    }
}
