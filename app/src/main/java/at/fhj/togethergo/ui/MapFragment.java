package at.fhj.togethergo.ui;

import android.Manifest;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import at.fhj.togethergo.R;
import at.fhj.togethergo.data.QuestsRepository;
import at.fhj.togethergo.data.entities.MapMarker;
import at.fhj.togethergo.data.models.Quest;
import at.fhj.togethergo.databinding.FragmentMapBinding;

/**
 * Map screen.
 * <p>
 * Shows location quests as markers and completes them when the user gets close enough. Users can
 * also drop meeting-point markers that are visible to everyone and expire after a chosen duration.
 */
public class MapFragment extends Fragment implements OnMapReadyCallback {

    private GoogleMap mMap;
    private FragmentMapBinding binding;
    private boolean isAddingMarker = false;

    private QuestsRepository questsRepository;
    private LocationManager locationManager;
    private LocationListener locationListener;

    private final List<Quest> locationQuests = new ArrayList<>();
    private final List<Marker> questMarkers = new ArrayList<>();
    private final List<Marker> userMarkers = new ArrayList<>();
    private final Set<String> completing = new HashSet<>();

    private ListenerRegistration markersListener;
    private String currentUserName;
    // ids of this user's own still-active markers — enforces one quest per user
    private final List<String> myActiveMarkerIds = new ArrayList<>();

    // expired markers are pruned on a timer, not only when the collection changes,
    // so they disappear on their own even while the map just sits open
    private static final long PRUNE_INTERVAL_MS = 30 * 1000L;
    private final Handler pruneHandler = new Handler(Looper.getMainLooper());
    private final List<DocumentSnapshot> lastMarkerDocs = new ArrayList<>();

