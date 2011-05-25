package sbc.spaceServer;

import java.util.concurrent.atomic.AtomicInteger;

import org.mozartspaces.capi3.Capi3AspectPort;
import org.mozartspaces.capi3.SubTransaction;
import org.mozartspaces.capi3.Transaction;
import org.mozartspaces.core.Entry;
import org.mozartspaces.core.aspects.AbstractContainerAspect;
import org.mozartspaces.core.aspects.AspectResult;
import org.mozartspaces.core.requests.WriteEntriesRequest;

import sbc.model.ChocolateRabbit;
import sbc.model.Egg;
import sbc.model.Nest;

/**
 * Container aspect, which adds ids to the entries
 *
 */
public class IdAspect extends AbstractContainerAspect {

	private static final long serialVersionUID = 2125907508047200299L;

	private static AtomicInteger eggID = new AtomicInteger(0);
	private static AtomicInteger chocoID = new AtomicInteger(0);
	private static AtomicInteger nestID = new AtomicInteger(0);
	
	public IdAspect()	{}
	
	@Override
	public AspectResult preWrite(WriteEntriesRequest request, Transaction tx,
			SubTransaction stx, Capi3AspectPort capi3, int executionCount) {
		
		
		for(Entry e : request.getEntries())	{
			if(e.getValue() instanceof Egg)	{
				Egg mo = (Egg) e.getValue();
				if(!mo.hasId())	{
					mo.setId(eggID.incrementAndGet());
//					System.out.println("set EGG id: " + eggID.get());
				}
			} else if(e.getValue() instanceof ChocolateRabbit)	{
				ChocolateRabbit mo = (ChocolateRabbit) e.getValue();
				if(!mo.hasId())	{
					mo.setId(chocoID.incrementAndGet());
//					System.out.println("set CHOCO id: " + chocoID.get());
				}
			} else if(e.getValue() instanceof Nest)	{
				Nest mo = (Nest) e.getValue();
				if(!mo.hasId())	{
					mo.setId(nestID.incrementAndGet());
//					System.out.println("set NEST id: " + nestID.get());
				}
			}
		}
		
		return AspectResult.OK;
	}
	
}
