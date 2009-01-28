package ibis.smartsockets.hub;

import java.io.PrintStream;

public abstract class Statistics {

	protected final String name;
	protected final long startTime;
	protected long endTime;
	
	public Statistics(String name) { 
		this.name = name;
		this.startTime = System.currentTimeMillis();
	}
	
	public void setEndTime() { 
		endTime = System.currentTimeMillis();	
	}
	
	public abstract void add(Statistics other);	
	public abstract void print(PrintStream out, String prefix); 
}
