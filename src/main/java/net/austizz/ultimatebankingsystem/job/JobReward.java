package net.austizz.ultimatebankingsystem.job;

/** A five-level job milestone and the banking-themed benefit it unlocks. */
public record JobReward(int level, String title, String description, String effect) {
    public JobReward {
        level = Math.max(5, Math.min(100, level));
        title = title == null ? "Career milestone" : title.trim();
        description = description == null ? "A new job benefit is available." : description.trim();
        effect = effect == null ? "Milestone unlocked" : effect.trim();
    }
}
