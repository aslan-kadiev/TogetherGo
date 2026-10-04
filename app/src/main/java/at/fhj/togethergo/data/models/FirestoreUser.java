package at.fhj.togethergo.data.models;

/** User profile as stored in Firestore (display name, level, reward points). */
public class FirestoreUser {
    public String displayName;
    public int level;
    public int rewardPoints;

    public FirestoreUser() {
        // Required for Firestore
    }

    public FirestoreUser(String displayName, int level, int rewardPoints) {
        this.displayName = displayName;
        this.level = level;
        this.rewardPoints = rewardPoints;
    }
}
