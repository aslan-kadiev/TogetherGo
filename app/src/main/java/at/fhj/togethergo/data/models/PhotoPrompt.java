package at.fhj.togethergo.data.models;

/**
 * A gentle daily prompt (behavioural activation) that invites the user to go outside and
 * capture a moment. Texts are string resources so they can be translated.
 */
public class PhotoPrompt {
    public final String id;
    public final String emoji;
    public final int titleRes;
    public final int descRes;
    public final int points;

    public PhotoPrompt(String id, String emoji, int titleRes, int descRes, int points) {
        this.id = id;
        this.emoji = emoji;
        this.titleRes = titleRes;
        this.descRes = descRes;
        this.points = points;
    }
}
