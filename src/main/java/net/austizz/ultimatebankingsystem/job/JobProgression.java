package net.austizz.ultimatebankingsystem.job;

import net.austizz.ultimatebankingsystem.Config;

import java.util.ArrayList;
import java.util.List;

/** Shared job progression calculations used by quotes, caps, packets, and the dashboard. */
public final class JobProgression {
    private static final long PAYOUT_CAP_REWARD_CENTS = 25_000L;
    private static final long UNITS_CAP_REWARD_MILLI = 100_000L;

    private JobProgression() {
    }

    public static List<JobReward> lumberjackRewards() {
        return rewardsFor(JobRegistry.get(JobService.LUMBERJACK_ID));
    }

    /** Returns the shared five-level milestone track with language tailored to the selected job. */
    public static List<JobReward> rewardsFor(JobDefinition definition) {
        List<JobReward> rewards = new ArrayList<>(20);
        for (int level = 5; level <= 100; level += 5) {
            rewards.add(rewardFor(definition, level));
        }
        return List.copyOf(rewards);
    }

    public static JobReward lumberjackReward(int level) {
        return rewardFor(JobRegistry.get(JobService.LUMBERJACK_ID), level);
    }

    public static JobReward rewardFor(JobDefinition definition, int level) {
        int milestone = Math.max(1, Math.min(20, level / 5));
        int milestoneLevel = milestone * 5;
        String jobName = definition == null || definition.displayName().isBlank()
                ? "Job" : definition.displayName();
        return switch ((milestone - 1) % 4) {
            case 0 -> {
                int payout = payoutBonusPercent(milestoneLevel);
                yield new JobReward(milestoneLevel, jobName + " Preferred Rate " + (((milestone - 1) / 4) + 1),
                        "Your verified " + jobName + " settlements receive a stronger preferred rate.",
                        "+" + payout + "% payout rate");
            }
            case 1 -> {
                int xp = xpBonusPercent(milestoneLevel);
                yield new JobReward(milestoneLevel, jobName + " Certification " + (((milestone - 2) / 4) + 1),
                        "Your " + jobName + " certification improves the career XP credited by each completed sale.",
                        "+" + xp + "% sale XP");
            }
            case 2 -> {
                long bonus = dailyPayoutCapBonusCents(milestoneLevel);
                yield new JobReward(milestoneLevel, jobName + " Reserve Window " + (((milestone - 3) / 4) + 1),
                        "The bank authorizes a larger daily payout reserve for verified " + jobName + " turn-ins.",
                        "+$" + formatDollars(bonus) + " daily payout room");
            }
            default -> {
                long bonus = dailyUnitsCapBonusMilli(milestoneLevel);
                yield new JobReward(milestoneLevel, jobName + " Intake Clearance " + (((milestone - 4) / 4) + 1),
                        "Your supplier clearance expands the amount of normalized goods the desk can accept each day.",
                        "+" + formatUnits(bonus) + " daily units");
            }
        };
    }

    public static int payoutBonusPercent(int level) {
        int cappedLevel = Math.max(1, Math.min(100, level));
        return Math.round(Config.LUMBERJACK_MAX_LEVEL_PAYOUT_BONUS_PERCENT.get()
                * (cappedLevel - 1) / 99.0F);
    }

    public static int xpBonusPercent(int level) {
        int milestone = Math.max(0, Math.min(20, level / 5));
        int trainingMilestones = milestone < 2 ? 0 : ((milestone - 2) / 4) + 1;
        return trainingMilestones * 2;
    }

    public static long dailyPayoutCapCents(int level) {
        return saturatingAdd(Math.max(1L, Config.LUMBERJACK_PLAYER_DAILY_PAYOUT_CENTS.get()),
                dailyPayoutCapBonusCents(level));
    }

    public static long dailyUnitsCapMilli(int level) {
        long configured = Math.max(1L, Config.LUMBERJACK_PLAYER_DAILY_UNITS.get()) * 1000L;
        return saturatingAdd(configured, dailyUnitsCapBonusMilli(level));
    }

    public static long dailyPayoutCapBonusCents(int level) {
        int milestone = Math.max(0, Math.min(20, level / 5));
        int reserveMilestones = milestone < 3 ? 0 : ((milestone - 3) / 4) + 1;
        return reserveMilestones * PAYOUT_CAP_REWARD_CENTS;
    }

    public static long dailyUnitsCapBonusMilli(int level) {
        int milestone = Math.max(0, Math.min(20, level / 5));
        int clearanceMilestones = milestone < 4 ? 0 : ((milestone - 4) / 4) + 1;
        return clearanceMilestones * UNITS_CAP_REWARD_MILLI;
    }

    private static long saturatingAdd(long left, long right) {
        return right > 0L && left > Long.MAX_VALUE - right ? Long.MAX_VALUE : Math.max(0L, left + right);
    }

    private static String formatDollars(long cents) {
        return String.format(java.util.Locale.ROOT, "%,d.%02d", cents / 100L, cents % 100L);
    }

    private static String formatUnits(long unitsMilli) {
        return String.format(java.util.Locale.ROOT, "%.0f", unitsMilli / 1000.0D);
    }
}
