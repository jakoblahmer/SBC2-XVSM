package sbc.loadbalancing;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.log4j.Logger;
import org.mozartspaces.core.Capi;
import org.mozartspaces.core.DefaultMzsCore;
import org.mozartspaces.notifications.NotificationManager;

/**
 * balances the products of more than 1 company
 */
public class LoadBalancingRabbit implements ILoadBalancingCallback {

	private static boolean loadbalanceActive = false;
	
	public static final int timout = 3000;

	private static Logger log = Logger.getLogger(LoadBalancingRabbit.class);
	
	public static final int maxEggFactor = 6;
	public static final int maxEggColoredFactor = 6;
	public static final int maxChocoRabbitFactor = 5;
	
	public static void main(String[] args)	{
		LoadBalancingRabbit lbr = new LoadBalancingRabbit(args);
	}

	private int id;
	
	// list containing all space uris
	private Map<URI, LoadBalancingListener> spaceURIs;

	private DefaultMzsCore core;

	private Capi capi;

	private NotificationManager nm;
	
	public LoadBalancingRabbit(String[] args)	{
		spaceURIs = new HashMap<URI, LoadBalancingListener>();
		this.parseArguments(args);
		
		this.initCapi();
		
		this.startListeners();
	}

	/**
	 * parses the arguments, expected arguments
	 * 
	 * 	- ID of loadbalancingrabbit
	 * 	- Space URI... (multiple)
	 * @param args
	 */
	private void parseArguments(String[] args) {
//		/*** DISABLED FOR DEBUG **
		if(args.length < 3)	{
			throw new IllegalArgumentException("at least an ID and two XVSM URIs have to be given!");
		}
//		*/
		try	{
			this.id = Integer.parseInt(args[0]);
		} catch (Exception e)	{
			throw new IllegalArgumentException("ID has to be an integer!");
		}
		
		for(int i=1; i < args.length; i++)	{
			try	{
				spaceURIs.put(URI.create(args[i]), null);
			} catch (Exception e)	{
				throw new IllegalArgumentException("URI (" + args[i] + ") could not be parsed");
			}
		}
	}

	/**
	 * inits xvsm capi
	 */
	protected void initCapi() {
		
		try	{
	        // Create an embedded space and construct a Capi instance for it
	        core = DefaultMzsCore.newInstance();
	        capi = new Capi(core);
	        nm = new NotificationManager(core);
		} catch(Exception e)	{
			System.out.println("ERROR: " + e.getCause());
		}
        if(capi == null)	{
        	System.out.println("ERROR: CAPI is null");
        }
	}
	
