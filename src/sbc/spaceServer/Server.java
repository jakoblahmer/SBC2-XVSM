package sbc.spaceServer;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;

import org.apache.log4j.Logger;
import org.mozartspaces.capi3.AnyCoordinator;
import org.mozartspaces.capi3.Coordinator;
import org.mozartspaces.capi3.LindaCoordinator;
import org.mozartspaces.capi3.QueryCoordinator;
import org.mozartspaces.core.Capi;
import org.mozartspaces.core.ContainerReference;
import org.mozartspaces.core.DefaultMzsCore;
import org.mozartspaces.core.Entry;
import org.mozartspaces.core.MzsCoreException;
import org.mozartspaces.core.MzsConstants.Container;
import org.mozartspaces.core.aspects.ContainerIPoint;
import org.mozartspaces.core.aspects.SpaceIPoint;

import sbc.model.lindamodel.WorkerCount;


/**
 * starts the XVSM space
 * 	creates containers ("products", "nests")
 * 	adds aspects (id aspect)
 * @author ja
 *
 */
public class Server {

	private static Logger log = Logger.getLogger(Server.class);
	
	/**
	 * ACTUALLY NOT NEEDED, because of configuration file
	 * expected parameter: space URI
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
		Server admin = new Server(space);
	}

	private DefaultMzsCore core;
	private Capi capi;
	private URI space;
	// products container (should be splittet in egg, choco, colored egg container)
	private ContainerReference productsRef;
	// nests container (contains nests before tested and shipped)
	private ContainerReference nestsRef;
	// error nests container (nests containing a product with an error are stored here)
	private ContainerReference nestsErrorRef;
	// completed nests (tested && shipped nests are stored here)
	private ContainerReference nestsCompletedRef;
	private ContainerReference egssToColorRef;
	private ContainerReference systemInfoRef;

	
	/**
	 * @param space
	 */
	public Server(URI space)	{
		this.space = space;
		
		this.initXVSMSpace();
		
		this.initXVSMContainers();
		
		this.addShutdownHook();
		
		log.info("shutdown using: Ctrl + C");
	}
	
	/**
	 * inits the XVSM space
	 */
	private void initXVSMSpace() {
        core = DefaultMzsCore.newInstance();
        capi = new Capi(core);
	}

	/**
	 * creates the xvsm containers
	 */
	private void initXVSMContainers() {
		// create products container
        try {
        	
        	egssToColorRef = Util.forceCreateContainer("eggsToColor", 
        			space, 
        			capi, 
        			Container.UNBOUNDED, 
        			new ArrayList<Coordinator>() {{ 
						add(new QueryCoordinator());	// eggs to color selected via linda
					}}, 
					null, null);
        	
        	productsRef = Util.forceCreateContainer("products", 
        			space, 
        			capi, 
        			Container.UNBOUNDED, 
        			new ArrayList<Coordinator>() {{ 
						add(new QueryCoordinator());	// build bunny (select missing products)
						add(new AnyCoordinator());		// bunnies (can be selected randomly)
					}}, 
					null, null);
        	
        	nestsRef = Util.forceCreateContainer("nests", 
        			space, 
        			capi, 
        			Container.UNBOUNDED, 
        			new ArrayList<Coordinator>() {{ 
        				add(new QueryCoordinator());	// test rabbit selects not tested nests
//        				add(new AnyCoordinator());		// completed nests
        			}}, 
        			null, null);
        	
        	nestsCompletedRef = Util.forceCreateContainer("nestsCompleted", 
        			space, 
        			capi, 
        			Container.UNBOUNDED, 
        			new ArrayList<Coordinator>() {{ 
        				add(new AnyCoordinator());		// completed nests
        			}}, 
        			null, null);
        	
        	
        	// stores the nests containing a product with an error
        	nestsErrorRef = Util.forceCreateContainer("nestsError", 
        			space, 
        			capi, 
        			Container.UNBOUNDED, 
        			new ArrayList<Coordinator>() {{ 
        				add(new AnyCoordinator());		// select an error nest (nest containing a product with an error)
        			}},
        			null, null);
        	
        	// number of workers etc is stored here
        	systemInfoRef = Util.forceCreateContainer("systemInfo", 
        			space, 
        			capi, 
        			Container.UNBOUNDED, 
        			new ArrayList<Coordinator>() {{ 
        				add(new AnyCoordinator());
        				add(new QueryCoordinator());
        			}},
        			null, null);
        	
        	// add ID aspect (creates ids for products)
        	IdAspect aspect = new IdAspect();
        	capi.addContainerAspect(aspect, egssToColorRef, ContainerIPoint.PRE_WRITE);
        	capi.addContainerAspect(aspect, productsRef, ContainerIPoint.PRE_WRITE);
        	capi.addContainerAspect(aspect, nestsRef, ContainerIPoint.PRE_WRITE);
        	
        	
        	// create systemInfo objects
        	capi.write(systemInfoRef, 0, null, new Entry(new WorkerCount("buildRabbit"), AnyCoordinator.newCoordinationData()));
        	capi.write(systemInfoRef, 0, null, new Entry(new WorkerCount("colorRabbit"), AnyCoordinator.newCoordinationData()));
        	capi.write(systemInfoRef, 0, null, new Entry(new WorkerCount("logisticRabbit"), AnyCoordinator.newCoordinationData()));
        	capi.write(systemInfoRef, 0, null, new Entry(new WorkerCount("testRabbit"), AnyCoordinator.newCoordinationData()));
        	
		} catch (MzsCoreException e) {
			this.close();
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
	
	private void close()	{
    	try {
    		System.out.println("SHUTTING DOWN....");
    		capi.destroyContainer(productsRef, null);
    		capi.destroyContainer(nestsRef, null);
			capi.shutdown(space);
			core.shutdown(true);
		} catch (MzsCoreException e) {
			System.out.println("ERROR SHUTTING DOWN SERVER");
		}
	}
}
