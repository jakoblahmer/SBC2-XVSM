package sbc.benchmark;

import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.log4j.Logger;
import org.mozartspaces.capi3.Property;
import org.mozartspaces.capi3.Query;
import org.mozartspaces.capi3.QueryCoordinator;
import org.mozartspaces.core.Capi;
import org.mozartspaces.core.ContainerReference;
import org.mozartspaces.core.DefaultMzsCore;
import org.mozartspaces.core.Entry;
import org.mozartspaces.core.MzsConstants.RequestTimeout;
import org.mozartspaces.core.MzsCoreException;

import sbc.benchmark.token.ResultEntry;
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
//		Scanner sc = new Scanner(System.in);
//		sc.nextLine();
		log.info("# benchmark started...");
		this.startBenchmark();
//		benchmarkTimer = new Timer();
//		benchmarkTimer.schedule(new StopBenchmark(), 60000);
		try {
			Thread.sleep(60000);
			log.info("10 seconds");
		} catch (InterruptedException e) {
			this.stopBenchmark();
		}
		this.stopBenchmark();
		
		this.collectResults();
	}


	/**
	 * starts the benchmark
	 */
	private void startBenchmark() {
		System.out.println("START BENCHMARK");
		
		// NO TRANSACTION POSSIBLE BECAUSE
		// TRANSACTIONS SUPPORT JUST 1 SPACE
//		TransactionReference tx = capi.createTransaction(TransactionTimeout.INFINITE, spaces);
		
		try {
			
			for(ContainerReference ref : spaces.values())	{
					capi.write(ref, 0, null, new Entry(new StartToken(), QueryCoordinator.newCoordinationData()));
					System.out.println("started: " + ref.getSpace());
			}
			
		} catch (MzsCoreException e) {
			log.error("BENCHMARK COULD NOT BE STARTED CORRECTLY");
		}
		
	}
	
	/**
	 * stops the benchmark
	 */
	private void stopBenchmark() {
		System.out.println("STOP BENCHMARK");
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
	 * collect benchmark results
	 */
	private void collectResults() {
		
		Query query = new Query().filter(Property.forName("ResultEntry.class").exists());
		query.cnt(1);
		
		ArrayList<Serializable> res = new ArrayList<Serializable>();
		
		try {
			for(ContainerReference ref : spaces.values())	{
				res.addAll(capi.take(ref, QueryCoordinator.newSelector(query), RequestTimeout.INFINITE, null));
				System.out.println("received result: " + ref.getSpace());
			}
		} catch (MzsCoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		int completed = 0;
		int error = 0;
		
		ResultEntry x;
		
		for(Serializable s : res)	{
			if(s instanceof ResultEntry)	{
				x = (ResultEntry) s;
				completed += x.getCompletedNests();
				error += x.getErrorNests();
			}
		}
		
		System.out.println("###################################");
		System.out.println("### RESULT:");
		System.out.println("###################################");
		System.out.println("	completed: " + completed);
		System.out.println("	error: " + error);
		System.out.println("	---------------------");
		System.out.println("	SUM: " + (error + completed));
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
