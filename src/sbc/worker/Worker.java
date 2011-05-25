package sbc.worker;

import java.net.URI;

import org.mozartspaces.core.Capi;
import org.mozartspaces.core.ContainerReference;
import org.mozartspaces.core.DefaultMzsCore;
import org.mozartspaces.core.MzsCore;
import org.mozartspaces.core.MzsCoreException;
import org.mozartspaces.core.MzsConstants.RequestTimeout;

/**
 * abstract class for worker
 */
public abstract class Worker {

	protected int id;
	

	protected String secondArgument;
	
	protected static String producerName = "sbc.server.queue";
	
	protected URI space;

	protected Capi capi;

	protected ContainerReference productsContainer;


	protected DefaultMzsCore core;
	
	/**
	 * reads arguments
	 * @param args
	 */
	public Worker(String[] args)	{
		this.parseArgs(args);
		this.initContainer();
	}
	
	/**
	 * reads args
	 * @param args
	 */
	private void parseArgs(String[] args) {
		if(args.length < 2)	{
			throw new IllegalArgumentException("at least an ID and XVSM URI has to be given!");
		}
		try	{
			this.id = Integer.parseInt(args[0]);
		} catch (Exception e)	{
			throw new IllegalArgumentException("ID has to be an integer!");
		}
		
		try	{
			this.space = URI.create(args[1]);
		} catch (Exception e)	{
			throw new IllegalArgumentException("URI could not be parsed");
		}
		
		if(args.length > 2)	{
			try	{
				this.secondArgument = args[2];
			} catch (Exception e)	{
				throw new IllegalArgumentException("second argument could not be parsed");
			}
		}
	}
	
	/**
	 * inits the XVSM space (config file has to be specified via System property)
	 */
	protected void initContainer() {
		
		try	{
	        // Create an embedded space and construct a Capi instance for it
	        core = DefaultMzsCore.newInstance();
	        capi = new Capi(core);
		} catch(Exception e)	{
			System.out.println("ERROR: " + e.getCause());
		}
        if(capi == null)	{
        	System.out.println("ERROR: CAPI is null");
        }
	}

	protected abstract void close();
}
