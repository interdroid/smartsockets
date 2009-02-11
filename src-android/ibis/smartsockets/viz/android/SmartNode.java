package ibis.smartsockets.viz.android;

import android.graphics.Color;

import com.touchgraph.graphlayout.Node;

public class SmartNode extends Node {

    private Pattern pattern;

    public SmartNode(String id) {
        super(id);
    }

    public SmartNode(String id, String text) {
        super(id, text);
    }

    public void setPattern(Pattern p) {
        this.pattern = p;
        setColor();
    }

    public void setPattern(int c) {
        this.pattern = new Pattern("AdHoc", Color.WHITE, c, c);
        setColor();
    }

    public Pattern getPattern() {
        return pattern;
    }

    private void setColor() {
        if (pattern == null) {
            return;
        }

        setBackColor(pattern.fillColor);
        setTextColor(pattern.fontColor);
        setNodeBorderColor(pattern.borderColor);
    }

}
