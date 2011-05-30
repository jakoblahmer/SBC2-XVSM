package sbc.producer;

import java.util.Random;

import org.apache.log4j.Logger;
import org.mozartspaces.capi3.AnyCoordinator;
import org.mozartspaces.core.Entry;
import org.mozartspaces.core.MzsCoreException;
import org.mozartspaces.core.MzsConstants.TransactionTimeout;
import org.mozartspaces.core.TransactionReference;

import sbc.model.lindamodel.ChocolateRabbit;

/**
 * produces choco rabbits
 * @author ja
 *
 */
public class ChocolateRabbitRabbit extends Producer {

	private static Logger log = Logger.getLogger(ChocolateRabbitRabbit.class);

	public static void main(String[] args) {
		ChocolateRabbitRabbit rabbit = new ChocolateRabbitRabbit(args);
		rabbit.start();
	}


	private ChocolateRabbit rabbit;
	private TransactionReference tx;


	public ChocolateRabbitRabbit(String[] args)	{
		super(args);
	}


	/**
	 * produces the given amount of choco rabbits
	 */
	@Override
	public void run() {
		
		log.info("#######################################");
		log.info("###### ChocolateRabbit started (make " + productCount + " ChocoRabbits)");
		log.info("#######################################");
		
		for(int i=0; i < productCount; i++)	{
			int sleep = new Random().nextInt(3) + 1;
			
			try {
				tx = capi.createTransaction(TransactionTimeout.INFINITE, space);
				sleep(sleep * 1000);
				
				// id is set via space aspect
				rabbit = new ChocolateRabbit(this.id);
				rabbit.setError(this.calculateDefect());
				
				// add with any coordinator
				capi.write(container, 0, tx, new Entry(rabbit, AnyCoordinator.newCoordinationData()));
				log.info("###### Choco Rabbit (" + (i + 1) + ") done");
				log.info("#######################################");
				capi.commitTransaction(tx);
			} catch (InterruptedException e) {
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
		log.info("###### CholateRabbit done");
		log.info("#######################################");
		this.close();
	}
}
