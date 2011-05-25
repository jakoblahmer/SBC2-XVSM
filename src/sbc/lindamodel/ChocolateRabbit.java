package sbc.lindamodel;

import org.mozartspaces.capi3.Index;
import org.mozartspaces.capi3.Queryable;

/**
 * model extension for Linda / Query Coordinator (queryable) 
 * @author ja
 *
 */
@Queryable
public class ChocolateRabbit extends sbc.model.ChocolateRabbit {

	private static final long serialVersionUID = 1L;

	
	@Index(label="isChocoRabbit")
	private boolean isChocoRabbit = true;
	
	public ChocolateRabbit()	{
		super();
	}
	
	public ChocolateRabbit(int producer)	{
		super(producer);
	}

	public void setChocoRabbit(boolean isChocoRabbit) {
		this.isChocoRabbit = true;
	}

	public boolean isChocoRabbit() {
		return isChocoRabbit;
	}
}
