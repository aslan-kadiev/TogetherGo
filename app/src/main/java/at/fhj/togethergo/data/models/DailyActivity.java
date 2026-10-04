package at.fhj.togethergo.data.models;

/**
 * Activity data for a single day, stored in Firestore and shown on the activity screen.
 * Also records whether each daily goal (steps, distance, calories) was already rewarded.
 */
public class DailyActivity {
    public int steps;
    public int stepGoal;
    public double km;
    public int activeMinutes;
    public int calories;
    public boolean goalRewarded;
    public boolean kmRewarded;
    public boolean kcalRewarded;

    public DailyActivity() {
        // Required for Firestore
    }

    public DailyActivity(int steps, int stepGoal, double km, int activeMinutes, int calories) {
        this.steps = steps;
        this.stepGoal = stepGoal;
        this.km = km;
        this.activeMinutes = activeMinutes;
        this.calories = calories;
    }
}
