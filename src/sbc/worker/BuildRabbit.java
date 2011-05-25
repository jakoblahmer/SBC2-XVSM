package sbc.worker;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Random;

import org.apache.log4j.Logger;
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

import sbc.lindamodel.ChocolateRabbit;
import sbc.lindamodel.Egg;
import sbc.lindamodel.Nest;
import sbc.worker.exceptions.BuildNestException;

/**
 * builds nests from given products (2 eggs + 1 choco bunny)
 * @author ja
 *
 */
public class BuildRabbit extends Worker {

	public static void main(String[] args) throws BuildNestException	{
		BuildRabbit rab = new BuildRabbit(args);
	}

	private static Logger log = Logger.getLogger(BuildRabbit.class);

	// different selector queries (what is needed to finalize nest)
	private Query querySelectorAll;
	private Query querySelectorEgg;
	private Query querySelectorChoco;
	
	private ContainerReference nestsContainer;
	private TransactionReference tx;
	
	private Nest currentNest;
	private int chocoCount;
	private int eggCount;
	private boolean close;


	/**
	 * expected parameter: id, space URI
	 * @param args
	 * @throws BuildNestException
	 */
	public BuildRabbit(String[] args) throws BuildNestException	{
		super(args);
		currentNest = null;
		eggCount = chocoCount = 0;
		
		this.addShutdownHookback();
		
		this.initQueries();
		
		this.readIngredients();
	}

	/**
	 * shutdown hook
	 */
	private void addShutdownHookback() {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
            	log.info("SHUTDOWN...");
            	close();
            }
        });
	}
	
	/**
	 * inits the space containers (uses the products and the nest container)
	 */
	@Override
	protected void initContainer()	{
		super.initContainer();
		
		try {
			productsContainer = capi.lookupContainer("products", space, RequestTimeout.DEFAULT, null);
			nestsContainer = capi.lookupContainer("nests", space, RequestTimeout.DEFAULT, null);
		} catch (MzsCoreException e) {
			System.out.println("ERROR ESTABLISHING CONNECTION TO CONTAINER");
			e.printStackTrace();
			System.exit(0);
		}
	}
	
	/**
	 * creates the queries for the different selection "modes"
	 * 	- ALL: select chocoRabbit OR egg
	 * 	- EGG: select egg
	 * 	- rabbit: select chocoRabbit
	 */
	private void initQueries() {
		// get eggs
		Property eggProperty = Property.forName("*", "colored");
		// get choco rabbits
		Property chocoProperty = Property.forName("*", "isChocoRabbit");
		
		
		querySelectorAll = new Query().filter(Matchmakers.or(eggProperty.equalTo(true), chocoProperty.equalTo(true)));
		querySelectorAll.cnt(1);
		
		querySelectorEgg = new Query().filter(eggProperty.equalTo(true));
		querySelectorEgg.cnt(1);
		
		querySelectorChoco = new Query().filter(chocoProperty.equalTo(true));
		querySelectorChoco.cnt(1);
	}
	
	/**
	 * reads the missing ingredients from the product container
	 * 	uses QueryCoordinator to find matching products
	 * 	writes nest (not shipped) to nestscontainer
	 * @throws BuildNestException
	 */
	private void readIngredients() throws BuildNestException {
		
		log.info("########## AWAITING INGREDIENTS (close with Ctrl + C)");
		
		while(!close)	{
			try {
				// specify which query should be used
				if(currentNest == null)	{
					currentNest = new Nest(this.id);
					// id is set via prewrite aspect
					chocoCount = eggCount = 0;
					tx = capi.createTransaction(TransactionTimeout.INFINITE, space);
				}
				
				ArrayList<Serializable> obj;
				
				// select matching query
				if(eggCount < 2 && chocoCount == 0)	{
					log.info("REQEUST EVERYTHING (egg + bunny)");
					obj = capi.take(productsContainer, QueryCoordinator.newSelector(querySelectorAll), RequestTimeout.INFINITE, tx);
				} else if(eggCount >= 2 && chocoCount == 0)	{
					log.info("REQEUST BUNNY");
					obj = capi.take(productsContainer, QueryCoordinator.newSelector(querySelectorChoco), RequestTimeout.INFINITE, tx);
				} else	{
					log.info("REQUEST EGG");
					obj = capi.take(productsContainer, QueryCoordinator.newSelector(querySelectorEgg), RequestTimeout.INFINITE, tx);
				}
				
				if(obj.size() > 1)	{
					currentNest = null;
					capi.rollbackTransaction(tx);
					throw new BuildNestException("more than 1 entry was read from the space");
				}
				
				Serializable s = obj.get(0);
				
				int sleep = new Random().nextInt(3) + 1;
				Thread.sleep(sleep * 1000);
				
				if(s instanceof Egg)	{
					log.info("###### received 1 EGG");

					if(eggCount < 2)	{

						log.info("###### add 1 EGG to nest");

						currentNest.addEgg((Egg) s);
						eggCount++;
					}
				} else if(s instanceof ChocolateRabbit)	{

					log.info("###### received 1 ChocoRabbit");

					if(chocoCount == 0)	{

						log.info("###### add 1 ChocoRabbit to nest");

						currentNest.setRabbit((ChocolateRabbit) s);
						chocoCount++;
					}
				} else	{
					// ERROR
					log.error("ERROR: NO EGG or RABBIT GIVEN");
					capi.rollbackTransaction(tx);
					currentNest = null;
					return;
				}
				
				log.info("NEST STATUS: " + currentNest);

				// send if nest is completed
				if(currentNest.isComplete())	{
					// send nest
					log.info("###### NEST is complete, send it");
					log.info("#######################################");
					capi.write(nestsContainer, 0, tx, new Entry(currentNest, LindaCoordinator.newCoordinationData()));
					capi.commitTransaction(tx);
					currentNest = null;
					log.info("###### waiting for ingrediants...");
					log.info("#######################################");
				}
			} catch (MzsCoreException e) {
				try {
					capi.rollbackTransaction(tx);
					currentNest = null;
				} catch (MzsCoreException e1) {
				}
				close = true;
			} catch (InterruptedException e) {
				try {
					capi.rollbackTransaction(tx);
					currentNest = null;
				} catch (MzsCoreException e1) {
				}
				close = true;
			}
		}
		this.close();
	}

	/**
	 * shutdown
	 */
	@Override
	protected void close()	{
    	log.info("SHUTTING DOWN....");
    	try {
    		// rollback transaction if active (can throw exception)
    		capi.rollbackTransaction(tx);
    		currentNest = null;
		} catch (Exception e) {
		}
		close = true;
		core.shutdown(true);
	}

}
