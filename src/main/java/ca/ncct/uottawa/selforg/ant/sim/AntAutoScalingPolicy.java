package ca.ncct.uottawa.selforg.ant.sim;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.ex.IAutoscalingPolicy;
import org.cloudbus.cloudsim.ex.MonitoringBorkerEX;
import org.cloudbus.cloudsim.ex.disk.HddCloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.ex.disk.HddVm;
import org.cloudbus.cloudsim.ex.util.CustomLog;
import org.cloudbus.cloudsim.ex.vm.VMStatus;
import org.cloudbus.cloudsim.ex.web.ILoadBalancer;
import org.cloudbus.cloudsim.ex.web.workload.brokers.WebBroker;

import java.util.*;

class AntAutoScalingPolicy implements IAutoscalingPolicy {

    private Map<Ant, HddVm> antToServer = new HashMap<>();
    private Map<HddVm, Double> pherLevels = new HashMap<>();
    private StringBuilder debugSB = new StringBuilder();
    private long appId;
    private AntSystemConfig config = null;
    private double lastTime = 0;
    private double nextDecay = 0;
    private IAntOptimizer optimizer;
    private double nextLog = 0;

    private static final int LOG_RATE = 60;

    AntAutoScalingPolicy(Properties antControlProps, long appId, IAntOptimizer optimizer) {
        this.appId = appId;
        this.config = new AntSystemConfig(antControlProps);
        this.optimizer = optimizer;
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
                    Double newPher = ant.controlStep(currServer, pherLevels.get(currServer),
                            Math.min(currServer.getCPUUtil(), 1d), appServers, diffTime);
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
                    pherLevels.replaceAll((k, v) -> Math.max(0, v - config.getDecayAmount()));
                    nextDecay = currentTime + config.getDecayRate();
                }

                pherLevels.forEach((k, v) -> debugSB.append(k.getId()).append('=').append(v).append("; "));

                if (nextLog <= currentTime) {
                    CustomLog.printf("Ant-Autoscale(%s) pheromone levels: %s", broker, this.debugSB);
                    nextLog = currentTime + LOG_RATE;
                }

                if (maxMorphCount > noMorphCount + minMorphCount) {
                    CustomLog.printf("Ant-Autoscale(%s) adding servers: %s", broker, this.debugSB);
                    optimizer.setAnts(antToServer.keySet());
                    optimizer.setPheromones((double) config.getMaxMorphLevel(),
                            (config.getMaxMorphLevel() + config.getMinMorphLevel()) / 2d);
                    addServers(optimizer.getAddServers(), loadBalancer, webBroker);
                } else if (minMorphCount > noMorphCount + maxMorphCount) {
                    optimizer.setAnts(antToServer.keySet());
                    optimizer.setPheromones((double) config.getMaxMorphLevel(),
                            (config.getMaxMorphLevel() + config.getMinMorphLevel()) / 2d);
                    if (antToServer.size() > 1) {
                        CustomLog.printf("Ant-Autoscale(%s) removing servers: %s", broker, this.debugSB);
                        removeServers(optimizer.getRemoveServers(), loadBalancer, webBroker);
                    }
                } /*else {
                    CustomLog.printf("Ant-Autoscale(%s) no change: %s", broker, this.debugSB);
                }*/
            }
        }

        lastTime = currentTime;
    }

    private void removeServers(int removeCount, ILoadBalancer loadBalancer, WebBroker webBroker) {
        CustomLog.printf("Ant-Autoscale actuating removing servers: %s", removeCount);
        List<HddVm> removeServers = new ArrayList<>();
        for (int i = 0; i < removeCount; i++) {
            for (HddVm vm : loadBalancer.getAppServers()) {
                if (EnumSet.of(VMStatus.INITIALISING, VMStatus.RUNNING).contains(vm.getStatus()) && !removeServers.contains(vm)) {
                    removeServers.add(vm);
                    break;
                }
            }
        }
        webBroker.destroyVMsAfter(removeServers, 0.0D);
        loadBalancer.getAppServers().removeAll(removeServers);
        antToServer.clear();
        pherLevels.clear();
    }

    /*private void removeAnts(List<HddVm> removeServers) {
        int removeCount = removeServers.size();
        List<Ant> antsToRemove = new ArrayList<>();

        for (Ant ant : antToServer.keySet()) {
            if (removeServers.contains(antToServer.get(ant)) && removeCount > 0) {
                antsToRemove.add(ant);
                removeCount--;
            }
        }

        for (Ant ant : antsToRemove) {
            antToServer.remove(ant);
        }

        for (HddVm vm : removeServers) {
            pherLevels.remove(vm);
        }

        // we need to reassign ants which were on removed servers
        int remaingSize = pherLevels.keySet().size();
        Random rand = new Random();
        for (Ant ant : antToServer.keySet()) {
            if (!pherLevels.keySet().contains(antToServer.get(ant))) {
                int count = rand.nextInt(remaingSize);
                Iterator<HddVm> iter = pherLevels.keySet().iterator();
                while (count > 0) {
                    iter.next();
                    count--;
                }
                antToServer.put(ant, iter.next());
            }
        }
    }*/

    private void addServers(int addCount, ILoadBalancer loadBalancer, WebBroker webBroker) {
        CustomLog.printf("Ant-Autoscale actuating adding servers: %s", addCount);
        List<HddVm> newServers = new ArrayList<>();
        for (int i = 0; i < addCount; i++) {
            HddVm newServ = loadBalancer.getAppServers().get(0).clone(new HddCloudletSchedulerTimeShared());
            loadBalancer.registerAppServer(newServ);
            newServers.add(newServ);
        }
        webBroker.createVmsAfter(newServers, 0.0D);
        antToServer.clear();
        pherLevels.clear();
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