	/**
	 * starts listener threads
	 */
	private void startListeners() {
		LoadBalancingListener lbl;
		for(URI uri : spaceURIs.keySet())	{
			lbl = new LoadBalancingListener(this, uri, this.capi, this.nm);
			lbl.start();
			spaceURIs.put(uri, lbl);
		}
	}

	
	@Override
	public synchronized void checkLoadBalance() {
		if(loadbalanceActive)	{
			return;
		}
		loadbalanceActive = true;
		List<LoadBalancingListener> needEggs, needColoredEggs, needChocoRabbits, hasEggs, hasColoredEggs, hasChocoRabbits;
		needEggs = new ArrayList<LoadBalancingListener>();
		needColoredEggs = new ArrayList<LoadBalancingListener>();
		needChocoRabbits = new ArrayList<LoadBalancingListener>();
		hasEggs = new ArrayList<LoadBalancingListener>();
		hasColoredEggs = new ArrayList<LoadBalancingListener>();
		hasChocoRabbits = new ArrayList<LoadBalancingListener>();
		
		// create priority list for each product
		int index = 0;
		for(LoadBalancingListener lbl : spaceURIs.values())	{
			if(lbl.getEggFactor() > 0)	{
				index = 0;
				for(LoadBalancingListener tmp : hasEggs)	{
					if(tmp.getEggFactor() > lbl.getEggFactor())
						index = hasEggs.indexOf(tmp) + 1;
				}
				hasEggs.add(index, lbl);
			} else if(lbl.getEggFactor() < 0)	{
				index = 0;
				for(LoadBalancingListener tmp : needEggs)	{
					if(tmp.getEggFactor() < lbl.getEggFactor())
						index = needEggs.indexOf(tmp) + 1;
				}
				needEggs.add(index, lbl);
			}
			
			if(lbl.getEggColoredFactor() > 0)	{
				index = 0;
				for(LoadBalancingListener tmp : hasColoredEggs)	{
					if(tmp.getEggColoredFactor() > lbl.getEggColoredFactor())
						index = hasColoredEggs.indexOf(tmp) + 1;
				}
				hasColoredEggs.add(index, lbl);
			} else if(lbl.getEggColoredFactor() < 0)	{
				index = 0;
				for(LoadBalancingListener tmp : needColoredEggs)	{
					if(tmp.getEggColoredFactor() < lbl.getEggColoredFactor())
						index = needColoredEggs.indexOf(tmp) + 1;
				}
				needColoredEggs.add(index, lbl);
			}
			
			if(lbl.getChocoRabbitFactor() > 0)	{
				index = 0;
				for(LoadBalancingListener tmp : hasChocoRabbits)	{
					if(tmp.getChocoRabbitFactor() > lbl.getChocoRabbitFactor())
						index = hasChocoRabbits.indexOf(tmp) + 1;
				}
				hasChocoRabbits.add(index, lbl);
			} else if(lbl.getChocoRabbitFactor() < 0)	{
				index = 0;
				for(LoadBalancingListener tmp : needChocoRabbits)	{
					if(tmp.getChocoRabbitFactor() < lbl.getChocoRabbitFactor())
						index = needChocoRabbits.indexOf(tmp) + 1;
				}
				needChocoRabbits.add(lbl);
			}
		}
		
		log.info("HAS EGGS:");
		for(LoadBalancingListener lbl : hasEggs)	{
			log.info("	" + lbl.getSpaceURI() + " " + lbl.getEggFactor());
		}
		
		log.info("NEED EGGS:");
		for(LoadBalancingListener lbl : needEggs)	{
			log.info("	" + lbl.getSpaceURI() + " " + lbl.getEggFactor());
		}
		
		log.info("HAS COL EGGS:");
		for(LoadBalancingListener lbl : hasColoredEggs)	{
			log.info("	" + lbl.getSpaceURI() + " " + lbl.getEggColoredFactor());
		}
		
		log.info("NEED COL EGGS:");
		for(LoadBalancingListener lbl : needColoredEggs)	{
			log.info("	" + lbl.getSpaceURI() + " " + lbl.getEggColoredFactor());
		}
		
		log.info("HAS CHOCO:");
		for(LoadBalancingListener lbl : hasChocoRabbits)	{
			log.info("	" + lbl.getSpaceURI() + " " + lbl.getChocoRabbitFactor());
		}
		
		log.info("NEED CHOCO:");
		for(LoadBalancingListener lbl : needChocoRabbits)	{
			log.info("	" + lbl.getSpaceURI() + " " + lbl.getChocoRabbitFactor());
		}
		
		// for each resource decide what to transfer
		LoadBalancingListener from, to;
		
		// #### EGG
		if(!hasEggs.isEmpty() && !needEggs.isEmpty())	{
			for(int i=0; i < hasEggs.size(); i++)	{
				if(needEggs.size() <= i)	{
					// break loop if no more eggs are needed
					break;
				}
				from = hasEggs.get(i);
				to = needEggs.get(i);
				
				// TODO verbesserungen des algorithmus notwendig
				/*
				if((to.getEggFactor() + from.getEggFactor()) < 0)	{
					// more eggs needed
				} else	{
					// enough (too much) eggs are moved
				}
//				*/
				from.moveTo(to, ProductType.EGG, from.getEggFactor());
			}
		}
		
		// #### COLORED EGG
		if(!hasColoredEggs.isEmpty() && !needColoredEggs.isEmpty())	{
			for(int i=0; i < hasColoredEggs.size(); i++)	{
				if(needColoredEggs.size() <= i)	{
					// break loop if no more eggs are needed
					break;
				}
				from = hasColoredEggs.get(i);
				to = needColoredEggs.get(i);
				
				// TODO verbesserungen des algorithmus notwendig
				/*
				if((to.getEggFactor() + from.getEggFactor()) < 0)	{
					// more eggs needed
				} else	{
					// enough (too much) eggs are moved
				}
//				*/
				from.moveTo(to, ProductType.COLORED_EGG, from.getEggColoredFactor());
			}
		}
		
		// #### CHOCO
		if(!hasChocoRabbits.isEmpty() && !needChocoRabbits.isEmpty())	{
			for(int i=0; i < hasChocoRabbits.size(); i++)	{
				if(needChocoRabbits.size() <= i)	{
					// break loop if no more eggs are needed
					break;
				}
				from = hasChocoRabbits.get(i);
				to = needChocoRabbits.get(i);
				
				// TODO verbesserungen des algorithmus notwendig
				/*
				if((to.getEggFactor() + from.getEggFactor()) < 0)	{
					// more eggs needed
				} else	{
					// enough (too much) eggs are moved
				}
//				*/
				from.moveTo(to, ProductType.CHOCORABBIT, from.getChocoRabbitFactor());
			}
		}
		
		loadbalanceActive = false;
	}
}








