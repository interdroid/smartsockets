package ibis.smartsockets.viz.android;

public class Pattern {

    public final String id;

    public final int fontColor;

    public final int borderColor;

    public final int fillColor;

    public Pattern(String id, int fontColor, int fillColor, int borderColor) {
        this.id = id;
        this.fontColor = fontColor;
        this.fillColor = fillColor;
        this.borderColor = borderColor;
    }
}