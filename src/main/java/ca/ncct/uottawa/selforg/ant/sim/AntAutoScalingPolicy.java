package ca.ncct.uottawa.selforg.ant.sim;

import org.cloudbus.cloudsim.ex.IAutoscalingPolicy;
import org.cloudbus.cloudsim.ex.MonitoringBorkerEX;
import org.cloudbus.cloudsim.ex.disk.HddVm;
import org.cloudbus.cloudsim.ex.web.ILoadBalancer;
import org.cloudbus.cloudsim.ex.web.workload.brokers.WebBroker;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Created by Bogdan on 1/31/2017.
 */
public class AntAutoScalingPolicy implements IAutoscalingPolicy {

    Map<Ant, HddVm> antToServer = new HashMap<>();
    private StringBuilder debugSB = new StringBuilder();
    private long appId;
    private AntSystemConfig config = null;

    public AntAutoScalingPolicy(Properties antControlProps, long appId) {
        this.appId = appId;
        this.config = new AntSystemConfig(antControlProps);
    }

    @Override
    public void scale(MonitoringBorkerEX broker) {
        if (broker instanceof WebBroker) {
            this.debugSB.setLength(0);
            WebBroker webBroker = (WebBroker) broker;
            ILoadBalancer loadBalancer = webBroker.getLoadBalancers().get(this.appId);

            List<HddVm> appServers = loadBalancer.getAppServers();

            // no ants in system, this must be the first auto scaling call
            if (antToServer.isEmpty()) {
                initializeAnts(appServers);
            }
        }
    }

    private void initializeAnts(List<HddVm> appServers) {
        for (HddVm vm : appServers) {
            Ant ant = new Ant(appServers, vm.getId(), config);
            antToServer.put(ant, vm);
        }
    }
}
