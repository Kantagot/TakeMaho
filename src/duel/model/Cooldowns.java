package duel.model;

import java.util.EnumMap;

public class Cooldowns {
    private final EnumMap<Skill, Long> readyAt = new EnumMap<>(Skill.class);

    public Cooldowns() {
        for (Skill s : Skill.values()) readyAt.put(s, 0L);
    }

    public boolean ready(long now, Skill s) {
        return now >= readyAt.getOrDefault(s, 0L);
    }

    public void set(long now, Skill s, long cdMillis) {
        readyAt.put(s, now + cdMillis);
    }

    public long remaining(long now, Skill s) {
        return Math.max(0L, readyAt.getOrDefault(s, 0L) - now);
    }
}
