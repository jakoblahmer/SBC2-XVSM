package sbc.benchmark.token;

import java.io.Serializable;

import org.mozartspaces.capi3.Index;
import org.mozartspaces.capi3.Queryable;

@Queryable
@Index(label="StopToken.class")
public class StopToken implements Serializable {

	private static final long serialVersionUID = -5388307283183545740L;

}
