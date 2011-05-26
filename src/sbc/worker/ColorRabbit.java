package sbc.worker;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Random;

import org.apache.commons.collections.iterators.ArrayListIterator;
import org.apache.log4j.Logger;
import org.mozartspaces.capi3.FifoCoordinator;
import org.mozartspaces.capi3.LindaCoordinator;
import org.mozartspaces.capi3.Matchmakers;
import org.mozartspaces.capi3.Property;
import org.mozartspaces.capi3.Query;
import org.mozartspaces.capi3.QueryCoordinator;
import org.mozartspaces.capi3.QueryCoordinator.QuerySelector;
import org.mozartspaces.capi3.Selector;
import org.mozartspaces.core.Capi;
import org.mozartspaces.core.ContainerReference;
import org.mozartspaces.core.DefaultMzsCore;
import org.mozartspaces.core.Entry;
import org.mozartspaces.core.MzsConstants.TransactionTimeout;
import org.mozartspaces.core.MzsCore;
import org.mozartspaces.core.MzsCoreException;
import org.mozartspaces.core.MzsConstants.RequestTimeout;
import org.mozartspaces.core.TransactionReference;

import sbc.lindamodel.Egg;
import sbc.model.Nest;
import sbc.model.Product;
import sbc.worker.exceptions.NoColorGivenException;

/**
 * colors eggs
 *
 */
public class ColorRabbit extends Worker {

	public static void main(String[] args) throws NoColorGivenException	{
		ColorRabbit rab = new ColorRabbit(args);
	}

	private static Logger log = Logger.getLogger(ColorRabbit.class);

	private String color;
	private ContainerReference productsContainer;
	private boolean close;
	private TransactionReference tx;
	private Egg egg;
	
	/**
	 * expected params: id, space URI, color
	 * @param args
	 * @throws NoColorGivenException
	 */
	public ColorRabbit(String[] args) throws NoColorGivenException	{
		super(args);

		if(this.secondArgument == null)	{
			throw new NoColorGivenException("A color has to be given");
		}
		this.color = this.secondArgument;
		
		this.addShutdownHookback();
		
		log.info("################# COLORRABBIT (" + this.color + ")");
		this.readEggs();
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
	 * inits the space container
	 */
	@Override
	protected void initContainer()	{
		super.initContainer();
		
		try {
			productsContainer = capi.lookupContainer("products", space, RequestTimeout.DEFAULT, null);
		} catch (MzsCoreException e) {
			System.out.println("ERROR ESTABLISHING CONNECTION TO CONTAINER");
			e.printStackTrace();
			this.close();
		}
	}
	
	/**
	 * reads eggs from the products container
	 * 	uses LindaCoordinator
	 * 	writes colored eggs to productsContainer
	 */
	private void readEggs() {
		
		log.info("########## (close with Ctrl + C)");
		
		/** CREATE QUERY SELECTOR **/
		Property colors = Property.forName("*", "colors", "*");
		Query query = new Query().filter( 
				Matchmakers.not(colors.equalTo(this.color)) 
		);
		QuerySelector selector = QueryCoordinator.newSelector(query,1);
		
		while(!close)	{
			try {
				log.info("########## AWAITING EGGS");
				tx = capi.createTransaction(TransactionTimeout.INFINITE, space);
				ArrayList<Serializable> obj = capi.take(productsContainer, selector, RequestTimeout.INFINITE, tx); // LindaCoordinator.newSelector(templateEgg, 1)
				for(Serializable s : obj)	{
					log.info("GOT: " + s);
					int sleep = new Random().nextInt(3) + 1;
					Thread.sleep(sleep * 1000);
					
					if(s instanceof Egg)	{
						
						egg = (Egg) s;
						
						// check if query works correctly
						if (egg.getColor().contains(this.color)) {
							log.error("ERROR: Got wrong colored egg!");
							continue;
						}
						egg.addColor(this.color, this.id);
						
						capi.write(productsContainer, 0, tx, new Entry(egg, QueryCoordinator.newCoordinationData()));
						log.info("WRITE: " + s);
						egg = null;
					} else	{
						log.error("GOT OBJECT, which is not an EGG");
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

	@Override
	protected void close() {
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
