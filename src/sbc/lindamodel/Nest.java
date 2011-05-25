package sbc.lindamodel;

import org.mozartspaces.capi3.Index;
import org.mozartspaces.capi3.Queryable;

/**
 * model extension for Linda / Query Coordinator
 * @author ja
 *
 */
@Queryable
public class Nest extends sbc.model.Nest {

	private static final long serialVersionUID = 2560158684039859496L;


	public Nest()	{
		super();
	}
	
	public Nest(boolean shipped)	{
		super();
		this.setShipped(shipped);
	}
	
	public Nest(int producer)	{
		super(producer);
	}
	
	
	@Index(label="shipped")
	private boolean linda_shipped;
	
	@Override
	public boolean isShipped() {
		return linda_shipped;
	}

	@Override
	public void setShipped(boolean shipped) {
		super.setShipped(shipped);
		this.linda_shipped = shipped;
	}
	
}
