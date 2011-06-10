package sbc.loadbalancing;

import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Map.Entry;

import org.apache.log4j.Logger;
import org.mozartspaces.capi3.Matchmaker;
import org.mozartspaces.capi3.Matchmakers;
import org.mozartspaces.capi3.Property;
import org.mozartspaces.capi3.Query;
import org.mozartspaces.capi3.QueryCoordinator;
import org.mozartspaces.capi3.QueryCoordinator.QuerySelector;
import org.mozartspaces.core.Capi;
import org.mozartspaces.core.ContainerReference;
import org.mozartspaces.core.MzsConstants.RequestTimeout;
import org.mozartspaces.core.MzsConstants.TransactionTimeout;
import org.mozartspaces.core.MzsCoreException;
import org.mozartspaces.core.TransactionReference;
import org.mozartspaces.notifications.Notification;
import org.mozartspaces.notifications.NotificationListener;
import org.mozartspaces.notifications.NotificationManager;
import org.mozartspaces.notifications.Operation;

import sbc.model.Product;
import sbc.model.lindamodel.ChocolateRabbit;
import sbc.model.lindamodel.Egg;
import sbc.model.lindamodel.Nest;
import sbc.model.lindamodel.ObjectCount;

/**
 * seperate thread to check whether company on given space URI produces too much / too less products 
 */
public class LoadBalancingListener extends Thread {

	private static Logger log = Logger.getLogger(LoadBalancingListener.class);
	
	private URI space;
	// callback to notify loadbalancing rabbit of abnormality
	private ILoadBalancingCallback callback;
	private Capi capi;
	private NotificationManager nm;
	private ContainerReference eggsToColorContainer;
	private ContainerReference productsContainer;
	private ContainerReference systemInfoContainer;
	
	private Map<String, Query> queryList;
	private Map<String, Integer> workerCount;

	private Notification eggsToColorNotification;
	private Notification productsNotification;

	private int eggs;
	private int eggsColored;
	private int chocoRabbits;

	private boolean close;

	public LoadBalancingListener(ILoadBalancingCallback callback, URI spaceURI, Capi capi, NotificationManager nm)	{
		this.callback = callback;
		this.space = spaceURI;
		this.capi = capi;
		this.nm = nm;
		
		queryList = new HashMap<String, Query>();
		workerCount = new HashMap<String, Integer>();
		
		workerCount.put("buildRabbit", 0);
		workerCount.put("colorRabbit", 0);
		workerCount.put("testRabbit", 0);
		workerCount.put("logisticRabbit", 0);
		
		this.eggs = 0;
		this.eggsColored = 0;
		this.chocoRabbits = 0;
		
		this.initSelectors();
		
		this.initListener();
	}
	
	private void initSelectors() {
	}

	/**
	 * 	- inits container references
	 * 	- get number of workers (optional)
	 */
	private void initListener()	{
		try {
			eggsToColorContainer = capi.lookupContainer("eggsToColor", space, RequestTimeout.DEFAULT, null);
			productsContainer = capi.lookupContainer("products", space, RequestTimeout.DEFAULT, null);
			systemInfoContainer = capi.lookupContainer("systemInfo", space, RequestTimeout.DEFAULT, null);
		} catch (MzsCoreException e) {
			this.close();
		}
		
		// queries:
		Property nameProperty = Property.forName("WorkerCount.class", "name");
		Property changedProperty = Property.forName("WorkerCount.class", "changed");
		
		Query buildRabbitQuery = new Query().filter(Matchmakers.and(nameProperty.equalTo("buildRabbit"), changedProperty.equalTo(true)));
		buildRabbitQuery.cnt(1);
		
		Query colorRabbitQuery = new Query().filter(Matchmakers.and(nameProperty.equalTo("colorRabbit"), changedProperty.equalTo(true)));
		colorRabbitQuery.cnt(1);
		
		Query testRabbitQuery = new Query().filter(Matchmakers.and(nameProperty.equalTo("testRabbit"), changedProperty.equalTo(true)));
		testRabbitQuery.cnt(1);
		
		Query logisticRabbitQuery = new Query().filter(Matchmakers.and(nameProperty.equalTo("logisticRabbit"), changedProperty.equalTo(true)));
		logisticRabbitQuery.cnt(1);
		
		queryList.put("buildRabbit", buildRabbitQuery);
		queryList.put("colorRabbit", colorRabbitQuery);
		queryList.put("testRabbit", testRabbitQuery);
		queryList.put("logisticRabbit", logisticRabbitQuery);
	}
	

