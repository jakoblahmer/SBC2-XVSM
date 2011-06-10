package sbc.model.lindamodel;

import java.io.Serializable;

import org.mozartspaces.capi3.Index;
import org.mozartspaces.capi3.Queryable;

@Queryable
@Index(label="WorkerCount.class")
public class ObjectCount implements Serializable {

	private static final long serialVersionUID = -5909752222883953631L;
	
	@Index(label="name")
	private String name;
	
	@Index(label="changed")
	private boolean changed;
	
	private int count;
	
	public ObjectCount(String name)	{
		this.name = name;
		this.changed = false;
		this.count = 0;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int getCount() {
		return count;
	}
	
	public int getCountAndRemoveChanged() {
		this.changed = false;
		return count;
	}

	public void setCount(int count) {
		this.count = count;
	}

	public void increaseCount()	{
		this.count++;
		this.changed = true;
	}
	
	public void decreaseCount()	{
		this.count--;
		if(count < 0)
			count = 0;
		this.changed = true;
	}
	
	public boolean isChanged() {
		return changed;
	}

	public void setChanged(boolean changed) {
		this.changed = changed;
	}
}
