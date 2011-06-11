package sbc.benchmark.token;

import java.io.Serializable;

import org.mozartspaces.capi3.Index;
import org.mozartspaces.capi3.Queryable;

@Queryable
@Index(label="ResultEntry.class")
public class ResultEntry implements Serializable {

	private static final long serialVersionUID = 982795638723199036L;

	private int completedNests;
	private int errorNests;
	
	
	public ResultEntry(int completedNests, int errorNests) {
		super();
		this.completedNests = completedNests;
		this.errorNests = errorNests;
	}
	
	
	public int getCompletedNests() {
		return completedNests;
	}
	public void setCompletedNests(int completedNests) {
		this.completedNests = completedNests;
	}
	public int getErrorNests() {
		return errorNests;
	}
	public void setErrorNests(int errorNests) {
		this.errorNests = errorNests;
	}
	
	
	
}
