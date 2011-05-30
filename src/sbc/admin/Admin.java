package sbc.admin;

import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
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
import sbc.model.lindamodel.ChocolateRabbit;
import sbc.model.lindamodel.Egg;
import sbc.model.lindamodel.Nest;
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
	
	private final static Double MAX_FAILURE_RATE = 0.5; 
	
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
	private ContainerReference eggsToColorRef;
	private ContainerReference nestsCompletedRef;
	private ContainerReference nestsErrorRef;

	private NotificationManager nm;
	
	private Notification productsNotification;
	private Notification nestsNotification;
	private Notification eggsToColorNotification;
	private Notification nestsCompletedNotification;
	private Notification nestsErrorNotification;

	
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
        try {
        	
        	eggsToColorRef = capi.lookupContainer("eggsToColor", space, RequestTimeout.DEFAULT, null);
        	productsRef = capi.lookupContainer("products", space, RequestTimeout.DEFAULT, null);
        	nestsRef = capi.lookupContainer("nests", space, RequestTimeout.DEFAULT, null);
        	nestsCompletedRef = capi.lookupContainer("nestsCompleted", space, RequestTimeout.DEFAULT, null);
        	nestsErrorRef = capi.lookupContainer("nestsError", space, RequestTimeout.DEFAULT, null);
        	
        	this.createNotifications();
        	
		} catch (MzsCoreException e) {
			this.close();
		}
	}

	/**
	 * creates the notifications for the live statistics
	 * 
	 * TODO check performance of ALL notifications, probably not best solution...
	 */
	private void createNotifications() {
    	try {
    		
    		// TODO check performance of ALL notifications, probably not best solution...
    		
    		eggsToColorNotification = nm.createNotification(eggsToColorRef, new NotificationListener() {
				@Override
				public void entryOperationFinished(Notification arg0, Operation arg1, List<? extends Serializable> arg2) {
					for(Serializable s : arg2)	{
						if(!(s instanceof Egg))	{
							s = ((Entry) s).getValue();
						}
						if(s instanceof Egg)	{
							if(((Egg) s).getColor().isEmpty())	{
								gui.updateEgg(1);
							}
						}
					}
				}
			}, Operation.WRITE);
    		
    		
        	productsNotification = nm.createNotification(productsRef, new NotificationListener() {
				
				@Override
				public void entryOperationFinished(Notification arg0, Operation arg1,
						List<? extends Serializable> arg2) {
					
					for(Serializable s : arg2)	{
						Serializable obj = ((Entry) s).getValue();
						
						if(obj instanceof Egg)	{
							gui.addColoredEgg(1);
							gui.updateEgg(-1);
						} else if(obj instanceof ChocolateRabbit)	{
							gui.updateChoco(1);
						} else	{
							log.error("NO EGG / ChocoBunny given - RETURN");
							return;
						}
					}
				}
			}, Operation.WRITE);
    		
        	
        	nestsNotification = nm.createNotification(nestsRef, new NotificationListener() {
				
				@Override
				public void entryOperationFinished(Notification arg0, Operation arg1,
						List<? extends Serializable> arg2) {
					
					for(Serializable s : arg2)	{
						Serializable obj = ((Entry) s).getValue();
						if(obj instanceof Nest)	{
							Nest nest = (Nest) obj;
							if(!nest.isComplete())	{
								log.error("GIVEN NEST IS NOT COMPLETED - ERROR!");
								return;
							}
							if(!nest.isTested() && !nest.isShipped())	{
								// first time written here
								gui.addNest(nest);
							} else	{
								gui.updateNest(nest);
							}
						} else	{
							log.error("ERROR: NO NEST GIVEN!");
							return;
						}
					}
				}
			}, Operation.WRITE);
        	
        	nestsCompletedNotification = nm.createNotification(nestsCompletedRef, new NotificationListener() {
				
				@Override
				public void entryOperationFinished(Notification arg0, Operation arg1,
						List<? extends Serializable> arg2) {
					
					for(Serializable s : arg2)	{
						Serializable obj = ((Entry) s).getValue();
						if(obj instanceof Nest)	{
							Nest nest = (Nest) obj;
							if(!nest.isComplete())	{
								log.error("GIVEN NEST IS NOT COMPLETED - ERROR!");
								return;
							}
							
							// final destination for nest
							gui.addCompletedNest(nest);
						} else	{
							log.error("ERROR: NO NEST GIVEN!");
							return;
						}
					}
				}
			}, Operation.WRITE);
        	
        	nestsErrorNotification = nm.createNotification(nestsErrorRef, new NotificationListener() {
				
				@Override
				public void entryOperationFinished(Notification arg0, Operation arg1,
						List<? extends Serializable> arg2) {
					
					for(Serializable s : arg2)	{
						Serializable obj = ((Entry) s).getValue();
						if(obj instanceof Nest)	{
							Nest nest = (Nest) obj;
							if(!nest.isComplete())	{
								log.error("GIVEN NEST IS NOT COMPLETED - ERROR!");
								return;
							}
							gui.addErrorNest(nest);
						} else	{
							log.error("ERROR: NO NEST GIVEN!");
							return;
						}
					}
				}
			}, Operation.WRITE);
        	
		} catch (MzsCoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
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
			chick = new Chicken(new String[]{"" + chickenID.incrementAndGet(), "" + eggs, String.valueOf(new Random().nextDouble() * MAX_FAILURE_RATE), space.toString()});
			chick.start();
		}
		
		for(int i=0;i<choco; i++)	{
			rabbit = new ChocolateRabbitRabbit(new String[]{"" + chocoRabbitID.incrementAndGet(), "" + chocoRabbits, String.valueOf(new Random().nextDouble() * MAX_FAILURE_RATE), space.toString()});
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
