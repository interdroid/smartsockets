package ibis.smartsockets.viz;

import java.awt.Color;
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
    
    public void setPattern(Color c) { 
        
        this.pattern = new Pattern("AdHoc", c, Color.WHITE, Color.BLACK);
        setColor();
    }
    
    
    public Pattern getPattern() { 
        return pattern;
    }

    private void setColor() { 
        if (pattern == null) {
            return;
        }
      
        setBackPaint(pattern.paint);
        setTextColor(pattern.font);
        setNodeBorderInactiveColor(pattern.inactive);
        setNodeBorderMouseOverColor(Color.WHITE);            
    }            

    
    private void setColor(Color c) { 
        if (c == null) {
            return;
        }
      
        if (c.getRed() > 200 || c.getGreen() > 200 || c.getBlue() > 200) { 
            setTextColor(Color.BLACK);
        } else { 
            setTextColor(Color.WHITE);
        }

        Color inactive = null;

        if (c.getRed() < 64 && c.getGreen() < 64 && c.getBlue() < 64) {

            if (c.getRed() < 32 && c.getGreen() < 32 && c.getBlue() < 32) {
                // Too dark for the regular 'brighter'!
                inactive = new Color(c.getRed() + 48, c.getGreen() + 48, 
                        c.getBlue() + 48);                             
            } else {                                     
                inactive = c.brighter();
            }
        } else { 
            inactive = c.darker();                
        }

        setNodeBorderInactiveColor(inactive);
        setNodeBorderMouseOverColor(Color.WHITE);            
    }            
}
