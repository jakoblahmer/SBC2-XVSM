package sbc.worker;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Random;

import org.apache.log4j.Logger;
import org.mozartspaces.capi3.AnyCoordinator;
import org.mozartspaces.capi3.LindaCoordinator;
import org.mozartspaces.capi3.Matchmakers;
import org.mozartspaces.capi3.Property;
import org.mozartspaces.capi3.Query;
import org.mozartspaces.capi3.QueryCoordinator;
import org.mozartspaces.core.ContainerReference;
import org.mozartspaces.core.Entry;
import org.mozartspaces.core.MzsCoreException;
import org.mozartspaces.core.MzsConstants.RequestTimeout;
import org.mozartspaces.core.MzsConstants.TransactionTimeout;
import org.mozartspaces.core.TransactionReference;

import sbc.model.lindamodel.Egg;
import sbc.model.lindamodel.Nest;
import sbc.worker.exceptions.NoColorGivenException;

/**
 * represents a logistic rabbit (ships nests)
 * @author ja
 *
 */
public class LogisticRabbit extends Worker {

	public static void main(String[] args) throws NoColorGivenException	{
		LogisticRabbit rab = new LogisticRabbit(args);
	}

	private static Logger log = Logger.getLogger(LogisticRabbit.class);

	private ContainerReference nestsContainer;
	private ContainerReference nestsCompletedContainer;
	private ContainerReference nestsErrorContainer;
	private boolean close;
	private Nest nest;



	/**
	 * expected params: id, space URI
	 * @param args
	 */
	public LogisticRabbit(String[] args)	{
		super(args);

		this.addShutdownHookback();
		
		this.readNests();
	}

	/**
	 * adds a shutdown hook (called whan worker quit)
	 */
	private void addShutdownHookback() {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
            	log.info("SHUTDDOWN...");
            	close();
            }
        });
	}
	
	/**
	 * inits the space container (lookup)
	 */
	@Override
	protected void initContainer()	{
		super.initContainer();
		
		try {
			nestsContainer = capi.lookupContainer("nests", space, RequestTimeout.DEFAULT, null);
			nestsCompletedContainer = capi.lookupContainer("nestsCompleted", space, RequestTimeout.DEFAULT, null);
			nestsErrorContainer = capi.lookupContainer("nestsError", space, RequestTimeout.DEFAULT, null);
		} catch (MzsCoreException e) {
			System.out.println("ERROR ESTABLISHING CONNECTION TO CONTAINER");
			e.printStackTrace();
			this.close();
		}
		
		this.increaseWorkerCount("logisticRabbit");
	}
	
	/**
	 * reads nests from the nestcontainer
	 * 	uses LindaCoordinator to find not shipped nests
	 * 	writes shipped nests to nestcontainer
	 */
	private void readNests() {
		
		log.info("########## AWAITING NESTS (close with Ctrl + C)");
		
		// check tested, but not shipped nest
		/** CREATE QUERY SELECTOR **/
		Property testedProp = Property.forName("Nest.class", "tested");
		Property shippedProp = Property.forName("Nest.class", "shipped");
		Query query = new Query().filter(
				Matchmakers.and(testedProp.equalTo(true), shippedProp.equalTo(false)) 
		);
		query.cnt(1);
		
		while(!close)	{
			try {
				tx = capi.createTransaction(TransactionTimeout.INFINITE, space);
				ArrayList<Serializable> obj = capi.take(nestsContainer, QueryCoordinator.newSelector(query, 1), RequestTimeout.INFINITE, tx);
				for(Serializable s : obj)	{
					if(s instanceof Nest)	{
						nest = (Nest) s;
						log.info("GOT: Nest [id=" + nest.getId() + "]");
						int sleep = new Random().nextInt(3) + 1;
						Thread.sleep(sleep * 1000);
						
						nest.setShipped(true);
						nest.setShipper_id(this.id);
						
						// method name says everything
						if(nest.isErrorFreeAndIsComplete())	{
							// nest error free and completed => write to competed nest container
							capi.write(nestsCompletedContainer, 0, tx, new Entry(nest, AnyCoordinator.newCoordinationData()));
						} else	{
							// nest has error (or is not completed) => write to error container
							capi.write(nestsErrorContainer, 0, tx, new Entry(nest, AnyCoordinator.newCoordinationData()));
						}
						log.info("WRITE: Nest [id=" + nest.getId() + "]");
						nest = null;
					} else	{
						log.error("GOT OBJECT, which is not a NEST");
						capi.rollbackTransaction(tx);
					}
				}
				capi.commitTransaction(tx);
			} catch (MzsCoreException e) {
				// close, when space is terminated
				try {
					capi.rollbackTransaction(tx);
				} catch (MzsCoreException e1) {
				}
				close = true;
			} catch (InterruptedException e) {
				try {
					capi.rollbackTransaction(tx);
				} catch (MzsCoreException e1) {
				}
				close = true;
			}
			
		}
		this.close();
	}
	
	/**
	 * closes the worker (shuts down the xvsm core)
	 */
	@Override
	protected void close()	{
    	log.info("SHUTTING DOWN....");
    	try {
    		// rollback transaction if active (can throw exception)
    		capi.rollbackTransaction(tx);
		} catch (Exception e) {
		}
		close = true;
		core.shutdown(true);
	}
}
