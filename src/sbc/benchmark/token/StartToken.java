package sbc.benchmark.token;

import java.io.Serializable;

import org.mozartspaces.capi3.Index;
import org.mozartspaces.capi3.Queryable;

@Queryable
@Index(label="StartToken.class")
public class StartToken implements Serializable {

	private static final long serialVersionUID = 982795638723199036L;

}
