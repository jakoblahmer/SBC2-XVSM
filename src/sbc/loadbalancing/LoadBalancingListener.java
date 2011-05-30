package sbc.loadbalancing;

import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
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

import sbc.model.lindamodel.WorkerCount;

/**
 * seperate thread to check whether company on given space URI produces too much / too less products 
 */
public class LoadBalancingListener extends Thread {

	private static Logger log = Logger.getLogger(LoadBalancingListener.class);
	
	private URI space;
	// callback to notify loadbalancing rabbit of abnormality
	private ILoadBalancingCallback callback;
	private Capi capi;
	private ContainerReference eggsToColorContainer;
	private ContainerReference productsContainer;
	private ContainerReference systemInfoContainer;
	
	private Map<String, Query> queryList;
	private Map<String, Integer> workerCount;

	public LoadBalancingListener(ILoadBalancingCallback callback, URI spaceURI, Capi capi)	{
		this.callback = callback;
		this.space = spaceURI;
		this.capi = capi;
		
		queryList = new HashMap<String, Query>();
		workerCount = new HashMap<String, Integer>();
		
		workerCount.put("buildRabbit", 0);
		workerCount.put("colorRabbit", 0);
		workerCount.put("testRabbit", 0);
		workerCount.put("logisticRabbit", 0);
		
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
		this.retrieveSystemInforations();
	}
	

	private void retrieveSystemInforations() {
		// capi.take(loadbalancingRef, QueryCoordinator.newSelector(query), RequestTimeout.TRY_ONCE, tx);
		try {
			for(Entry<String, Query> entry : queryList.entrySet())	{
				ArrayList<Serializable> res = capi.read(systemInfoContainer, QueryCoordinator.newSelector(entry.getValue()), RequestTimeout.TRY_ONCE, null);
				if(res != null)	{
					Serializable obj = res.get(0);
					if(!(obj instanceof WorkerCount))	{
						obj = ((org.mozartspaces.core.Entry) obj).getValue();
					}
					workerCount.put(entry.getKey(), ((WorkerCount) obj).getCountAndRemoveChanged());
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
	 * 
	 */
	@Override
	public void run()	{
		log.info("START NOTIFICATIONS");
	}
	
	
	private void close() {
		// TODO Auto-generated method stub
	}
}
