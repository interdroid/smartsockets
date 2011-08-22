package ibis.smartsockets.viz;

import java.awt.Color;
import java.awt.Paint;

public class Pattern {

    public final String id;

    public final Color font;
    public final Color inactive;
    public final Paint paint;

    public Pattern(String id, Paint paint, Color font, Color inactive) {
        this.id = id;
        this.paint = paint;
        this.font = font;
        this.inactive = inactive;
    }
}