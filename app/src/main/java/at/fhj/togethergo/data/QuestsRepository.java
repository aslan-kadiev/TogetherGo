package at.fhj.togethergo.data;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import at.fhj.togethergo.data.models.Quest;

/**
 * Firestore repository for quests. Exposes the available quests (standard and location
 * quests separately), hides those already completed today and handles completion,
 * including points.
 */
public class QuestsRepository {
    private static final String TAG = "QuestsRepository";
    private final FirebaseFirestore db;
    private final FirebaseAuth auth;
    private final List<ListenerRegistration> registrations = new ArrayList<>();

    public QuestsRepository() {
        this.db = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
    }

    public LiveData<List<Quest>> getAvailableQuests() {
        return observeQuests(false);
    }

    public LiveData<List<Quest>> getLocationQuests() {
        return observeQuests(true);
    }

    /**
     * Observes quests from Firestore, combining the quest collection with the user's quests
     * completed today.
     * @param locationOnly {@code true} for location quests, {@code false} for standard quests
     */
    private LiveData<List<Quest>> observeQuests(boolean locationOnly) {
        MutableLiveData<List<Quest>> questsData = new MutableLiveData<>();
        String userId = auth.getUid();

        if (userId == null) {
            Log.w(TAG, "No user logged in, cannot observe quests.");
            return questsData;
        }

        // hold the latest of both sources so we can re-filter whenever either changes
        final List<Quest> allQuests = new ArrayList<>();
        final Set<String> completedIds = new HashSet<>();

        registrations.add(db.collection("quests")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Listen failed for quests.", e);
                        return;
                    }

                    if (snapshots != null) {
                        allQuests.clear();
                        for (QueryDocumentSnapshot doc : snapshots) {
                            allQuests.add(doc.toObject(Quest.class));
                        }
                        questsData.setValue(filterQuests(allQuests, completedIds, locationOnly));
                    }
                }));

        // drop the quests this user has already finished so points can't be farmed twice
        registrations.add(db.collection("users").document(userId)
                .collection("completed_quests")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Listen failed for completed quests.", e);
                        return;
                    }

                    completedIds.clear();
                    if (snapshots != null) {
                        for (QueryDocumentSnapshot doc : snapshots) {
                            Long completedAt = doc.getLong("completedAt");
                            if (completedAt != null && isToday(completedAt)) {
                                completedIds.add(doc.getId());
                            }
                        }
                    }
                    questsData.setValue(filterQuests(allQuests, completedIds, locationOnly));
                }));

        return questsData;
    }

    /** Returns whether the given epoch-millis timestamp falls on today. */
    private boolean isToday(long millis) {
        Calendar cal1 = Calendar.getInstance();
        cal1.setTimeInMillis(millis);
        Calendar cal2 = Calendar.getInstance();
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }

    /** Filters quests by type and hides those completed today. */
    private List<Quest> filterQuests(List<Quest> quests, Set<String> completedIds, boolean locationOnly) {
        List<Quest> result = new ArrayList<>();
        for (Quest quest : quests) {
            boolean completed = quest.id != null && completedIds.contains(quest.id);
            if (completed || quest.isLocationQuest() != locationOnly) {
                continue;
            }
            result.add(quest);
        }
        return result;
    }

    /** Removes the Firestore listeners so they don't accumulate when screens are recreated. */
    public void detach() {
        for (ListenerRegistration registration : registrations) {
            registration.remove();
        }
        registrations.clear();
    }

    /**
     * Completes a quest. A transaction updates points, level and total quest count; a quest
     * completed again on the same day is recorded but not rewarded twice.
     */
    public void completeQuest(Quest quest, OnQuestCompletedListener listener) {
        String userId = auth.getUid();
        if (userId == null) return;

        DocumentReference userRef = db.collection("users").document(userId);
        DocumentReference userQuestRef = userRef.collection("completed_quests").document(quest.id);

        db.runTransaction(transaction -> {
            // Firestore requires all reads to run before any writes
            DocumentSnapshot questSnapshot = transaction.get(userQuestRef);
            DocumentSnapshot userSnapshot = transaction.get(userRef);

            // already completed today -> record it again but don't award points twice
            Long previous = questSnapshot.getLong("completedAt");
            boolean alreadyToday = previous != null && isToday(previous);

            quest.completedAt = System.currentTimeMillis();
            transaction.set(userQuestRef, quest);

            if (!alreadyToday) {
                long currentPoints = userSnapshot.contains("currentPoints") ? userSnapshot.getLong("currentPoints") : 0;
                long newPoints = currentPoints + quest.points;
                long newLevel = (newPoints / 1000) + 1;

                long totalQuests = userSnapshot.contains("totalQuests") ? userSnapshot.getLong("totalQuests") : 0;

                transaction.update(userRef, "currentPoints", newPoints);
                transaction.update(userRef, "level", (int) newLevel);
                transaction.update(userRef, "totalQuests", totalQuests + 1);
            }

            return null;
        }).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                listener.onSuccess();
            } else {
                Log.e(TAG, "Transaction failed.", task.getException());
                listener.onFailure(task.getException());
            }
        });
    }

    /** Callback for the result of completing a quest. */
    public interface OnQuestCompletedListener {
        void onSuccess();

        void onFailure(Exception e);
    }
}
