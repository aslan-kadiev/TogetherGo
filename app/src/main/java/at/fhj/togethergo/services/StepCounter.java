package at.fhj.togethergo.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import at.fhj.togethergo.R;

/**
 * Foreground service that counts today's steps.
 * <p>
 * Reads the step counter sensor, derives distance, active minutes and calories, and stores the
 * daily totals in Firestore. Reaching a daily goal (steps, distance, calories) completes the
 * matching quest and awards points. Because it runs in the foreground, counting continues while the
 * app is in the background.
 */
public class StepCounter extends Service implements SensorEventListener {

    private static final String CHANNEL_ID = "step_counter_channel";
    private static final int NOTIFICATION_ID = 1;

    // rough averages for turning steps into km / calories / minutes
    private static final double STEP_LENGTH_METERS = 0.762;
    private static final double CALORIES_PER_STEP = 0.04;
    private static final double STEPS_PER_MINUTE = 100.0;
    private static final int WALK_QUEST_STEP_GOAL = 500;
    private static final int DEFAULT_STEP_GOAL = 5000;

    private final IBinder binder = new LocalBinder();

    /** Binder that gives bound components direct access to the running service. */
    public class LocalBinder extends Binder {
        public StepCounter getService() {
            return StepCounter.this;
        }
    }

    private SensorManager sensorManager;
    private Sensor stepCounterSensor;

    private float startSteps = -1;
    private int currentSessionSteps = 0;

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private int dailyBaseSteps = 0; // steps saved for today before this session started
    private boolean baseLoaded = false;
    private boolean goalRewarded = false;
    private boolean kmRewarded = false;
    private boolean kcalRewarded = false;
    private boolean goalRewardPending = false;
    private boolean kmRewardPending = false;
    private boolean kcalRewardPending = false;
    private int lastPersistedSteps = -1;
    private String lastTrackedDate;

    @Override
    public void onCreate() {
        super.onCreate();

        createNotificationChannel();

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        if (sensorManager != null) {
            stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        }

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        lastTrackedDate = today();
        loadDailyBaseSteps(lastTrackedDate);
    }

