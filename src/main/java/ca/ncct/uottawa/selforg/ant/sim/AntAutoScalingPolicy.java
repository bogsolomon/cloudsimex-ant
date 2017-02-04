package ca.ncct.uottawa.selforg.ant.sim;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.ex.IAutoscalingPolicy;
import org.cloudbus.cloudsim.ex.MonitoringBorkerEX;
import org.cloudbus.cloudsim.ex.disk.HddVm;
import org.cloudbus.cloudsim.ex.util.CustomLog;
import org.cloudbus.cloudsim.ex.vm.VMStatus;
import org.cloudbus.cloudsim.ex.web.ILoadBalancer;
import org.cloudbus.cloudsim.ex.web.workload.brokers.WebBroker;

import java.util.*;

/**
 * Created by Bogdan on 1/31/2017.
 */
public class AntAutoScalingPolicy implements IAutoscalingPolicy {

    Map<Ant, HddVm> antToServer = new HashMap<>();
    Map<HddVm, Double> pherLevels = new HashMap<>();
    private StringBuilder debugSB = new StringBuilder();
    private long appId;
    private AntSystemConfig config = null;
    private double lastTime = 0;
    private double nextDecay = 0;

    public AntAutoScalingPolicy(Properties antControlProps, long appId) {
        this.appId = appId;
        this.config = new AntSystemConfig(antControlProps);
    }

    @Override
    public void scale(MonitoringBorkerEX broker) {
        double currentTime = CloudSim.clock();

        if (broker instanceof WebBroker) {
            this.debugSB.setLength(0);
            WebBroker webBroker = (WebBroker) broker;
            ILoadBalancer loadBalancer = webBroker.getLoadBalancers().get(this.appId);

            List<HddVm> appServers = loadBalancer.getAppServers();

            // no ants in system, this must be the first auto scaling call
            if (antToServer.isEmpty()) {
                initializeAnts(appServers);
            } else {
                double diffTime = currentTime - lastTime;
                Map<Ant, HddVm> updatedMoves = new HashMap<>();
                int maxMorphCount = 0;
                int minMorphCount = 0;
                int noMorphCount = 0;

                for (Ant ant : antToServer.keySet()) {
                    HddVm currServer = antToServer.get(ant);
                    Double newPher = ant.controlStep(currServer, pherLevels.get(currServer), currServer.getCPUUtil(), appServers, diffTime);
                    if (newPher != null) {
                        pherLevels.put(currServer, newPher);
                        updatedMoves.put(ant, ant.getNextNode());
                    }
                    switch(ant.morph()) {
                        case MaxMorph:
                            maxMorphCount++;
                            break;
                        case MinMorph:
                            minMorphCount++;
                            break;
                        default:
                            noMorphCount++;
                            break;
                    }
                }

                antToServer.putAll(updatedMoves);

                if (nextDecay <= currentTime) {
                    pherLevels.replaceAll((k, v) -> v - config.getDecayAmount());
                    nextDecay = currentTime + config.getDecayRate();
                }

                pherLevels.forEach((k, v) -> debugSB.append(k.getId()).append('=').append(v).append("; "));

                if (maxMorphCount > noMorphCount + minMorphCount) {
                    CustomLog.printf("Ant-Autoscale(%s) would add servers: %s", new Object[]{broker, this.debugSB});
                } else if (minMorphCount > noMorphCount + maxMorphCount) {
                    CustomLog.printf("Ant-Autoscale(%s) would remove servers: %s", new Object[]{broker, this.debugSB});
                } else {
                    CustomLog.printf("Ant-Autoscale(%s) no change: %s", new Object[]{broker, this.debugSB});
                }
            }
        }

        lastTime = currentTime;
    }

    private void initializeAnts(List<HddVm> appServers) {
        Double startPherLevel = ((double) config.getMaxMorphLevel() + config.getMinMorphLevel()) / 2;
        for (HddVm vm : appServers) {
            Ant ant = new Ant(vm.getId(), config);
            antToServer.put(ant, vm);
            pherLevels.put(vm, startPherLevel);
        }
    }
}
