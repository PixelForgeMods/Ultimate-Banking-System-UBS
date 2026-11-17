package net.austizz.ultimatebankingsystem.job;

/** Stable quest metadata contract used by every job's daily rotation. */
public interface JobQuestDefinition {
    String id();

    String title();

    String description();

    long target();
}
