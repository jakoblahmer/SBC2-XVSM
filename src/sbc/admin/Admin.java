package sbc.admin;

import java.io.Serializable;
import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;
import org.mozartspaces.core.Capi;
import org.mozartspaces.core.ContainerReference;
import org.mozartspaces.core.DefaultMzsCore;
import org.mozartspaces.core.Entry;
import org.mozartspaces.core.MzsCoreException;
import org.mozartspaces.core.MzsConstants.RequestTimeout;
import org.mozartspaces.notifications.Notification;
import org.mozartspaces.notifications.NotificationListener;
import org.mozartspaces.notifications.NotificationManager;
import org.mozartspaces.notifications.Operation;

import sbc.gui.AdminGUI;
import sbc.gui.ProducerInterface;
import sbc.lindamodel.ChocolateRabbit;
import sbc.lindamodel.Egg;
import sbc.lindamodel.Nest;
import sbc.producer.Chicken;
import sbc.producer.ChocolateRabbitRabbit;

/**
 * admin interface, adds notifications, creates GUI
 * @author ja
 *
 */
public class Admin implements ProducerInterface {

	private static Logger log = Logger.getLogger(Admin.class);
	
	private static AtomicInteger chickenID = new AtomicInteger(0);
	private static AtomicInteger chocoRabbitID = new AtomicInteger(0);
	
	
	/**
	 * params: XVSM URI
	 * @param args
	 */
	public static void main(String[] args)	{
		if(args.length < 1)	{
			throw new IllegalArgumentException("expected parameters: 'XVSM space Port'!");
		}
		
		URI space;
		
		try	{
			space = URI.create(args[0]);
		} catch (Exception e)	{
			throw new IllegalArgumentException("URI could not be parsed");
		}
		Admin admin = new Admin(space);
	}

	private AdminGUI gui;
	private DefaultMzsCore core;
	private Capi capi;
	private URI space;
	private ContainerReference productsRef;
	private ContainerReference nestsRef;
	private NotificationManager nm;
	private Notification productsNotification;
	private Notification nestsNotification;

	
	/**
	 * start Admin
	 * @param space
	 */
	public Admin(URI space)	{
		this.space = space;
		
		gui = new AdminGUI(this);
		gui.start();
		
		this.initXVSMConnection();
		
		this.initXVSMContainers();
		
		this.addShutdownHook();
		
		log.info("shutdown using: Ctrl + C");
	}
	
	/**
	 * start the XVSM Connection
	 */
	private void initXVSMConnection() {
        core = DefaultMzsCore.newInstance();
        capi = new Capi(core);
        nm = new NotificationManager(core);
	}

	/**
	 * creates the XVSM Containers
	 */
	private void initXVSMContainers() {
		// create products container
        try {
        	
        	productsRef = capi.lookupContainer("products", space, RequestTimeout.DEFAULT, null);
			nestsRef = capi.lookupContainer("nests", space, RequestTimeout.DEFAULT, null);
        	
        	// PRODUCT NOTIFICATION
        	productsNotification = nm.createNotification(productsRef, new NotificationListener() {
				
				@Override
				public void entryOperationFinished(Notification arg0, Operation arg1,
						List<? extends Serializable> arg2) {
					
					int chocoCount;
					int eggColoredCount;
					int eggCount = eggColoredCount = chocoCount = 0;
					for(Serializable s : arg2)	{
						if(!(s instanceof Entry))	{
							log.error("NO ENTRY give... return");
							return;
						}
						Serializable obj = ((Entry) s).getValue();
						
						if(obj instanceof Egg)	{
							if(((Egg)obj).isColored())	{
								eggColoredCount++;
								eggCount--;
							} else	{
								eggCount++;
							}
						} else if(obj instanceof ChocolateRabbit)	{
							chocoCount++;
						} else	{
							log.error("NO EGG / ChocoBunny given - RETURN");
							return;
						}
					}
					// update in live stats
					gui.updateInfoData(eggCount, eggColoredCount, chocoCount, 0, 0);
				}
			}, Operation.WRITE);
        	
        	// NEST NOTIFICATION
        	nestsNotification = nm.createNotification(nestsRef, new NotificationListener() {
				
				@Override
				public void entryOperationFinished(Notification arg0, Operation arg1,
						List<? extends Serializable> arg2) {
					
					int nestCount;
					int nestCompletedCount;
					int chocoCount;
					int eggColoredCount;
					int eggCount = eggColoredCount = chocoCount = nestCount = nestCompletedCount = 0;
					for(Serializable s : arg2)	{
						if(!(s instanceof Entry))	{
							log.error("NO ENTRY give... return");
							return;
						}
						Serializable obj = ((Entry) s).getValue();
						if(obj instanceof Nest)	{
							Nest nest = (Nest) obj;
							if(!nest.isComplete())	{
								log.error("GIVEN NEST IS NOT COMPLETED - ERROR!");
								return;
							}
							
							if(nest.isShipped())	{
								// nest is completed
								nestCompletedCount++;
								nestCount--;
							} else	{
								// nest has to be sipped
								nestCount++;
								eggColoredCount-= 2;
								chocoCount--;
							}
							gui.updateNest(nest);
						} else	{
							log.error("ERROR: NO NEST GIVEN!");
							return;
						}
					}
					// update in live stats
					gui.updateInfoData(eggCount, eggColoredCount, chocoCount, nestCount, nestCompletedCount);
				}
			}, Operation.WRITE);
		} catch (MzsCoreException e) {
			this.close();
		} catch (InterruptedException e) {
			this.close();
		}
	}

	/**
	 * interface method (callback for GUI)
	 */
	@Override
	public void createProducers(int chicken, int eggs, int choco,
			int chocoRabbits) {
		
		log.info("CREATE PRODUCERS ");
		Chicken chick;
		
		ChocolateRabbitRabbit rabbit;
		
		for(int i=0;i<chicken; i++)	{
			chick = new Chicken(new String[]{"" + chickenID.incrementAndGet(), "" + eggs, space.toString()});
			chick.start();
		}
		
		for(int i=0;i<choco; i++)	{
			rabbit = new ChocolateRabbitRabbit(new String[]{"" + chocoRabbitID.incrementAndGet(), "" + chocoRabbits, space.toString()});
			rabbit.start();
		}
		
	}

	/**
	 * adds a shutdown hook (called before shutdown)
	 */
	private void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
            	close();
            }
        });
	}
	
	/**
	 * closes the admin GUI
	 */
	protected void close()	{
    	try {
    		log.info("SHUTTING DOWN....");
    		productsNotification.destroy();
    		nestsNotification.destroy();
			core.shutdown(true);
		} catch (MzsCoreException e) {
			log.error("ERROR SHUTTING DOWN ADMIN");
		}
	}
}
