package at.fhj.togethergo.ui;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import java.io.File;
import java.util.List;

import at.fhj.togethergo.data.PhotoRepository;
import at.fhj.togethergo.data.entities.PhotoEntity;
import at.fhj.togethergo.data.models.PhotoPrompt;

/**
 * View model for the moments screen. Delegates loading, saving and deleting to
 * {@link PhotoRepository}.
 */
public class MomentsViewModel extends AndroidViewModel {

    private final PhotoRepository repository;
    private final LiveData<List<PhotoEntity>> moments;

    public MomentsViewModel(@NonNull Application application) {
        super(application);
        this.repository = new PhotoRepository(application);
        this.moments = repository.getAllMoments();
    }

    public LiveData<List<PhotoEntity>> getMoments() {
        return moments;
    }

    public File newImageFile() {
        return repository.newImageFile();
    }

    public Uri uriFor(File file) {
        return repository.uriFor(file);
    }

    /** Saves a moment captured with the camera. */
    public void saveCameraMoment(File file, String caption, PhotoPrompt prompt, PhotoRepository.SaveCallback callback) {
        repository.saveCameraMoment(file, caption, prompt, callback);
    }

    /** Saves a moment picked from the gallery. */
    public void saveGalleryMoment(Uri source, String caption, PhotoPrompt prompt, PhotoRepository.SaveCallback callback) {
        repository.saveGalleryMoment(source, caption, prompt, callback);
    }

    /** Deletes a moment. */
    public void deleteMoment(PhotoEntity moment) {
        repository.deleteMoment(moment);
    }

}
