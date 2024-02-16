package cn.paper_card.player_last_quit;

import org.jetbrains.annotations.NotNull;

class Util {
    static @NotNull String minutesAndSeconds(long secs) {
        final StringBuilder sb = new StringBuilder();

        final long minutes = secs / 60;
        secs %= 60;

        if (minutes != 0) {
            sb.append(minutes);
            sb.append('分');
        }

        if (secs != 0 || minutes == 0) {
            sb.append(secs);
            sb.append('秒');
        }
        return sb.toString();
    }
}
