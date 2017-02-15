package ca.ncct.uottawa.selforg.ant.sim;//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//


import java.util.*;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.ex.IAutoscalingPolicy;
import org.cloudbus.cloudsim.ex.MonitoringBorkerEX;
import org.cloudbus.cloudsim.ex.disk.HddCloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.ex.disk.HddVm;
import org.cloudbus.cloudsim.ex.util.CustomLog;
import org.cloudbus.cloudsim.ex.vm.VMStatus;
import org.cloudbus.cloudsim.ex.web.ILoadBalancer;
import org.cloudbus.cloudsim.ex.web.workload.brokers.WebBroker;

public class SimpleAutoScalingPolicy implements IAutoscalingPolicy {
    private final double scaleUpCPUTrigger;
    private final double scaleDownCPUTrigger;
    private final double coolDownPeriod;
    private long appId;
    private StringBuilder debugSB = new StringBuilder();
    private double lastActionTime = -1.0D;

    public SimpleAutoScalingPolicy(long appId, double scaleUpCPUTrigger, double scaleDownCPUTrigger, double coolDownPeriod) {
        if(scaleUpCPUTrigger < scaleDownCPUTrigger) {
            throw new IllegalArgumentException("Scale-up ratio should be greater than scale-down. Provided values: " + scaleUpCPUTrigger + "; " + scaleDownCPUTrigger);
        } else {
            this.scaleUpCPUTrigger = scaleUpCPUTrigger;
            this.scaleDownCPUTrigger = scaleDownCPUTrigger;
            this.coolDownPeriod = coolDownPeriod;
            this.appId = appId;
        }
    }

    public void scale(MonitoringBorkerEX broker) {
        double currentTime = CloudSim.clock();
        boolean performScaling = this.lastActionTime < 0.0D || this.lastActionTime + this.coolDownPeriod < currentTime;
        if(broker instanceof WebBroker) {
            WebBroker webBroker = (WebBroker)broker;
            this.debugSB.setLength(0);
            ILoadBalancer loadBalancer = (ILoadBalancer)webBroker.getLoadBalancers().get(Long.valueOf(this.appId));
            double avgCPU = 0.0D;
            int count = 0;
            HddVm candidateToStop = null;
            Iterator toStop = loadBalancer.getAppServers().iterator();

            while(toStop.hasNext()) {
                HddVm vm = (HddVm)toStop.next();
                if(EnumSet.of(VMStatus.INITIALISING, VMStatus.RUNNING).contains(vm.getStatus())) {
                    avgCPU += vm.getCPUUtil();
                    ++count;
                    candidateToStop = vm;

                    Set<Integer> sessions = webBroker.getSessionsInServer(vm.getId());

                    this.debugSB.append(vm);
                    this.debugSB.append("[").append(vm.getStatus().name()).append("] ");
                    this.debugSB.append(String.format("sessions(%d) ", sessions.size()));
                    this.debugSB.append(String.format("cpu(%.2f) ram(%.2f) cdlts(%d);\t", new Object[]{Double.valueOf(vm.getCPUUtil()), Double.valueOf(vm.getRAMUtil()), Integer.valueOf(vm.getCloudletScheduler().getCloudletExecList().size())}));
                }
            }

            avgCPU = count == 0?0.0D:avgCPU / (double)count;
            CustomLog.printf("Simple-Autoscale(%s) avg-cpu(%.2f): %s", new Object[]{broker, Double.valueOf(avgCPU), this.debugSB});
            if(performScaling && avgCPU > this.scaleUpCPUTrigger) {
                HddVm var13 = ((HddVm)loadBalancer.getAppServers().get(0)).clone(new HddCloudletSchedulerTimeShared());
                loadBalancer.registerAppServer(var13);
                webBroker.createVmsAfter(Arrays.asList(new HddVm[]{var13}), 0.0D);
                this.lastActionTime = currentTime;
                CustomLog.printf("Simple-Autoscale(%s) Scale-Up: New AS VMs provisioned: %s", new Object[]{webBroker.toString(), var13});
            } else if(performScaling && avgCPU < this.scaleDownCPUTrigger && count > 1) {
                List var14 = Arrays.asList(new HddVm[]{candidateToStop});
                webBroker.destroyVMsAfter(var14, 0.0D);
                loadBalancer.getAppServers().removeAll(var14);
                this.lastActionTime = currentTime;
                CustomLog.printf("Simple-Autoscale(%s) Scale-Down: AS VMs terminated: %s, sessions to be killed:", new Object[]{webBroker.toString(), var14.toString(), webBroker.getSessionsInServer(candidateToStop.getId())});
            }
        }

    }
}