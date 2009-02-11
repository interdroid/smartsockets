package ibis.smartsockets.viz.android;

import java.util.LinkedList;

import android.graphics.Color;

public class UniqueColor {

    LinkedList<Integer> colors = new LinkedList<Integer>();

    private int hueParts = 6;

    public UniqueColor() {
        generateColors();
    }

    private void generateColors() {
        generateColors(1.0f, 1.0f);
        generateColors(0.5f, 1.0f);
        generateColors(1.0f, 0.5f);
        // generateColors(0.5f, 0.5f);
    }

    private void generateColors(float sat, float bri) {

        for (int h = 0; h < hueParts; h++) {

            float hue = h * (1.0f / hueParts);

            colors.add((Color.HSVToColor(new float[] { hue, sat, bri })));
        }
    }

    public synchronized int getUniqueColor() {

        int c = colors.removeFirst();

        return c;
    }
}