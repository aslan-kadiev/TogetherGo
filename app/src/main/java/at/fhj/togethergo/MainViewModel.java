package at.fhj.togethergo;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import at.fhj.togethergo.data.entities.UserEntity;

/** Shared view model holding the signed-in user's Firestore profile. */
public class MainViewModel extends ViewModel {
    private final MutableLiveData<UserEntity> firestoreUser = new MutableLiveData<>();
    private ListenerRegistration userListener;

    public MainViewModel() {
        listenToUserChanges();
    }

    private void listenToUserChanges() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            return;
        }

        DocumentReference docRef = FirebaseFirestore.getInstance()
                .collection("users")
                .document(currentUser.getUid());

        userListener = docRef.addSnapshotListener((snapshot, error) -> {
            if (error != null || snapshot == null || !snapshot.exists()) {
                return;
            }

            String name = snapshot.getString("name");
            Long level = snapshot.getLong("level");
            Long points = snapshot.getLong("currentPoints");
            Long totalQuests = snapshot.getLong("totalQuests");
            firestoreUser.setValue(new UserEntity(
                    name != null ? name : "User",
                    level != null ? level.intValue() : 1,
                    points != null ? points.intValue() : 0,
                    totalQuests != null ? totalQuests.intValue() : 0));
        });
    }

    public LiveData<UserEntity> getUser() {
        return firestoreUser;
    }

    @Override
    protected void onCleared() {
        if (userListener != null) {
            userListener.remove();
        }
        super.onCleared();
    }
}
