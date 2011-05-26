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
import org.mozartspaces.core.MzsCoreException;
import org.mozartspaces.core.MzsConstants.Container;
import org.mozartspaces.core.aspects.ContainerIPoint;


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
        	
        	log.info("CREATE CONTAINER (products)");
        	productsRef = Util.forceCreateContainer("products", 
        			space, 
        			capi, 
        			Container.UNBOUNDED, 
        			new ArrayList<Coordinator>() {{ 
						add(new LindaCoordinator());	// eggs to color
						add(new QueryCoordinator());	// build bunny (select missing products)
						add(new AnyCoordinator());		// bunnies (can be selected randomly)
					}}, 
					null, null);
        	
        	nestsRef = Util.forceCreateContainer("nests", 
        			space, 
        			capi, 
        			Container.UNBOUNDED, 
        			new ArrayList<Coordinator>() {{ 
        				add(new LindaCoordinator());	// logistic rabbit selects not shipped nests
        				add(new AnyCoordinator());		// completed nests
        			}}, 
        			null, null);
        	
        	nestsCompletedRef = Util.forceCreateContainer("nestsCompleted", 
        			space, 
        			capi, 
        			Container.UNBOUNDED, 
        			new ArrayList<Coordinator>() {{ 
        				add(new LindaCoordinator());	// logistic rabbit selects not shipped nests
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
        	
        	// add aspect
        	capi.addContainerAspect(new IdAspect(), productsRef, new HashSet<ContainerIPoint>(){{ add(ContainerIPoint.PRE_WRITE); }}, null);
        	capi.addContainerAspect(new IdAspect(), nestsRef, new HashSet<ContainerIPoint>(){{ add(ContainerIPoint.PRE_WRITE); }}, null);
        	
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
