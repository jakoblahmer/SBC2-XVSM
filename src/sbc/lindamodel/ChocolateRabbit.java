package sbc.lindamodel;

import org.mozartspaces.capi3.Index;
import org.mozartspaces.capi3.Queryable;

/**
 * model extension for Linda / Query Coordinator (queryable) 
 * @author ja
 *
 */
@Queryable
@Index(label="ChocolateRabbit.class")
public class ChocolateRabbit extends sbc.model.ChocolateRabbit {

	private static final long serialVersionUID = 1L;

	public ChocolateRabbit()	{
		super();
	}
	
	public ChocolateRabbit(int producer)	{
		super(producer);
	}
}