    /**
     * Loads the steps already stored for the given day so counting continues instead of
     * restarting. Also restores the reward flags.
     * @param date day in yyyy-MM-dd format
     */
    private void loadDailyBaseSteps(String date) {
        String userId = auth != null ? auth.getUid() : null;
        if (userId == null) {
            baseLoaded = true;
            return;
        }

        baseLoaded = false;

        db.collection("users").document(userId)
                .collection("daily_activity").document(date)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot != null && snapshot.exists()) {
                        Long steps = snapshot.getLong("steps");
                        dailyBaseSteps = steps != null ? steps.intValue() : 0;

                        Boolean gRewarded = snapshot.getBoolean("goalRewarded");
                        goalRewarded = gRewarded != null ? gRewarded : false;

                        Boolean kRewarded = snapshot.getBoolean("kmRewarded");
                        kmRewarded = kRewarded != null ? kRewarded : false;

                        Boolean cRewarded = snapshot.getBoolean("kcalRewarded");
                        kcalRewarded = cRewarded != null ? cRewarded : false;
                    } else {
                        // Reset for a fresh day if document doesn't exist
                        dailyBaseSteps = 0;
                        goalRewarded = false;
                        kmRewarded = false;
                        kcalRewarded = false;
                    }
                    baseLoaded = true;
                    lastPersistedSteps = getDailySteps();
                })
                .addOnFailureListener(e -> baseLoaded = true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                    NOTIFICATION_ID,
                    buildNotification(getString(R.string.notif_running_title),
                            getString(R.string.notif_running_text, 0, 0)),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            );
        } else {
            startForeground(
                    NOTIFICATION_ID,
                    buildNotification(getString(R.string.notif_running_title),
                            getString(R.string.notif_running_text, 0, 0))
            );
        }

        if (stepCounterSensor != null) {
            sensorManager.registerListener(
                    this,
                    stepCounterSensor,
                    SensorManager.SENSOR_DELAY_UI
            );
        } else {
            stopSelf();
        }

        return START_STICKY;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        if (event.sensor.getType() != Sensor.TYPE_STEP_COUNTER) {
            return;
        }

        // Check if the day changed while the service was running
        String currentDate = today();
        if (!currentDate.equals(lastTrackedDate)) {
            // Persist the final count for the previous day
            persistDailyActivity(lastTrackedDate);

            // Reset for the new day
            lastTrackedDate = currentDate;
            startSteps = event.values[0];
            currentSessionSteps = 0;
            dailyBaseSteps = 0;
            goalRewarded = false;
            kmRewarded = false;
            kcalRewarded = false;
            goalRewardPending = false;
            kmRewardPending = false;
            kcalRewardPending = false;
            lastPersistedSteps = -1;

            loadDailyBaseSteps(currentDate);
            return;
        }

        if (!baseLoaded) return;

        float totalStepsSinceReboot = event.values[0];

        if (startSteps < 0) {
            startSteps = totalStepsSinceReboot;
        }

        currentSessionSteps = (int) (totalStepsSinceReboot - startSteps);

        updateNotification(getDailySteps());

        // only write every 10 steps to avoid spamming Firestore
        if (baseLoaded && getDailySteps() - lastPersistedSteps >= 10) {
            persistDailyActivity(lastTrackedDate);
        }

        // each daily goal is also a quest: completing it awards points AND counts
        // towards the profile's "Quests" stat (see completeActivityQuest)
        if (baseLoaded && !goalRewarded && !goalRewardPending && getDailySteps() >= WALK_QUEST_STEP_GOAL) {
            goalRewardPending = true;
            completeActivityQuest("activity_steps", getString(R.string.quest_walk_title), 50,
                    () -> {
                        goalRewardPending = false;
                        goalRewarded = true;
                        persistDailyActivity(lastTrackedDate);
                        onStepGoalReached();
                    },
                    () -> goalRewardPending = false);
        }

        // distance goal: 1 km
        if (baseLoaded && !kmRewarded && !kmRewardPending && getKm() >= 1.0) {
            kmRewardPending = true;
            completeActivityQuest("activity_distance", getString(R.string.quest_distance_title), 60,
                    () -> {
                        kmRewardPending = false;
                        kmRewarded = true;
                        persistDailyActivity(lastTrackedDate);
                    },
                    () -> kmRewardPending = false);
        }

        // calorie goal: 50 kcal
        if (baseLoaded && !kcalRewarded && !kcalRewardPending && getCalories() >= 50) {
            kcalRewardPending = true;
            completeActivityQuest("activity_burn", getString(R.string.quest_burn_title), 75,
                    () -> {
                        kcalRewardPending = false;
                        kcalRewarded = true;
                        persistDailyActivity(lastTrackedDate);
                    },
                    () -> kcalRewardPending = false);
        }
    }

    /** Returns today's total steps: the stored daily base plus the steps of the current session. */
    public int getDailySteps() {
        return dailyBaseSteps + currentSessionSteps;
    }

    /** Converts today's steps into distance in kilometers. */
    public double getKm() {
        return (getDailySteps() * STEP_LENGTH_METERS) / 1000.0;
    }

    /** Estimates today's active minutes from the step count. */
    public int getActiveMinutes() {
        return (int) (getDailySteps() / STEPS_PER_MINUTE);
    }

    /** Estimates calories burned today from the step count. */
    public int getCalories() {
        return (int) Math.round(getDailySteps() * CALORIES_PER_STEP);
    }

    /**
     * Writes today's totals to Firestore. Uses a merge so other fields of the daily document
     * are preserved.
     * @param date day in yyyy-MM-dd format
     */
    private void persistDailyActivity(String date) {
        String userId = auth != null ? auth.getUid() : null;
        if (userId == null) {
            return;
        }

        int dailySteps = getDailySteps();
        lastPersistedSteps = dailySteps;

        Map<String, Object> data = new HashMap<>();
        data.put("steps", dailySteps);
        data.put("stepGoal", DEFAULT_STEP_GOAL);
        data.put("km", getKm());
        data.put("activeMinutes", getActiveMinutes());
        data.put("calories", getCalories());
        data.put("goalRewarded", goalRewarded);
        data.put("kmRewarded", kmRewarded);
        data.put("kcalRewarded", kcalRewarded);

        db.collection("users").document(userId)
                .collection("daily_activity").document(date)
                .set(data, SetOptions.merge());
    }

    /**
     * Completes a daily goal as a quest: awards points, updates the level and increments the
     * quest count. The transaction is idempotent per day, so a goal is never counted twice even
     * if the in-memory flags are lost (e.g. after a crash).
     */
    private void completeActivityQuest(String questId, String title, int points,
                                       Runnable onSuccess, Runnable onFailure) {
        String userId = auth != null ? auth.getUid() : null;
        if (userId == null) {
            onFailure.run();
            return;
        }

        DocumentReference userRef = db.collection("users").document(userId);
        DocumentReference questRef = userRef.collection("completed_quests").document(questId);

        db.runTransaction(transaction -> {
            // Firestore requires all reads before any writes
            DocumentSnapshot questSnapshot = transaction.get(questRef);
            DocumentSnapshot userSnapshot = transaction.get(userRef);

            // already counted this quest today -> don't award or count it again
            Long previous = questSnapshot.getLong("completedAt");
            if (previous != null && isToday(previous)) {
                return null;
            }

            Map<String, Object> quest = new HashMap<>();
            quest.put("title", title);
            quest.put("points", points);
            quest.put("type", "ACTIVITY");
            quest.put("completedAt", System.currentTimeMillis());
            transaction.set(questRef, quest);

            Long currentPoints = userSnapshot.getLong("currentPoints");
            long newPoints = (currentPoints != null ? currentPoints : 0L) + points;
            long newLevel = (newPoints / 1000) + 1;
            Long totalQuests = userSnapshot.getLong("totalQuests");
            long newTotalQuests = (totalQuests != null ? totalQuests : 0L) + 1;

            transaction.update(userRef, "currentPoints", newPoints);
            transaction.update(userRef, "level", newLevel);
            transaction.update(userRef, "totalQuests", newTotalQuests);
            return null;
        }).addOnSuccessListener(aVoid -> onSuccess.run())
                .addOnFailureListener(e -> onFailure.run());
    }

    /** Returns whether the given epoch-millis timestamp falls on today. */
    private boolean isToday(long millis) {
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(millis);
        Calendar now = Calendar.getInstance();
        return then.get(Calendar.YEAR) == now.get(Calendar.YEAR)
                && then.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR);
    }

    private String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }

    /**
     * Called when the daily step goal is reached: saves progress and shows a success
     * notification. The service intentionally keeps running so steps, distance and active
     * minutes continue to accumulate.
     */
    private void onStepGoalReached() {
        persistDailyActivity(lastTrackedDate);

        Notification notification = buildNotification(
                getString(R.string.notif_step_goal_title),
                getString(R.string.notif_step_goal_text, getDailySteps())
                        + " +50 " + getString(R.string.label_points)
        );

        NotificationManager manager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (manager != null) {
            manager.notify(NOTIFICATION_ID, notification);
        }
    }

    /** Updates the ongoing notification with the current step count. */
    private void updateNotification(int steps) {
        Notification notification = buildNotification(
                getString(R.string.notif_running_title),
                getString(R.string.notif_running_text, steps, getCalories())
        );

        NotificationManager manager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (manager != null) {
            manager.notify(NOTIFICATION_ID, notification);
        }
    }

    /** Builds the notification shown by the foreground service. */
    private Notification buildNotification(String title, String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_walker_playstore)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }

    /** Creates the notification channel for the step counter (required on Android 8+). */
    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Step Counter",
                NotificationManager.IMPORTANCE_LOW
        );

        NotificationManager manager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (baseLoaded && currentSessionSteps > 0) {
            persistDailyActivity(lastTrackedDate);
        }

        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not needed here.
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    /** Returns the steps of the current session only, excluding the stored daily base. */
    public int getSteps() {
        return currentSessionSteps;
    }
}
