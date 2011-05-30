package sbc.loadbalancing;

/**
 * callback interface for loadbalancing rabbit
 * @author ja
 */
public interface ILoadBalancingCallback {
	
	public void checkLoadBalance();
}
