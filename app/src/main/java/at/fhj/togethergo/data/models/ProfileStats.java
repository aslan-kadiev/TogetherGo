package at.fhj.togethergo.data.models;

/**
 * Aggregated profile stats collected from several Firestore sources (user document, daily
 * activity, mood entries). Used, among other things, to unlock badges.
 */
public class ProfileStats {
    public int totalQuests;
    public int activeDays;
    public long totalSteps;
    public int streak;
    public int bestStreak;
    public int moodCheckins;

    public ProfileStats() {
    }

    public ProfileStats(int totalQuests, int activeDays, long totalSteps, int streak) {
        this.totalQuests = totalQuests;
        this.activeDays = activeDays;
        this.totalSteps = totalSteps;
        this.streak = streak;
        this.bestStreak = streak;
    }

    public ProfileStats(int totalQuests, int activeDays, long totalSteps, int streak, int bestStreak) {
        this.totalQuests = totalQuests;
        this.activeDays = activeDays;
        this.totalSteps = totalSteps;
        this.streak = streak;
        this.bestStreak = bestStreak;
    }
}
