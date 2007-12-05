package ibis.smartsockets.viz;

import java.awt.Color;
import java.util.LinkedList;

public class UniqueColor {
    
    LinkedList <Color> colors = new LinkedList<Color>();
    
    private int hueParts = 6;
    
    public UniqueColor() { 
        generateColors();
    }
    
    private void generateColors() { 
        generateColors(1.0f, 1.0f);
        generateColors(0.5f, 1.0f);
        generateColors(1.0f, 0.5f);
    //    generateColors(0.5f, 0.5f);
    }
        
    private void generateColors(float sat, float bri) { 
        
        for (int h=0;h<hueParts;h++) { 
                    
            float hue = h * (1.0f / hueParts);
            
            System.err.println("Adding color: " + hue + "-" + sat + "-" + bri);
            
            colors.add(new Color(Color.HSBtoRGB(hue, sat, bri)));   
        }
        
        
        
/*                
            
            
            
        
        double [] oldColor = hue;
        double [] oldSat = saturation;
        
        hue = new double[(oldColor.length-1)*2 + 1];
        saturation = new double[(oldSat.length-1)*2 + 1];
        
        for (int i=0;i<oldColor.length-1;i++) { 
            hue[2*i] = oldColor[i];
            saturation[2*i] = oldSat[i] / 2.0;    
            
            hue[2*i+1] = oldColor[i] + ((oldColor[i+1] - oldColor[i]) / 2);   
            saturation[2*i+1] = 0.9;    
            
            index = 0;
            indexIncrement = 1;
        }
        
        System.err.println("Colors    : " + Arrays.toString(hue));
        System.err.println("Saturation: " + Arrays.toString(saturation));
        */
    }
    
    public synchronized Color getUniqueColor() {    
        
        Color c = colors.removeFirst();
        
        System.err.println("Color = " + c);
        
        return c;
    }
}