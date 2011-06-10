package sbc.benchmark;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.log4j.Logger;
import org.mozartspaces.capi3.QueryCoordinator;
import org.mozartspaces.core.Capi;
import org.mozartspaces.core.ContainerReference;
import org.mozartspaces.core.DefaultMzsCore;
import org.mozartspaces.core.Entry;
import org.mozartspaces.core.MzsConstants.RequestTimeout;
import org.mozartspaces.core.MzsCoreException;

import sbc.benchmark.token.StartToken;
import sbc.benchmark.token.StopToken;

public class BenchmarkServer {

	public static void main(String[] args)	{
		new BenchmarkServer(args);
	}

	private static Logger log = Logger.getLogger(BenchmarkServer.class);
	
	private int id;
	private Map<URI, ContainerReference> spaces;
	private DefaultMzsCore core;
	private Capi capi;

	private int completedNestCount;

	private int errorNestCount;

	private Timer benchmarkTimer;
	
	
	public BenchmarkServer(String[] args) {
		spaces = new HashMap<URI, ContainerReference>();
		this.parseArguments(args);
		this.initCapi();
		this.createContainerReferences();
		
		log.info("######################################");
		log.info("# BENCHMARK SERVER ###################");
		log.info("######################################");
		log.info("# press ENTER to start benchmark...");
		Scanner sc = new Scanner(System.in);
		sc.nextLine();
		this.startBenchmark();
		benchmarkTimer = new Timer();
		benchmarkTimer.schedule(new StopBenchmark(), 60000);
	}


	/**
	 * starts the benchmark
	 */
	private void startBenchmark() {
		log.info("START BENCHMARK");
		// set counter to 0
		this.completedNestCount = 0;
		this.errorNestCount = 0;
		
		// NO TRANSACTION POSSIBLE BECAUSE
		// TRANSACTIONS SUPPORT JUST 1 SPACE
//		TransactionReference tx = capi.createTransaction(TransactionTimeout.INFINITE, spaces);
		
		try {
			
			for(ContainerReference ref : spaces.values())	{
					capi.write(ref, 0, null, new Entry(new StartToken(), QueryCoordinator.newCoordinationData()));
			}
			
		} catch (MzsCoreException e) {
			log.error("BENCHMARK COULD NOT BE STARTED CORRECTLY");
		}
		
	}
	
	/**
	 * stops the benchmark
	 */
	private void stopBenchmark() {
		log.info("STOP BENCHMARK");
		// NO TRANSACTION POSSIBLE BECAUSE
		// TRANSACTIONS SUPPORT JUST 1 SPACE
//		TransactionReference tx = capi.createTransaction(TransactionTimeout.INFINITE, spaces);
		
		try {
			
			for(ContainerReference ref : spaces.values())	{
					capi.write(ref, 0, null, new Entry(new StopToken(), QueryCoordinator.newCoordinationData()));
			}
			
		} catch (MzsCoreException e) {
			log.error("BENCHMARK COULD NOT BE STOPPED CORRECTLY");
		}
		
	}

	/**
	 * timerclass, called to stop the benchmark
	 * @author ja
	 *
	 */
	class StopBenchmark extends TimerTask	{
		@Override
		public void run() {
			stopBenchmark();
		}
	}

	/**
	 * initialises capi
	 */
	private void initCapi() {
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
	 * create the systeminfo container references
	 */
	private void createContainerReferences() {
		ContainerReference systemRef;
		for(URI uri : spaces.keySet())	{
			try {
				
				systemRef = capi.lookupContainer("systemInfo", uri, RequestTimeout.DEFAULT, null);
				spaces.put(uri, systemRef);
				
			} catch (MzsCoreException e) {
				log.error("ERROR looking up systemInfo container for uri: " + uri);
				return;
			}
		}		
	}
	
	
	/**
	 * parses the arguments, expected arguments
	 * 
	 * 	- ID of benchmarkServer
	 * 	- Space URI... (multiple)
	 * @param args
	 */
	private void parseArguments(String[] args) {
//		/*** DISABLED FOR DEBUG **
		if(args.length < 2)	{
			throw new IllegalArgumentException("at least an ID and one XVSM URIs have to be given!");
		}
//		*/
		try	{
			this.id = Integer.parseInt(args[0]);
		} catch (Exception e)	{
			throw new IllegalArgumentException("ID has to be an integer!");
		}
		
		for(int i=1; i < args.length; i++)	{
			try	{
				spaces.put(URI.create(args[i]), null);
			} catch (Exception e)	{
				throw new IllegalArgumentException("URI (" + args[i] + ") could not be parsed");
			}
		}
	}
	
}