	/**
	 * fetch the system information from space (worker count)
	 */
	private void retrieveSystemInforations() {
		// capi.take(loadbalancingRef, QueryCoordinator.newSelector(query), RequestTimeout.TRY_ONCE, tx);
		for(Entry<String, Query> entry : queryList.entrySet())	{
			try	{
				ArrayList<Serializable> res = capi.read(systemInfoContainer, QueryCoordinator.newSelector(entry.getValue()), RequestTimeout.TRY_ONCE, null);
				
				if(res != null)	{
					Serializable obj = res.get(0);
					if(!(obj instanceof ObjectCount))	{
						obj = ((org.mozartspaces.core.Entry) obj).getValue();
					}
					workerCount.put(entry.getKey(), ((ObjectCount) obj).getCountAndRemoveChanged());
					log.info("set: " + entry.getKey() + " to: " + workerCount.get(entry.getKey()));
				}
			} catch (MzsCoreException e) {
				// TODO Auto-generated catch block
//				e.printStackTrace();
				log.info("System Informations for " + entry.getKey() + " not available...");
				workerCount.put(entry.getKey(), 0);
			}
		}
	}

	/**
	 * on thread start
	 * 	- add notifications to the space
	 *  - check count regulary
	 */
	@Override
	public void run()	{
		
		// retrieve worker information
		this.retrieveSystemInforations();
		
		// start notifications
		this.registerNotifications();
		
		while(!close)	{
			try {
				// timout
				sleep(LoadBalancingRabbit.timout);
				
				// retrieve system information here for current number of test / build / color / logistic rabbits
				// performance?
//				this.retrieveSystemInforations();
				
				if(Math.abs(this.getEggColoredFactor()) < LoadBalancingRabbit.maxEggColoredFactor &&
					Math.abs(this.getEggFactor()) < LoadBalancingRabbit.maxEggFactor &&
					Math.abs(this.getChocoRabbitFactor()) < LoadBalancingRabbit.maxChocoRabbitFactor)	{
					continue;
				}
				log.info("CHECK BALANCE");
				callback.checkLoadBalance();
				
			} catch (InterruptedException e) {
				close = true;
			}
		}
	}
	
	
	private void registerNotifications() {
    	try {
			eggsToColorNotification = nm.createNotification(eggsToColorContainer, new NotificationListener() {
				@Override
				public void entryOperationFinished(Notification arg0, Operation arg1, List<? extends Serializable> arg2) {
					for(Serializable s : arg2)	{
						if(!(s instanceof Egg))	{
							s = ((org.mozartspaces.core.Entry) s).getValue();
						}
						if(s instanceof Egg)	{
							if(arg1 == Operation.WRITE)
								eggs++;
							else if(arg1 == Operation.TAKE)
								eggs--;
						}
					}
				}
			}, Operation.WRITE, Operation.TAKE);
			
			
			productsNotification = nm.createNotification(productsContainer, new NotificationListener() {
				
				@Override
				public void entryOperationFinished(Notification arg0, Operation arg1,
						List<? extends Serializable> arg2) {
					
					if(arg1 == Operation.WRITE)	{
						for(Serializable s : arg2)	{
							Serializable obj = ((org.mozartspaces.core.Entry) s).getValue();
							
							if(obj instanceof Egg)	{
								eggsColored++;
							} else if(obj instanceof ChocolateRabbit)	{
								chocoRabbits++;
							} else	{
								log.error("NO EGG / ChocoBunny given - RETURN");
								return;
							}
						}
					} else if(arg1 == Operation.TAKE)	{
						for(Serializable s : arg2)	{
							Serializable obj = ((org.mozartspaces.core.Entry) s).getValue();
							
							if(obj instanceof Egg)	{
								eggsColored--;
							} else if(obj instanceof ChocolateRabbit)	{
								chocoRabbits--;
							} else	{
								log.error("NO EGG / ChocoBunny given - RETURN");
								return;
							}
						}
					}
				}
			}, Operation.WRITE, Operation.TAKE);
			
			
		} catch (MzsCoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * returns the current "egg" factor
	 * 	- how many eggs can be taken / are needed
	 * @return
	 * 		- positive value: too much eggs available
	 * 		- negative value: eggs are needed
	 */
	public int getEggFactor()	{
		return this.eggs - (2 * this.workerCount.get("colorRabbit")) - 1;
	}
	
	/**
	 * returns the current "colored egg" factor
	 * 	- how many colored eggs can be taken / are needed
	 * @return
	 * 		- positive value: too much colored eggs available
	 * 		- negative value: colored eggs are needed
	 */
	public int getEggColoredFactor()	{
		return this.eggsColored - (3 * this.workerCount.get("buildRabbit")) - 1;
	}
	
	/**
	 * returns the current "choco" factor
	 * 	- how many choco can be taken / are needed
	 * @return
	 * 		- positive value: too much chocorabbits available
	 * 		- negative value: chocorabbits are needed
	 */
	public int getChocoRabbitFactor()	{
		return this.chocoRabbits - (2 * this.workerCount.get("buildRabbit")) - 1;
	}
	
	/**
	 * moves the given amount of type to the target
	 * 
	 * @param target
	 * @param type
	 * @param amount
	 */
	public synchronized void moveTo(LoadBalancingListener target, ProductType type, int amount)	{
		log.info(this.space + "	MOVE: " + amount + " " + type + " to " + target.getSpaceURI().toString());

		TransactionReference tx = null;
		try {
			tx = capi.createTransaction(TransactionTimeout.INFINITE, space);
			
			ArrayList<Serializable> objs = new ArrayList<Serializable>();
			
			if(type == ProductType.EGG)	{
				// take eggs from eggsToColor Container
				Query query = new Query();
				query.cnt(1);
				while(amount > 0)	{
					ArrayList<Serializable> obj = capi.take(eggsToColorContainer, QueryCoordinator.newSelector(query), RequestTimeout.TRY_ONCE, tx);
					if(obj == null)
						break;
					objs.addAll(obj);
					amount -= obj.size(); 
					log.info(this.space + "	TOOK 1 EGG");
				}
				capi.commitTransaction(tx);
			} else if(type == ProductType.COLORED_EGG)	{
				// take colored eggs from products Container
				Property eggColoredProperty = Property.forName("Egg.class", "colored");
				Query query = new Query().filter(eggColoredProperty.equalTo(true));
				query.cnt(1);
				while(amount > 0)	{
					ArrayList<Serializable> obj = capi.take(productsContainer, QueryCoordinator.newSelector(query), RequestTimeout.TRY_ONCE, tx);
					if(obj == null)
						break;
					objs.addAll(obj);
					amount -= obj.size(); 
					log.info(this.space + "	TOOK 1 COLORED EGG");
				}
				capi.commitTransaction(tx);
			} else if(type == ProductType.CHOCORABBIT)	{
				// take choco rabbits from products Container
				Property chocoProperty = Property.forName("ChocolateRabbit.class");
				Query query = new Query().filter(chocoProperty.exists());
				query.cnt(1);
				while(amount > 0)	{
					ArrayList<Serializable> obj = capi.take(productsContainer, QueryCoordinator.newSelector(query), RequestTimeout.TRY_ONCE, tx);
					if(obj == null)
						break;
					objs.addAll(obj);
					amount -= obj.size(); 
					log.info(this.space + "	TOOK 1 CHOCO");
				}
				capi.commitTransaction(tx);
			}
			// write objects to target container
			target.write(type, objs);
		} catch (MzsCoreException e) {
			try {
				capi.rollbackTransaction(tx);
			} catch (MzsCoreException e1) {
			}
			log.info("COULD NOT TAKE ELEMENT FROM SPACE");
		}
	}
	
	
	/**
	 * writes objects to container, specified by product type
	 * @param type
	 * @param obj
	 */
	private synchronized void write(ProductType type, List<Serializable> obj) {
		log.info(this.space + "	GET: " + obj.size() + " " + type);
		
		if(type == ProductType.EGG)	{
			writeToContainer(eggsToColorContainer, obj);
		} else if(type == ProductType.COLORED_EGG || type == ProductType.CHOCORABBIT)	{
			writeToContainer(productsContainer, obj);
		}
	}

	/**
	 * writes objects to given container
	 * @param obj
	 */
	private void writeToContainer(ContainerReference ref, List<Serializable> obj)	{
		TransactionReference tx = null;
		try {
			tx = capi.createTransaction(TransactionTimeout.INFINITE, space);
			
			for(Serializable entry : obj)	{
				log.info(this.space + "	WRITE " + entry);
				if(entry instanceof org.mozartspaces.core.Entry)	{
					entry = ((org.mozartspaces.core.Entry) entry).getValue();
				}
					capi.write(new org.mozartspaces.core.Entry(entry), ref, RequestTimeout.TRY_ONCE, tx);
			}
			
			capi.commitTransaction(tx);
			
		} catch (MzsCoreException e) {
			try {
				capi.rollbackTransaction(tx);
			} catch (MzsCoreException e1) {}
			
			e.printStackTrace();
		}
	}
	
	public int getEggs()	{
		return this.eggs;
	}
	
	public int getEggsColored()	{
		return this.eggsColored;
	}
	
	public int getChocoRabbits()	{
		return this.chocoRabbits;
	}
	
	/**
	 * returns the space URI
	 * @return
	 */
	public URI getSpaceURI()	{
		return this.space;
	}
	
	
	private void close() {
		// TODO Auto-generated method stub
	}
}
