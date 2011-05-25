package sbc.lindamodel;

import java.util.List;

import org.mozartspaces.capi3.Index;
import org.mozartspaces.capi3.Queryable;

/**
 * model extension for Linda / Query Coordinator
 * @author ja
 *
 */
@Queryable
public class Egg extends sbc.model.Egg {

	private static final long serialVersionUID = 6253223622921211723L;
	
	@Index(label="colored")
	protected boolean linda_colored;
	
	@Index(label="colors")
	protected List<String> linda_color = null;
	
	public Egg(boolean colored)	{
		this.setColored(colored);
	}
	
	public Egg(int producer) {
		super(producer);
	}
	
	public Egg(int producer, int colorCount) {
		super(producer, colorCount);
	}


	@Override
	public boolean isColored()	{
		return linda_colored;
	}
	
	@Override
	public void setColored(boolean color)	{
		super.setColored(color);
		this.linda_colored = color;
	}
	
	@Override
	public void addColor(String color)	{
		super.addColor(color);
		this.linda_colored = super.isColored();
		this.linda_color.add(color);
	}
}
