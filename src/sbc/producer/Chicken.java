package sbc.producer;

import java.util.Random;

import org.apache.log4j.Logger;
import org.mozartspaces.capi3.LindaCoordinator;
import org.mozartspaces.core.Entry;
import org.mozartspaces.core.MzsConstants.TransactionTimeout;
import org.mozartspaces.core.MzsCoreException;
import org.mozartspaces.core.TransactionReference;

import sbc.admin.Admin;
import sbc.lindamodel.Egg;
import sbc.spaceServer.Util;

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
	 * produces the eggs and places them in the "products" container
	 */
	@Override
	public void run() {
		
		log.info("#######################################");
		log.info("###### chicken started (lay " + productCount + " eggs)");
		log.info("#######################################");
		
		for(int i=0; i < productCount; i++)	{
			int sleep = new Random().nextInt(3) + 1;
			try {
				tx = capi.createTransaction(TransactionTimeout.INFINITE, space);
				sleep(sleep * 1000);
				
				
				// egg id is set via space aspect
				egg = new Egg(this.id, getRandomColorCount());
				egg.setError(this.calculateDefect());
				
				capi.write(container, 0, tx, new Entry(egg, LindaCoordinator.newCoordinationData()));
				
				log.info("###### EGG (" + (egg.getId()) + ") ("+(i+1) + "/" + productCount + ") done");
				log.info("#######################################");
				
				capi.commitTransaction(tx);
				
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				try {
					capi.rollbackTransaction(tx);
				} catch (MzsCoreException e1) {
				}
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
		return new Random().nextInt(2) + 2;
	}
	
}