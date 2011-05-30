package sbc.loadbalancing;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mozartspaces.core.Capi;
import org.mozartspaces.core.DefaultMzsCore;

/**
 * balances the products of more than 1 company
 */
public class LoadBalancingRabbit implements ILoadBalancingCallback {

	public static void main(String[] args)	{
		LoadBalancingRabbit lbr = new LoadBalancingRabbit(args);
	}

	private int id;
	
	// list containing all space uris
	private Map<URI, LoadBalancingListener> spaceURIs;

	private DefaultMzsCore core;

	private Capi capi;
	
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
		/*** DISABLED FOR DEBUG **
		
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
			lbl = new LoadBalancingListener(this, uri, this.capi);
			lbl.start();
		}
	}

	
	@Override
	public void checkLoadBalance() {
		// TODO Auto-generated method stub
		
	}
	
	
	
}