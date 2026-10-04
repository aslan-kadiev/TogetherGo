package at.fhj.togethergo.data;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import at.fhj.togethergo.R;
import at.fhj.togethergo.data.models.PhotoPrompt;

/**
 * Curated photo prompts and the daily prompt. The daily prompt is derived from the date,
 * so the home and moments screens always show the same one.
 */
public final class PhotoPrompts {

    private static final int REWARD = 40;

    private static final List<PhotoPrompt> PROMPTS = build();

    private PhotoPrompts() {
    }

    private static List<PhotoPrompt> build() {
        List<PhotoPrompt> list = new ArrayList<>();
        list.add(new PhotoPrompt("doorstep", "🚪", R.string.prompt_doorstep_title, R.string.prompt_doorstep_desc, REWARD));
        list.add(new PhotoPrompt("green", "🌿", R.string.prompt_green_title, R.string.prompt_green_desc, REWARD));
        list.add(new PhotoPrompt("sky", "☁️", R.string.prompt_sky_title, R.string.prompt_sky_desc, REWARD));
        list.add(new PhotoPrompt("smile", "😊", R.string.prompt_smile_title, R.string.prompt_smile_desc, REWARD));
        list.add(new PhotoPrompt("water", "💧", R.string.prompt_water_title, R.string.prompt_water_desc, REWARD));
        list.add(new PhotoPrompt("color", "🎨", R.string.prompt_color_title, R.string.prompt_color_desc, REWARD));
        list.add(new PhotoPrompt("animal", "🐾", R.string.prompt_animal_title, R.string.prompt_animal_desc, REWARD));
        list.add(new PhotoPrompt("light", "🌤️", R.string.prompt_light_title, R.string.prompt_light_desc, REWARD));
        list.add(new PhotoPrompt("people", "🧑‍🤝‍🧑", R.string.prompt_people_title, R.string.prompt_people_desc, REWARD));
        list.add(new PhotoPrompt("favorite", "❤️", R.string.prompt_favorite_title, R.string.prompt_favorite_desc, REWARD));
        list.add(new PhotoPrompt("new", "✨", R.string.prompt_new_title, R.string.prompt_new_desc, REWARD));
        list.add(new PhotoPrompt("flowers", "🌸", R.string.prompt_flowers_title, R.string.prompt_flowers_desc, REWARD));
        list.add(new PhotoPrompt("tree", "🌳", R.string.prompt_tree_title, R.string.prompt_tree_desc, REWARD));
        list.add(new PhotoPrompt("reflection", "🪞", R.string.prompt_reflection_title, R.string.prompt_reflection_desc, REWARD));
        list.add(new PhotoPrompt("sunset", "🌇", R.string.prompt_sunset_title, R.string.prompt_sunset_desc, REWARD));
        list.add(new PhotoPrompt("mural", "🖼️", R.string.prompt_mural_title, R.string.prompt_mural_desc, REWARD));
        list.add(new PhotoPrompt("rest", "🪑", R.string.prompt_rest_title, R.string.prompt_rest_desc, REWARD));
        list.add(new PhotoPrompt("moon", "🌙", R.string.prompt_moon_title, R.string.prompt_moon_desc, REWARD));
        return Collections.unmodifiableList(list);
    }

    public static List<PhotoPrompt> all() {
        return PROMPTS;
    }

    /** Returns today's prompt, which is the same for all users. */
    public static PhotoPrompt today() {
        return PROMPTS.get(Math.floorMod(seedToday(), PROMPTS.size()));
    }

    /**
     * Returns a few extra prompts for today. They rotate daily and never repeat the main
     * prompt.
     */
    public static List<PhotoPrompt> todaysExtra(int count) {
        int size = PROMPTS.size();
        count = Math.max(0, Math.min(count, size - 1));
        int start = Math.floorMod(seedToday(), size);
        List<PhotoPrompt> result = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            result.add(PROMPTS.get(Math.floorMod(start + i, size)));
        }
        return result;
    }

    /** Date-based seed so the selection stays stable for the whole day. */
    private static int seedToday() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.YEAR) * 366 + c.get(Calendar.DAY_OF_YEAR);
    }

    /** Returns the prompt with the given id, or {@code null} if none matches. */
    public static PhotoPrompt byId(String id) {
        if (id == null) {
            return null;
        }
        for (PhotoPrompt prompt : PROMPTS) {
            if (prompt.id.equals(id)) {
                return prompt;
            }
        }
        return null;
    }
}
