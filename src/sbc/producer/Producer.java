package sbc.producer;

import java.net.URI;
import java.util.Random;

import org.mozartspaces.core.Capi;
import org.mozartspaces.core.ContainerReference;
import org.mozartspaces.core.DefaultMzsCore;
import org.mozartspaces.core.MzsCoreException;
import org.mozartspaces.core.MzsConstants.RequestTimeout;

/**
 * abstract class for producers
 * @author ja
 */
public abstract class Producer extends Thread {

	protected int id;
	protected int productCount;
	protected double failureRate;
	protected URI space;
	protected ContainerReference container;
	protected Capi capi;
	private DefaultMzsCore core;

	protected boolean close = false;
	
	/**
	 * constructor
	 * @param args
	 */
	public Producer(String[] args)	{
		this.parseArgs(args);
		this.init();
	}

	public void setArgs(String[] args)	{
		this.parseArgs(args);
	}

	/**
	 * parses the args (id, amount, URI)
	 * @param args
	 */
	private void parseArgs(String[] args) {
		if(args.length < 2)	{
			throw new IllegalArgumentException("expected parameters: 'id' 'numberOfElementsToProduce' 'failure rate' 'XVSM space URI'!");
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
		
		try	{
			this.productCount = Integer.parseInt(args[2]);
		} catch (Exception e)	{
			throw new IllegalArgumentException("amount has to be an integer");
		}
		
		try	{
			this.failureRate = Double.parseDouble(args[3]);
		} catch (Exception e)	{
			throw new IllegalArgumentException("failure rate has to be a float and must be 0 <= failure rate <= 1");
		}
	}


	
	protected boolean calculateDefect()	{
		return (this.failureRate >= new Random().nextDouble());
	}
	
	/**
	 * inits the XVSM space
	 */
	protected void init() {
		
        // Create an embedded space and construct a Capi instance for it
        core = DefaultMzsCore.newInstance();
        capi = new Capi(core);

        // Ensure that the container "products" exists
        try {
        	container = capi.lookupContainer("products", space, RequestTimeout.DEFAULT, null);
		} catch (MzsCoreException e) {
			System.out.println("ERROR ESTABLISHING CONNECTION TO CONTAINER");
			e.printStackTrace();
			this.close();
		}
	}

	public void stopBenchmark()	{
		this.close = true;
	}
	
	/**
	 * shutdown the producer
	 */
	protected void close() {
		core.shutdown(true);
	}
}