    private final Runnable pruneRunnable = new Runnable() {
        @Override
        public void run() {
            pruneExpiredMarkers();
            pruneHandler.postDelayed(this, PRUNE_INTERVAL_MS);
        }
    };

    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (!isAdded()) return;
                if (hasLocationPermission()) {
                    enableLocationFeatures();
                } else {
                    Toast.makeText(getContext(),
                            R.string.location_permission_needed,
                            Toast.LENGTH_LONG).show();
                }
            });

    public MapFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentMapBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        questsRepository = new QuestsRepository();
        locationManager = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);

        loadCurrentUserName();

        binding.loadingBox.getRoot().setVisibility(View.VISIBLE);
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        binding.fabAddMarker.setOnClickListener(v -> {
            isAddingMarker = !isAddingMarker;
            if (isAddingMarker) {
                Toast.makeText(getContext(), R.string.click_to_set_marker, Toast.LENGTH_SHORT).show();
                binding.fabAddMarker.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
            } else {
                binding.fabAddMarker.setImageResource(android.R.drawable.ic_input_add);
            }
        });

        // keep the quest markers in sync with Firestore
        questsRepository.getLocationQuests().observe(getViewLifecycleOwner(), quests -> {
            locationQuests.clear();
            if (quests != null) {
                locationQuests.addAll(quests);
            }
            drawQuestMarkers();
        });
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        if (!isAdded()) return;
        mMap = googleMap;
        if (binding != null) {
            binding.loadingBox.getRoot().setVisibility(View.GONE);
        }

        LatLng tt = new LatLng(47.06982275642366, 15.40652830009696);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(tt, 15));

        mMap.setInfoWindowAdapter(new ActivityInfoWindowAdapter());

        loadMarkersFromFirestore();
        drawQuestMarkers();

        // keep pruning expired markers on a timer while this screen is shown
        pruneHandler.removeCallbacks(pruneRunnable);
        pruneHandler.postDelayed(pruneRunnable, PRUNE_INTERVAL_MS);

        mMap.setOnMapClickListener(latLng -> {
            if (isAddingMarker) {
                if (!myActiveMarkerIds.isEmpty()) {
                    showReplaceExistingDialog(latLng);
                } else {
                    showMarkerNameDialog(latLng);
                }
                exitAddMode();
            }
        });

        if (hasLocationPermission()) {
            enableLocationFeatures();
        } else {
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    /** Leaves marker placement mode and restores the add icon. */
    private void exitAddMode() {
        isAddingMarker = false;
        if (binding != null) {
            binding.fabAddMarker.setImageResource(android.R.drawable.ic_input_add);
        }
    }

    private boolean hasLocationPermission() {
        Context context = getContext();
        return context != null && ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /** Enables the my-location layer and starts location updates if permission is granted. */
    private void enableLocationFeatures() {
        if (mMap == null || !hasLocationPermission()) {
            return;
        }
        try {
            mMap.setMyLocationEnabled(true);
            startLocationUpdates();
        } catch (SecurityException e) {
            // permission revoked between the check and the call; nothing else to do
        }
    }

    /** Requests GPS and network location updates to check the distance to location quests. */
    private void startLocationUpdates() {
        if (locationManager == null || !hasLocationPermission() || locationListener != null) {
            return;
        }

        locationListener = location -> checkQuestProximity(location);

        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 4000, 10, locationListener);
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 4000, 10, locationListener);
            }
        } catch (SecurityException e) {
            // ignore, handled by the permission flow
        }
    }

    /** Completes every location quest the user is close enough to. */
    private void checkQuestProximity(Location location) {
        for (Quest quest : new ArrayList<>(locationQuests)) {
            if (quest.id == null || completing.contains(quest.id)) {
                continue;
            }

            float[] result = new float[1];
            Location.distanceBetween(location.getLatitude(), location.getLongitude(),
                    quest.latitude, quest.longitude, result);

            if (result[0] <= quest.effectiveRadius()) {
                completing.add(quest.id);
                completeLocationQuest(quest);
            }
        }
    }

    /**
     * Completes a location quest via the repository. Shows a message on success and allows
     * a retry on failure.
     */
    private void completeLocationQuest(Quest quest) {
        questsRepository.completeQuest(quest, new QuestsRepository.OnQuestCompletedListener() {
            @Override
            public void onSuccess() {
                if (isAdded() && binding != null) {
                    Snackbar.make(binding.getRoot(),
                            getString(R.string.quest_completed_loc, quest.title, quest.points),
                            Snackbar.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Exception e) {
                completing.remove(quest.id); // allow another attempt
            }
        });
    }

    /** Redraws the location quests as blue markers. */
    private void drawQuestMarkers() {
        if (mMap == null || !isAdded()) {
            return;
        }

        for (Marker marker : questMarkers) {
            marker.remove();
        }
        questMarkers.clear();

        for (Quest quest : locationQuests) {
            Marker marker = mMap.addMarker(new MarkerOptions()
                    .position(new LatLng(quest.latitude, quest.longitude))
                    .title(quest.title)
                    .snippet(getString(R.string.quest_marker_snippet, quest.points))
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
            if (marker != null) {
                questMarkers.add(marker);
            }
        }
    }

    /** Loads the user's display name once so new markers can be attributed to them. */
    private void loadCurrentUserName() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            return;
        }
        FirebaseFirestore.getInstance().collection("users").document(uid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot != null && snapshot.exists()) {
                        currentUserName = snapshot.getString("name");
                    }
                });
    }

    /**
     * Listens to the shared markers in Firestore so a new marker appears for everyone, and
     * removes expired markers from the map and from Firestore.
     */
    private void loadMarkersFromFirestore() {
        if (markersListener != null) {
            return;
        }
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        markersListener = db.collection("markers")
                .addSnapshotListener((snapshots, e) -> {
                    if (!isAdded()) return;
                    if (e != null) {
                        Toast.makeText(getContext(), getString(R.string.marker_load_error, e.getMessage()), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (snapshots == null || mMap == null) {
                        return;
                    }

                    for (Marker m : userMarkers) {
                        m.remove();
                    }
                    userMarkers.clear();
                    myActiveMarkerIds.clear();
                    lastMarkerDocs.clear();
                    lastMarkerDocs.addAll(snapshots.getDocuments());

                    String myUid = FirebaseAuth.getInstance().getUid();
                    long now = System.currentTimeMillis();
                    for (DocumentSnapshot document : snapshots) {
                        MapMarker marker = document.toObject(MapMarker.class);
                        if (marker == null) {
                            continue;
                        }
                        // self-cleanup: anyone who sees a stale marker removes it for all
                        if (marker.isExpired(now)) {
                            document.getReference().delete();
                            continue;
                        }
                        // remember my own active marker(s) so I can't spam more
                        if (myUid != null && myUid.equals(marker.getCreatedByUid())) {
                            myActiveMarkerIds.add(document.getId());
                        }
                        drawUserMarker(marker);
                    }
                });
    }

    /** Draws a user marker and attaches the full data as its tag for the custom info window. */
    private void drawUserMarker(MapMarker marker) {
        Marker added = mMap.addMarker(new MarkerOptions()
                .position(new LatLng(marker.getLatitude(), marker.getLongitude()))
                .title(marker.getName())
                .snippet(markerSnippet(marker))
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ROSE)));
        if (added != null) {
            added.setTag(marker);
            userMarkers.add(added);
        }
    }

    /**
     * Deletes all expired markers; the realtime listener then redraws the map without them.
     * Runs on a timer and after every change.
     */
    private void pruneExpiredMarkers() {
        long now = System.currentTimeMillis();
        for (DocumentSnapshot doc : new ArrayList<>(lastMarkerDocs)) {
            MapMarker marker = doc.toObject(MapMarker.class);
            if (marker != null && marker.isExpired(now)) {
                doc.getReference().delete();
            }
        }
    }

    /**
     * Custom info window that sizes itself to its content, so description, author, start and
     * expiry time are shown in full instead of being truncated.
     */
    private class ActivityInfoWindowAdapter implements GoogleMap.InfoWindowAdapter {
        @Override
        public View getInfoWindow(@NonNull Marker marker) {
            return null; // keep the default bubble frame
        }

        @Override
        public View getInfoContents(@NonNull Marker marker) {
            if (!isAdded()) return null;
            if (!(marker.getTag() instanceof MapMarker)) {
                return null; // quest markers keep the default rendering
            }
            MapMarker data = (MapMarker) marker.getTag();
            Context context = getContext();
            if (context == null) return null;
            View view = LayoutInflater.from(context)
                    .inflate(R.layout.marker_info_window, null);

            TextView title = view.findViewById(R.id.tvInfoTitle);
            TextView author = view.findViewById(R.id.tvInfoAuthor);
            TextView start = view.findViewById(R.id.tvInfoStart);
            TextView until = view.findViewById(R.id.tvInfoUntil);

            String name = data.getName();
            title.setText(name != null && !name.trim().isEmpty() ? name : getString(R.string.new_marker));

            String who = data.getCreatedByName();
            author.setText(getString(R.string.marker_snippet_by,
                    who != null && !who.trim().isEmpty() ? who : getString(R.string.new_marker)));

            if (data.getMeetTimeMillis() > 0) {
                start.setText(getString(R.string.marker_snippet_start, formatTime(data.getMeetTimeMillis())));
                start.setVisibility(View.VISIBLE);
            }
            if (data.getExpiresAtMillis() > 0) {
                until.setText(getString(R.string.marker_snippet_until, formatTime(data.getExpiresAtMillis())));
                until.setVisibility(View.VISIBLE);
            }
            return view;
        }
    }

    /** Builds the multi-line marker text: author plus start and expiry time if present. */
    private String markerSnippet(MapMarker marker) {
        String author = marker.getCreatedByName();
        if (author == null || author.trim().isEmpty()) {
            author = getString(R.string.new_marker);
        }
        StringBuilder snippet = new StringBuilder(getString(R.string.marker_snippet_by, author));
        if (marker.getMeetTimeMillis() > 0) {
            snippet.append('\n').append(getString(R.string.marker_snippet_start, formatTime(marker.getMeetTimeMillis())));
        }
        if (marker.getExpiresAtMillis() > 0) {
            snippet.append('\n').append(getString(R.string.marker_snippet_until, formatTime(marker.getExpiresAtMillis())));
        }
        return snippet.toString();
    }

    /** Formats epoch millis as HH:mm. */
    private String formatTime(long millis) {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(millis);
    }

    /**
     * Offers to replace the user's existing marker, since only one active marker per user
     * is allowed.
     */
    private void showReplaceExistingDialog(LatLng latLng) {
        Context context = getContext();
        if (context == null) return;
        new AlertDialog.Builder(context)
                .setTitle(R.string.marker_limit_title)
                .setMessage(R.string.marker_limit_message)
                .setPositiveButton(R.string.marker_limit_replace, (dialog, which) -> {
                    for (String id : myActiveMarkerIds) {
                        FirebaseFirestore.getInstance().collection("markers")
                                .document(id).delete();
                    }
                    myActiveMarkerIds.clear();
                    showMarkerNameDialog(latLng);
                })
                .setNegativeButton(R.string.action_cancel, (dialog, which) -> dialog.cancel())
                .show();
    }

    /** Opens the dialog for naming a marker, with an optional start time. */
    private void showMarkerNameDialog(LatLng latLng) {
        Context context = getContext();
        if (context == null) return;
        View content = LayoutInflater.from(context)
                .inflate(R.layout.dialog_add_marker, null);
        EditText input = content.findViewById(R.id.etMarkerName);

        // start time the activity begins at (optional), shown to everyone
        TextView startLabel = content.findViewById(R.id.tvStartTime);
        Button pickStart = content.findViewById(R.id.btnPickStart);
        final long[] startMillis = {0L};
        pickStart.setOnClickListener(v -> {
            Calendar nowCal = Calendar.getInstance();
            Context c = getContext();
            if (c == null) return;
            new TimePickerDialog(c, (picker, hour, minute) -> {
                Calendar chosen = Calendar.getInstance();
                chosen.set(Calendar.HOUR_OF_DAY, hour);
                chosen.set(Calendar.MINUTE, minute);
                chosen.set(Calendar.SECOND, 0);
                chosen.set(Calendar.MILLISECOND, 0);
                // a start time in the past is read as "tomorrow"
                if (chosen.getTimeInMillis() <= System.currentTimeMillis()) {
                    chosen.add(Calendar.DAY_OF_YEAR, 1);
                }
                startMillis[0] = chosen.getTimeInMillis();
                startLabel.setText(formatTime(startMillis[0]));
            }, nowCal.get(Calendar.HOUR_OF_DAY), nowCal.get(Calendar.MINUTE), true).show();
        });

        showMarker(latLng, content, context, input, startMillis);
    }

    /** Shows the duration picker (hours and minutes) and saves the new marker on confirm. */
    private void showMarker(LatLng latLng, View content, Context context, EditText input, long[] startMillis) {
        // timer-style wheels for hours and minutes the marker should live
        NumberPicker hoursPicker = content.findViewById(R.id.npHours);
        NumberPicker minutesPicker = content.findViewById(R.id.npMinutes);
        hoursPicker.setMinValue(0);
        hoursPicker.setMaxValue(23);
        hoursPicker.setValue(6);
        hoursPicker.setWrapSelectorWheel(false);
        minutesPicker.setMinValue(0);
        minutesPicker.setMaxValue(59);
        minutesPicker.setValue(0);
        minutesPicker.setFormatter(value -> String.format(Locale.getDefault(), "%02d", value));

        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle(R.string.marker_dialog_title)
                .setView(content)
                .setPositiveButton(R.string.marker_action_post, (dialog, which) -> {
                    if (!isAdded()) return;
                    String markerName = input.getText().toString().trim();
                    if (markerName.isEmpty()) {
                        markerName = getString(R.string.new_marker);
                    }
                    saveMarkerToFirestore(markerName, latLng,
                            selectedDurationMs(hoursPicker.getValue(), minutesPicker.getValue()),
                            startMillis[0]);
                })
                .setNegativeButton(R.string.action_cancel, (dialog, which) -> dialog.cancel());

        builder.show();
    }

    /**
     * Converts the chosen duration to milliseconds and enforces a minimum, so a marker never
     * expires immediately.
     */
    private long selectedDurationMs(int hours, int minutes) {
        long total = (hours * 60L + minutes) * 60L * 1000L;
        long minimum = 15 * 60L * 1000L; // 15 minutes
        return Math.max(total, minimum);
    }

    /**
     * Saves a new meeting-point marker to Firestore; the realtime listener then draws it
     * for everyone.
     */
    private void saveMarkerToFirestore(String name, LatLng latLng, long durationMs, long startMillis) {
        long now = System.currentTimeMillis();
        long expiresAt = now + durationMs;

        if (!isAdded()) return;

        String author = currentUserName != null && !currentUserName.trim().isEmpty()
                ? currentUserName : getString(R.string.new_marker);
        String uid = FirebaseAuth.getInstance().getUid();

        MapMarker marker = new MapMarker(name, latLng.latitude, latLng.longitude,
                author, uid, startMillis, expiresAt, now);

        if (uid != null) {
            FirebaseFirestore.getInstance().collection("markers")
                    .document(uid)
                    .set(marker)
                    .addOnSuccessListener(aVoid -> {
                        if (isAdded()) {
                            Toast.makeText(getContext(), R.string.marker_saved, Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnFailureListener(e -> {
                        if (isAdded()) {
                            Toast.makeText(getContext(), getString(R.string.marker_save_error, e.getMessage()), Toast.LENGTH_SHORT).show();
                        }
                    });
        }
        // the realtime listener will draw it for us (and for everyone else)
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (locationManager != null && locationListener != null) {
            locationManager.removeUpdates(locationListener);
            locationListener = null;
        }
        if (markersListener != null) {
            markersListener.remove();
            markersListener = null;
        }
        pruneHandler.removeCallbacks(pruneRunnable);
        if (questsRepository != null) {
            questsRepository.detach();
        }
        binding = null;
    }
}
