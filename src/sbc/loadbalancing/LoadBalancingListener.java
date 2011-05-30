package sbc.loadbalancing;

import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.log4j.Logger;
import org.mozartspaces.capi3.Matchmaker;
import org.mozartspaces.capi3.Matchmakers;
import org.mozartspaces.capi3.Property;
import org.mozartspaces.capi3.Query;
import org.mozartspaces.capi3.QueryCoordinator;
import org.mozartspaces.core.Capi;
import org.mozartspaces.core.ContainerReference;
import org.mozartspaces.core.MzsConstants.RequestTimeout;
import org.mozartspaces.core.MzsCoreException;
import org.mozartspaces.notifications.Notification;
import org.mozartspaces.notifications.NotificationListener;
import org.mozartspaces.notifications.NotificationManager;
import org.mozartspaces.notifications.Operation;

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
		
		this.initListener();
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
		try {
			for(Entry<String, Query> entry : queryList.entrySet())	{
				ArrayList<Serializable> res = capi.read(systemInfoContainer, QueryCoordinator.newSelector(entry.getValue()), RequestTimeout.TRY_ONCE, null);
				if(res != null)	{
					Serializable obj = res.get(0);
					if(!(obj instanceof ObjectCount))	{
						obj = ((org.mozartspaces.core.Entry) obj).getValue();
					}
					workerCount.put(entry.getKey(), ((ObjectCount) obj).getCountAndRemoveChanged());
					log.info("set: " + entry.getKey() + " to: " + workerCount.get(entry.getKey()));
				}
			}
		} catch (MzsCoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
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
				
				if(Math.abs(this.getEggColoredFactor()) < LoadBalancingRabbit.maxEggColoredFactor &&
					Math.abs(this.getEggFactor()) < LoadBalancingRabbit.maxEggFactor &&
					Math.abs(this.getChocoRabbitFactor()) < LoadBalancingRabbit.maxChocoRabbitFactor)	{
					continue;
				}
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
							if(((Egg) s).getColor().isEmpty())	{
								eggs++;
							}
						}
					}
				}
			}, Operation.WRITE);
		
		

			productsNotification = nm.createNotification(productsContainer, new NotificationListener() {
				
				@Override
				public void entryOperationFinished(Notification arg0, Operation arg1,
						List<? extends Serializable> arg2) {
					
					if(arg1 == Operation.WRITE)	{
						for(Serializable s : arg2)	{
							Serializable obj = ((org.mozartspaces.core.Entry) s).getValue();
							
							if(obj instanceof Egg)	{
								eggs--;
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
		return this.workerCount.get("colorRabbit") - this.eggs;
	}
	
	/**
	 * returns the current "colored egg" factor
	 * 	- how many colored eggs can be taken / are needed
	 * @return
	 * 		- positive value: too much colored eggs available
	 * 		- negative value: colored eggs are needed
	 */
	public int getEggColoredFactor()	{
		return (this.workerCount.get("buildRabbit") * 2) - this.eggsColored;
	}
	
	/**
	 * returns the current "choco" factor
	 * 	- how many choco can be taken / are needed
	 * @return
	 * 		- positive value: too much chocorabbits available
	 * 		- negative value: chocorabbits are needed
	 */
	public int getChocoRabbitFactor()	{
		return this.workerCount.get("buildRabbit") - this.chocoRabbits;
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
	
	private void close() {
		// TODO Auto-generated method stub
	}
}
