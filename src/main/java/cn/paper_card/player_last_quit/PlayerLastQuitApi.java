package cn.paper_card.player_last_quit;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

public interface PlayerLastQuitApi {
    record Info(String name, UUID uuid, String ip, long time) {
    }

    @SuppressWarnings("unused")
    @NotNull List<Info> queryByIp(@NotNull String ip) throws Exception;
}
