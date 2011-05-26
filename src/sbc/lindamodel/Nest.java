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

	@Index(label="shipped")
	private boolean linda_shipped;

	@Index(label="tested")
	private boolean linda_tested;
	
	public Nest()	{
		super();
	}
	
	public Nest(boolean tested, boolean shipped)	{
		super();
		this.setShipped(shipped);
		this.setTested(tested);
	}
	
	public Nest(int producer)	{
		super(producer);
	}
	
	@Override
	public boolean isShipped() {
		return linda_shipped;
	}

	@Override
	public void setShipped(boolean shipped) {
		super.setShipped(shipped);
		this.linda_shipped = shipped;
	}
	
	@Override
	public boolean isTested() {
		return linda_tested;
	}
	
	@Override
	public void setTested(boolean tested) {
		super.setTested(tested);
		this.linda_tested = tested;
	}
	
}
