package sbc.worker;

import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;

import org.mozartspaces.capi3.AnyCoordinator;
import org.mozartspaces.capi3.Property;
import org.mozartspaces.capi3.Query;
import org.mozartspaces.capi3.QueryCoordinator;
import org.mozartspaces.core.Capi;
import org.mozartspaces.core.ContainerReference;
import org.mozartspaces.core.DefaultMzsCore;
import org.mozartspaces.core.Entry;
import org.mozartspaces.core.MzsCore;
import org.mozartspaces.core.MzsCoreException;
import org.mozartspaces.core.TransactionReference;
import org.mozartspaces.core.MzsConstants.RequestTimeout;
import org.mozartspaces.core.MzsConstants.TransactionTimeout;

import sbc.model.lindamodel.ObjectCount;

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

	protected TransactionReference tx;

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

	
	
	/**
	 * retrieves the current amount of worker rabbits and increases the amount
	 */
	protected void increaseWorkerCount(String workerName) {
		this.processWorkerCount(workerName, true);
	}
	
	/**
	 * retrieves the current amount of worker rabbits and decreases the amount
	 */
	protected void decreseWorkerCount(String workerName)	{
		this.processWorkerCount(workerName, false);
	}
	
	/**
	 * processes the worker amount
	 * @param workerName
	 * @param increase
	 */
	private void processWorkerCount(String workerName, boolean increase)	{
		try {
			ContainerReference loadbalancingRef = capi.lookupContainer("systemInfo", space, RequestTimeout.DEFAULT, null);
			Property countProperty = Property.forName("WorkerCount.class", "name");
			
			Query query = new Query().filter(countProperty.equalTo(workerName));
			query.cnt(1);
			
			tx = capi.createTransaction(TransactionTimeout.INFINITE, space);
			
			ArrayList<Serializable> entryarray = capi.take(loadbalancingRef, QueryCoordinator.newSelector(query), RequestTimeout.INFINITE, tx);
			
			Serializable elem = entryarray.get(0);
			if(elem instanceof ObjectCount)	{
				ObjectCount model = (ObjectCount) elem;
				if(increase)
					model.increaseCount();
				else
					model.decreaseCount();
				capi.write(loadbalancingRef, 0, tx, new Entry(model, QueryCoordinator.newCoordinationData()));
				capi.commitTransaction(tx);
			} else	{
				capi.rollbackTransaction(tx);
			}
			
		} catch (MzsCoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	protected abstract void close();
}
