package sbc.lindamodel;

import org.mozartspaces.capi3.Index;
import org.mozartspaces.capi3.Queryable;

/**
 * model extension for Linda / Query Coordinator
 * @author ja
 *
 */
@Queryable
@Index(label="Nest.class")
public class Nest extends sbc.model.Nest {

	private static final long serialVersionUID = 2560158684039859496L;

	@Index(label="shipped")
	protected boolean linda_shipped;

	@Index(label="tested")
	protected boolean linda_tested;
	
	public Nest()	{
		super();
	}
	
	public Nest(boolean t, boolean shipped)	{
		super();
		this.setShipped(shipped);
		this.setTested(t);
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
	public void setTested(boolean t) {
		super.setTested(t);
		this.linda_tested = t;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + (linda_shipped ? 1231 : 1237);
		result = prime * result + (linda_tested ? 1231 : 1237);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		Nest other = (Nest) obj;
		if (linda_shipped != other.linda_shipped)
			return false;
		if (linda_tested != other.linda_tested)
			return false;
		return true;
	}
	
}
