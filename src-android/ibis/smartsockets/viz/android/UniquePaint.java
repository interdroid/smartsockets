package ibis.smartsockets.viz.android;

import java.util.ArrayList;

import android.graphics.Color;
import android.graphics.Paint;

public class UniquePaint {

    private ArrayList<Pattern> patterns = new ArrayList<Pattern>();

    private int index = 0;

    private int hueParts = 12;

    public UniquePaint() {
        generateColors();

        // System.err.println("Generated " + patterns.size() + " patterns");
    }

    private void generateColors() {
        generateColors(1.0f, 1.0f);
        generateColors(0.5f, 1.0f);
        generateColors(1.0f, 0.5f);

        // generateTextures(1.0f, 1.0f, Color.BLACK);
        // generateTextures(0.5f, 1.0f, Color.BLACK);
        // generateTextures(1.0f, 0.5f, Color.BLACK);
    }

    private void generateColors(float sat, float bri) {

        for (int h = 0; h < hueParts; h++) {

            float hue = h * (360.0f / hueParts);

            String id = hue + "," + sat + "," + bri + ",";

            int borderColor = Color.HSVToColor(new float[] { hue, sat, bri });

            int fillColor = Color.HSVToColor(new float[] { hue, sat, 0.50f });

            Paint paint = new Paint();
            paint.setColor(fillColor);

            Pattern p = new Pattern("C" + id + "B", Color.WHITE, fillColor,
                    borderColor);

            patterns.add(p);
        }
    }

    // private void generateTextures(float sat, float bri, int alt) {
    //
    // for (int h = 0; h < hueParts; h++) {
    //
    // float hue = h * (1.0f / hueParts);
    //
    // String id = hue + "," + sat + "," + bri + ",";
    //
    // int c = Color.HSVToColor(new float[] { hue, sat, bri });
    //
    // int iaColor = Color.HSVToColor(new float[] { hue, sat, 0.25f });
    //
    // // Add textured versions!
    // Bitmap b = Bitmap.createBitmap(2, 2, Bitmap.Config.ALPHA_8);
    //
    // Canvas canvas = new Canvas();
    // canvas.setBitmap(b);
    // // Graphics2D g2 = b.createGraphics();
    //
    // Paint paint = new Paint();
    // paint.setColor(c);
    // // g2.setColor(c);
    // canvas.drawRect(new Rect(0, 0, 2, 2), paint);
    // // g2.fillRect(0, 0, 2, 2);
    // paint.setColor(alt);
    // // g2.setColor(alt);
    // canvas.drawRect(new Rect(0, 0, 1, 1), paint);
    // canvas.drawRect(new Rect(1, 1, 1, 1), paint);
    //
    // // Pattern p = new Pattern("T" + id + "B", new TexturePaint(b,
    // // new Rectangle(0, 0, 2, 2)), Color.BLACK, iaColor);
    //
    // Pattern p = new Pattern("T" + id + "B", paint, Color.BLACK, iaColor);
    //
    // patterns.add(p);
    //
    // /*
    // *
    // * BufferedImage b = new BufferedImage(3, 3,
    // * BufferedImage.TYPE_3BYTE_BGR);
    // *
    // * Graphics2D g2 = b.createGraphics();
    // *
    // * g2.setColor(alt); g2.fillRect(0, 0, 3, 3);
    // *
    // * g2.setColor(c); g2.fillRect(1, 1, 1, 1);
    // *
    // * Pattern p = new Pattern("T" + id + "B", new TexturePaint(b, new
    // * Rectangle(0, 0, 2, 2)), Color.BLACK, ia);
    // *
    // * patterns.add(p); /* b = new BufferedImage(2, 2,
    // * BufferedImage.TYPE_3BYTE_BGR);
    // *
    // * g2 = b.createGraphics();
    // *
    // * g2.setColor(c); g2.fillRect(0, 0, 2, 2);
    // *
    // * g2.setColor(Color.WHITE); g2.fillRect(0, 0, 1, 1); g2.fillRect(1,
    // * 1, 1, 1);
    // *
    // * p = new Pattern("T" + id + "B", new TexturePaint(b, new
    // * Rectangle(0, 0, 2, 2)), Color.BLACK, ia);
    // *
    // * patterns.add(p);
    // */
    // }
    // }

    public synchronized Pattern getUniquePaint() {

        int tmp = index;

        index = (index + 1) % patterns.size();

        return patterns.get(tmp);
    }
}
