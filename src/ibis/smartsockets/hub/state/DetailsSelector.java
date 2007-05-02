package ibis.smartsockets.hub.state;

import java.util.LinkedList;

public class DetailsSelector extends Selector {
    
    private LinkedList<String> result = new LinkedList<String>();
    
    public boolean needAll() {
        return true;
    }
    
    public void select(HubDescription description) {
        
        StringBuffer tmp = new StringBuffer("HubInfo(");

        tmp.append(description.hubAddressAsString);
        tmp.append(",");
        
        String name = description.getName();
        
        if (name == null || name.length() == 0) { 
            name = "<unknown>";
        }
        
        tmp.append(name);
        tmp.append(",");        
        tmp.append(description.getHomeState());        
        tmp.append(",");
        tmp.append(description.numberOfClients());                
        tmp.append(",");    
        
        String [] con = description.connectedTo();
        
        if (con == null) { 
            tmp.append("0");               
        } else { 
            tmp.append(con.length);
            
            for (int i=0;i<con.length;i++) {
                tmp.append(",");
                tmp.append(con[i]);
            }            
        } 
          
        tmp.append(")");
        
        result.add(tmp.toString());
    }
    
    public LinkedList<String> getResult() { 
        return result;
    }   
}
