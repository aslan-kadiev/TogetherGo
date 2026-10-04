package at.fhj.togethergo.data;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.content.FileProvider;
import androidx.lifecycle.LiveData;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import at.fhj.togethergo.data.dao.PhotoDao;
import at.fhj.togethergo.data.entities.PhotoEntity;
import at.fhj.togethergo.data.models.PhotoPrompt;

/**
 * Stores captured moments locally: image files in private app storage, metadata in Room.
 * When a photo completes a prompt, points are credited to the Firestore profile.
 */
public class PhotoRepository {

    private static final String TAG = "PhotoRepository";
    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor();

    /** Result callback after a moment has been saved. */
    public interface SaveCallback {
        /**
         * Called after a successful save.
         * @param awardedPoints whether points were awarded
         * @param points        number of points awarded (0 if none)
         */
        void onSaved(boolean awardedPoints, int points);

        /** Called if saving failed. */
        void onError();
    }

    /** Internal callback reporting whether crediting the points succeeded. */
    private interface AwardCallback {
        void onResult(boolean confirmed, boolean awarded);
    }

    private final Context appContext;
    private final PhotoDao photoDao;
    private final File momentsDir;
    private final Handler mainHandler;
    private final FirebaseFirestore db;
    private final FirebaseAuth auth;

    public PhotoRepository(Context context) {
        this.appContext = context.getApplicationContext();
        this.photoDao = AppDatabase.getDatabase(appContext).photoDao();
        this.momentsDir = new File(appContext.getFilesDir(), "moments");
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.db = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();

        // create the storage folder off the main thread; the single-thread executor
        // guarantees this runs before any save task that needs the folder
        IO_EXECUTOR.execute(() -> {
            if (!momentsDir.exists()) {
                momentsDir.mkdirs();
            }
        });
    }

    public LiveData<List<PhotoEntity>> getAllMoments() {
        return photoDao.getAll();
    }

    /** Creates a new empty file for the camera to write the photo into. */
    public File newImageFile() {
        return new File(momentsDir, "moment_" + System.currentTimeMillis() + ".jpg");
    }

    /** Returns a FileProvider content URI for the given file. */
    public Uri uriFor(File file) {
        return FileProvider.getUriForFile(appContext, appContext.getPackageName() + ".fileprovider", file);
    }

    /**
     * Saves a moment captured with the camera. The file already exists; only the metadata is
     * added and a newly completed prompt is rewarded.
     */
    public void saveCameraMoment(File file, String caption, PhotoPrompt prompt, SaveCallback callback) {
        IO_EXECUTOR.execute(() -> finishSave(file, caption, prompt, callback));
    }

    /**
     * Saves a moment picked from the gallery. The image is first copied into app storage,
     * because the gallery URI belongs to another app.
     */
    public void saveGalleryMoment(Uri source, String caption, PhotoPrompt prompt, SaveCallback callback) {
        IO_EXECUTOR.execute(() -> {
            File file = newImageFile();
            if (!copyUriToFile(source, file)) {
                postError(callback);
                return;
            }
            finishSave(file, caption, prompt, callback);
        });
    }

    /** Deletes a moment together with its image file. */
    public void deleteMoment(PhotoEntity moment) {
        IO_EXECUTOR.execute(() -> {
            if (moment.filePath != null) {
                File file = new File(moment.filePath);
                if (file.exists()) {
                    file.delete();
                }
            }
            photoDao.delete(moment);
        });
    }

    /**
     * Inserts the Room record and awards the prompt's points, unless the prompt was already
     * completed today.
     */
    private void finishSave(File file, String caption, PhotoPrompt prompt, SaveCallback callback) {
        if (file == null || !file.exists() || file.length() == 0) {
            postError(callback);
            return;
        }

        String dateKey = dateKey();
        String promptId = prompt != null ? prompt.id : null;
        String promptText = prompt != null ? appContext.getString(prompt.titleRes) : null;

        if (prompt == null) {
            insertMoment(file, caption, null, null, dateKey);
            postSaved(callback, false, 0);
            return;
        }

        awardPoints(prompt, dateKey, (confirmed, awarded) -> IO_EXECUTOR.execute(() -> {
            // Only a confirmed Firestore result marks the prompt as completed locally.
            // A failed request keeps the photo but allows a later capture to retry.
            insertMoment(file, caption, confirmed ? promptId : null,
                    confirmed ? promptText : null, dateKey);
            postSaved(callback, awarded, awarded ? prompt.points : 0);
        }));
    }

    private void insertMoment(File file, String caption, String promptId,
                              String promptText, String dateKey) {
        photoDao.insert(new PhotoEntity(
                file.getAbsolutePath(),
                caption != null ? caption : "",
                promptId,
                promptText,
                dateKey,
                System.currentTimeMillis()));
    }

    /** Copies the content of a URI into a local file. Returns {@code false} on failure. */
    private boolean copyUriToFile(Uri uri, File dest) {
        try (InputStream in = appContext.getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(dest)) {
            if (in == null) {
                return false;
            }
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to copy gallery image", e);
            return false;
        }
    }

    /**
     * Writes points, level and quest count to the profile. Uses read-then-write instead of a
     * transaction because it also works offline: Firestore applies the change locally and
     * syncs it later.
     */
    private void awardPoints(PhotoPrompt prompt, String dateKey, AwardCallback callback) {
        String userId = auth.getUid();
        if (userId == null) {
            mainHandler.post(() -> callback.onResult(false, false));
            return;
        }

        DocumentReference userRef = db.collection("users").document(userId);
        DocumentReference rewardRef = userRef.collection("completed_photo_quests")
                .document(dateKey + "_" + prompt.id);

        db.runTransaction(transaction -> {
                    DocumentSnapshot rewardSnapshot = transaction.get(rewardRef);
                    DocumentSnapshot userSnapshot = transaction.get(userRef);
                    if (rewardSnapshot.exists()) {
                        return false;
                    }

                    Long current = userSnapshot.getLong("currentPoints");
                    long newPoints = (current != null ? current : 0L) + prompt.points;
                    long newLevel = (newPoints / 1000) + 1;
                    Long totalQuests = userSnapshot.getLong("totalQuests");
                    long newTotalQuests = (totalQuests != null ? totalQuests : 0L) + 1;

                    Map<String, Object> data = new HashMap<>();
                    data.put("currentPoints", newPoints);
                    data.put("level", newLevel);
                    data.put("totalQuests", newTotalQuests);
                    transaction.set(userRef, data, SetOptions.merge());

                    Map<String, Object> reward = new HashMap<>();
                    reward.put("promptId", prompt.id);
                    reward.put("dateKey", dateKey);
                    reward.put("points", prompt.points);
                    reward.put("completedAt", System.currentTimeMillis());
                    transaction.set(rewardRef, reward);
                    return true;
                })
                .addOnSuccessListener(awarded -> callback.onResult(true, Boolean.TRUE.equals(awarded)))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Could not award photo quest points", e);
                    callback.onResult(false, false);
                });
    }

    private void postError(SaveCallback callback) {
        mainHandler.post(() -> {
            if (callback != null) {
                callback.onError();
            }
        });
    }

    private void postSaved(SaveCallback callback, boolean awarded, int points) {
        mainHandler.post(() -> {
            if (callback != null) {
                callback.onSaved(awarded, points);
            }
        });
    }

    private String dateKey() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }
}
