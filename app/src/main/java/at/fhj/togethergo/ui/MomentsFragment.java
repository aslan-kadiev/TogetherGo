package at.fhj.togethergo.ui;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import at.fhj.togethergo.R;
import at.fhj.togethergo.data.PhotoPrompts;
import at.fhj.togethergo.data.PhotoRepository;
import at.fhj.togethergo.data.entities.PhotoEntity;
import at.fhj.togethergo.data.models.PhotoPrompt;
import at.fhj.togethergo.databinding.FragmentMomentsBinding;
import at.fhj.togethergo.util.ImageUtils;

/**
 * Moments screen (photo journal).
 * <p>
 * Shows the daily photo prompt, a few extra photo quests and the gallery of captured moments.
 * Photos can be taken with the camera or picked from the gallery; completing a prompt awards
 * points.
 */
public class MomentsFragment extends Fragment {

    private FragmentMomentsBinding binding;
    private MomentsViewModel viewModel;
    private PhotoAdapter adapter;
    private PhotoQuestAdapter photoQuestAdapter;

    private List<PhotoEntity> currentMoments = new ArrayList<>();

    // remembered between launching a capture and its async result
    private File pendingCameraFile;
    private PhotoPrompt pendingPrompt;

    private final ActivityResultLauncher<Uri> takePicture =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (Boolean.TRUE.equals(success) && pendingCameraFile != null) {
                    promptForCaption(pendingCameraFile, null, pendingPrompt);
                } else if (pendingCameraFile != null && pendingCameraFile.exists()) {
                    pendingCameraFile.delete(); // discard the empty placeholder on cancel
                }
            });

    private final ActivityResultLauncher<String> pickImage =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    promptForCaption(null, uri, pendingPrompt);
                }
            });

    private final ActivityResultLauncher<String> requestCamera =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (Boolean.TRUE.equals(granted)) {
                    launchCamera();
                } else {
                    toast(getString(R.string.camera_permission_needed));
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentMomentsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(MomentsViewModel.class);

        setupRecyclerView();
        bindDailyPrompt();

        binding.btnCapture.setOnClickListener(v -> startCapture(PhotoPrompts.today()));
        binding.fabAddMoment.setOnClickListener(v -> startCapture(null));

        binding.loadingBox.getRoot().setVisibility(View.VISIBLE);
        viewModel.getMoments().observe(getViewLifecycleOwner(), moments -> {
            binding.loadingBox.getRoot().setVisibility(View.GONE);
            currentMoments = moments != null ? moments : new ArrayList<>();
            adapter.setMoments(currentMoments);
            updateEmptyState();
            updateCount();
            bindDailyPrompt();
            refreshPhotoQuests();
        });
    }

    /** Refreshes the extra photo quests, marking those already completed today. */
    private void refreshPhotoQuests() {
        photoQuestAdapter.submit(PhotoPrompts.todaysExtra(4), completedTodayIds());
    }

    /** Returns the ids of the prompts already completed today. */
    private Set<String> completedTodayIds() {
        String today = todayKey();
        Set<String> ids = new HashSet<>();
        for (PhotoEntity moment : currentMoments) {
            if (moment.promptId != null && today.equals(moment.dateKey)) {
                ids.add(moment.promptId);
            }
        }
        return ids;
    }

    /** Sets up the gallery grid and the list of extra photo quests. */
    private void setupRecyclerView() {
        adapter = new PhotoAdapter(this::showMomentDialog);
        binding.rvMoments.setLayoutManager(new GridLayoutManager(getContext(), 3));
        binding.rvMoments.setAdapter(adapter);

        photoQuestAdapter = new PhotoQuestAdapter(this::startCapture);
        binding.rvPhotoQuests.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvPhotoQuests.setNestedScrollingEnabled(false);
        binding.rvPhotoQuests.setAdapter(photoQuestAdapter);
    }

    /** Shows today's photo prompt and disables the button if it was already completed today. */
    private void bindDailyPrompt() {
        PhotoPrompt prompt = PhotoPrompts.today();
        binding.tvPromptEmoji.setText(prompt.emoji);
        binding.tvPromptTitle.setText(prompt.titleRes);
        binding.tvPromptDesc.setText(prompt.descRes);
        binding.tvPromptPoints.setText(getString(R.string.quest_points, prompt.points));

        boolean done = isPromptDoneToday(prompt);
        binding.btnCapture.setEnabled(!done);
        binding.btnCapture.setText(done ? R.string.captured_today : R.string.capture_moment);
    }

    /** Returns whether the given prompt already has a photo today. */
    private boolean isPromptDoneToday(PhotoPrompt prompt) {
        String today = todayKey();
        for (PhotoEntity moment : currentMoments) {
            if (prompt.id.equals(moment.promptId) && today.equals(moment.dateKey)) {
                return true;
            }
        }
        return false;
    }

    /** Shows either the gallery or the empty state. */
    private void updateEmptyState() {
        boolean empty = currentMoments.isEmpty();
        binding.tvEmptyMoments.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.rvMoments.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    /** Updates the moment counter, or shows an encouraging hint if there are none yet. */
    private void updateCount() {
        int count = currentMoments.size();
        if (count == 0) {
            binding.tvMomentCount.setText(R.string.moments_encouragement);
        } else {
            binding.tvMomentCount.setText(
                    getResources().getQuantityString(R.plurals.moments_count, count, count));
        }
    }

    // Capture flow

    /**
     * Starts capturing a moment and lets the user choose between camera and gallery.
     * @param prompt the related prompt, or {@code null} for a free moment
     */
    private void startCapture(PhotoPrompt prompt) {
        pendingPrompt = prompt;
        CharSequence[] options = {getString(R.string.use_camera), getString(R.string.use_gallery)};
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.add_moment)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        ensureCameraThenLaunch();
                    } else {
                        pickImage.launch("image/*");
                    }
                })
                .show();
    }

    /** Launches the camera, requesting the camera permission first if needed. */
    private void ensureCameraThenLaunch() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            launchCamera();
        } else {
            requestCamera.launch(Manifest.permission.CAMERA);
        }
    }

    /** Opens the camera, falling back to the gallery if no camera is available. */
    private void launchCamera() {
        try {
            pendingCameraFile = viewModel.newImageFile();
            takePicture.launch(viewModel.uriFor(pendingCameraFile));
        } catch (ActivityNotFoundException e) {
            toast(getString(R.string.no_camera));
            pickImage.launch("image/*");
        }
    }

    /** Asks for an optional caption, then saves the moment. */
    private void promptForCaption(File file, Uri galleryUri, PhotoPrompt prompt) {
        final EditText input = new EditText(requireContext());
        input.setHint(R.string.caption_hint);
        int pad = dpToPx(20);
        input.setPadding(pad, pad / 2, pad, pad / 2);

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.add_a_note)
                .setView(input)
                .setPositiveButton(R.string.action_save, (d, w) ->
                        doSave(file, galleryUri, input.getText().toString().trim(), prompt))
                .setNegativeButton(R.string.action_skip, (d, w) ->
                        doSave(file, galleryUri, "", prompt))
                .setCancelable(false)
                .show();
    }

    /** Saves the moment (from camera or gallery) and shows feedback. */
    private void doSave(File file, Uri galleryUri, String caption, PhotoPrompt prompt) {
        PhotoRepository.SaveCallback callback = new PhotoRepository.SaveCallback() {
            @Override
            public void onSaved(boolean awardedPoints, int points) {
                if (binding == null) {
                    return;
                }
                if (awardedPoints) {
                    showRewardFeedback(points);
                } else {
                    toast(getString(R.string.moment_saved));
                }
            }

            @Override
            public void onError() {
                if (binding != null) {
                    toast(getString(R.string.moment_save_failed));
                }
            }
        };

        if (file != null) {
            viewModel.saveCameraMoment(file, caption, prompt, callback);
        } else {
            viewModel.saveGalleryMoment(galleryUri, caption, prompt, callback);
        }
    }

    // Viewing a saved moment

    /** Shows a saved moment with image, caption and time, and offers to delete it. */
    private void showMomentDialog(PhotoEntity moment) {
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = dpToPx(16);
        content.setPadding(pad, pad, pad, 0);

        ImageView image = new ImageView(requireContext());
        image.setAdjustViewBounds(true);
        image.setMinimumHeight(dpToPx(200));
        image.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.bg_subtle));
        content.addView(image, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // decode off the main thread so opening a moment never freezes a weak device
        final String path = moment.filePath;
        new Thread(() -> {
            Bitmap bitmap = ImageUtils.decodeSampled(path, 900, 900);
            if (bitmap != null) {
                image.post(() -> image.setImageBitmap(bitmap));
            }
        }).start();

        if (!TextUtils.isEmpty(moment.caption)) {
            TextView caption = new TextView(requireContext());
            caption.setText(moment.caption);
            caption.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
            caption.setTextSize(15f);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.topMargin = dpToPx(12);
            content.addView(caption, cp);
        }

        TextView when = new TextView(requireContext());
        when.setText(new SimpleDateFormat("EEEE, MMM d · HH:mm", Locale.getDefault()).format(new Date(moment.timestamp)));
        when.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_tertiary));
        when.setTextSize(12f);
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wp.topMargin = dpToPx(8);
        content.addView(when, wp);

        String title = !TextUtils.isEmpty(moment.promptText) ? moment.promptText : getString(R.string.moment_free_title);

        new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setView(content)
                .setPositiveButton(R.string.action_close, null)
                .setNegativeButton(R.string.action_delete, (d, w) -> confirmDelete(moment))
                .show();
    }

    /** Asks for confirmation before deleting a moment. */
    private void confirmDelete(PhotoEntity moment) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete_moment_title)
                .setMessage(R.string.delete_moment_msg)
                .setPositiveButton(R.string.action_delete, (d, w) -> {
                    viewModel.deleteMoment(moment);
                    toast(getString(R.string.moment_deleted));
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    /** Shows a green confirmation with the points earned for the moment. */
    private void showRewardFeedback(int points) {
        Snackbar snackbar = Snackbar.make(binding.getRoot(),
                getString(R.string.moment_reward, points), Snackbar.LENGTH_LONG);
        snackbar.getView().setBackgroundColor(
                ContextCompat.getColor(requireContext(), R.color.accent_green_dark));
        snackbar.show();
    }

    private String todayKey() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private void toast(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
