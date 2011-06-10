package sbc.producer;

import java.util.Random;

import org.apache.log4j.Logger;
import org.mozartspaces.capi3.QueryCoordinator;
import org.mozartspaces.core.Entry;
import org.mozartspaces.core.MzsConstants.RequestTimeout;
import org.mozartspaces.core.MzsConstants.TransactionTimeout;
import org.mozartspaces.core.MzsCoreException;
import org.mozartspaces.core.TransactionReference;

import sbc.model.lindamodel.Egg;

/**
 * creates eggs and places them in the "products" container
 *
 */
public class Chicken extends Producer {

	private static Logger log = Logger.getLogger(Chicken.class);

	private Egg egg;

	private TransactionReference tx;

	/**
	 * main class (NOT NEEDED - should be started via admin gui)
	 * @param args
	 */
	public static void main(String[] args) {
		Chicken chicken = new Chicken(args);
		chicken.start();
	}

	/**
	 * needed arguments: id number of eggs space URI
	 * @param args
	 */
	public Chicken(String[] args)	{
		// read params etc
		super(args);
	}

	
	/**
	 * override, because chicken puts eggs in "eggsToColorContainer", not in "products" container
	 */
	@Override
	protected void init() {
		super.init();
        // Ensure that the container "products" exists
        try {
        	container = capi.lookupContainer("eggsToColor", space, RequestTimeout.DEFAULT, null);
		} catch (MzsCoreException e) {
			System.out.println("ERROR ESTABLISHING CONNECTION TO CONTAINER");
			e.printStackTrace();
			this.close();
		}
	}
	
	/**
	 * produces the eggs and places them in the "products" container
	 */
	@Override
	public void run() {
		log.info("started");
		// endless loop
		if(productCount == -1)	{
			log.info("started");
			while(!close)	{
				egg = new Egg(this.id, getRandomColorCount());
				egg.setError(this.calculateDefect());
				
				try {
					capi.write(container, 0, null, new Entry(egg, QueryCoordinator.newCoordinationData()));
				} catch (MzsCoreException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			this.close();
			return;
		}
		
		log.info("#######################################");
		log.info("###### chicken started (lay " + productCount + " eggs)");
		log.info("#######################################");
		
		for(int i=0; i < productCount; i++)	{
//			int sleep = new Random().nextInt(3) + 1;
			try {
				tx = capi.createTransaction(TransactionTimeout.INFINITE, space);
//				sleep(sleep * 1000);
				
				
				// egg id is set via space aspect
				egg = new Egg(this.id, getRandomColorCount());
				egg.setError(this.calculateDefect());
				
				capi.write(container, 0, tx, new Entry(egg, QueryCoordinator.newCoordinationData()));
				
				log.info("###### EGG ("+(i+1) + "/" + productCount + ") done");
				log.info("#######################################");
				
				capi.commitTransaction(tx);

				/*
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				try {
					capi.rollbackTransaction(tx);
				} catch (MzsCoreException e1) {
				}
				*/
			} catch (MzsCoreException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				try {
					capi.rollbackTransaction(tx);
				} catch (MzsCoreException e1) {
				}
			}
		}
		
		log.info("#######################################");
		log.info("###### chicken done");
		log.info("#######################################");
		this.close();
	}
	
	private int getRandomColorCount()	{
		return new Random().nextInt(3) + 2;
	}
	
}
