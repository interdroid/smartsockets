package ibis.smartsockets.viz;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.TexturePaint;
import java.awt.image.BufferedImage;
import java.util.ArrayList;

public class UniquePaint {

    private ArrayList <Pattern> patterns = new ArrayList<Pattern>();
    private int index = 0;
    
    private int hueParts = 12;

    public UniquePaint() { 
        generateColors();
        
        System.err.println("Generated " + patterns.size() + " patterns");
    }

    private void generateColors() { 
        generateColors(1.0f, 1.0f);
        generateColors(0.5f, 1.0f);
        generateColors(1.0f, 0.5f);

 //         generateTextures(1.0f, 1.0f, Color.BLACK);
  //      generateTextures(0.5f, 1.0f, Color.BLACK);
       // generateTextures(1.0f, 0.5f, Color.BLACK);
    }
    
    private void generateColors(float sat, float bri) { 

        for (int h=0;h<hueParts;h++) { 

            float hue = h * (1.0f / hueParts);
          
            String id = hue + "," + sat + "," + bri + ","; 
            
            Color c = new Color(Color.HSBtoRGB(hue, sat, bri));
            
            Color f = Color.BLACK;
            
            System.err.println("Adding color: " + id);
            
            Color ia = new Color(Color.HSBtoRGB(hue, sat, 0.25f));
            
            Pattern p = new Pattern("C" + id + "B", c, f, ia);
            
            patterns.add(p);
        }
    }

    private void generateTextures(float sat, float bri, Color alt) { 

        for (int h=0;h<hueParts;h++) { 

            float hue = h * (1.0f / hueParts);
          
            String id = hue + "," + sat + "," + bri + ","; 
            
            Color c = new Color(Color.HSBtoRGB(hue, sat, bri));
            
            Color ia = new Color(Color.HSBtoRGB(hue, sat, 0.25f));

            // Add textured versions!
            BufferedImage b = new BufferedImage(2, 2, BufferedImage.TYPE_3BYTE_BGR);
                    
            Graphics2D g2 = b.createGraphics();
                    
            g2.setColor(c);
            g2.fillRect(0, 0, 2, 2);
                    
            g2.setColor(alt);
            g2.fillRect(0, 0, 1, 1);
            g2.fillRect(1, 1, 1, 1);
           
            Pattern p = new Pattern("T" + id + "B", 
                    new TexturePaint(b, new Rectangle(0, 0, 2, 2)), Color.BLACK, ia);
      
            patterns.add(p);
            
            /*
            
            BufferedImage b = new BufferedImage(3, 3, BufferedImage.TYPE_3BYTE_BGR);
                    
            Graphics2D g2 = b.createGraphics();
                    
            g2.setColor(alt);
            g2.fillRect(0, 0, 3, 3);
                    
            g2.setColor(c);
            g2.fillRect(1, 1, 1, 1);
            
            Pattern p = new Pattern("T" + id + "B", 
                    new TexturePaint(b, new Rectangle(0, 0, 2, 2)), Color.BLACK, ia);
            
            patterns.add(p);
       
            /*
            b = new BufferedImage(2, 2, BufferedImage.TYPE_3BYTE_BGR);
            
            g2 = b.createGraphics();
                    
            g2.setColor(c);
            g2.fillRect(0, 0, 2, 2);
                    
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, 1, 1);
            g2.fillRect(1, 1, 1, 1);
           
            p = new Pattern("T" + id + "B", 
                    new TexturePaint(b, new Rectangle(0, 0, 2, 2)), Color.BLACK, ia);
            
            patterns.add(p);*/
        }
    }

    
    public synchronized Pattern getUniquePaint() {
        
        int tmp = index;
        
        index = (index + 1) % patterns.size(); 
        
        return patterns.get(tmp);
    }
}
