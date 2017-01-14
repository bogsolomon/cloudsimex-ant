package ca.ncct.uottawa.selforg.ant.sim;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.ex.MonitoringBorkerEX;
import org.cloudbus.cloudsim.ex.disk.*;
import org.cloudbus.cloudsim.ex.util.CustomLog;
import org.cloudbus.cloudsim.ex.web.ILoadBalancer;
import org.cloudbus.cloudsim.ex.web.SimpleDBBalancer;
import org.cloudbus.cloudsim.ex.web.SimpleWebLoadBalancer;
import org.cloudbus.cloudsim.ex.web.WebSession;
import org.cloudbus.cloudsim.ex.web.workload.StatWorkloadGenerator;
import org.cloudbus.cloudsim.ex.web.workload.brokers.SimpleAutoScalingPolicy;
import org.cloudbus.cloudsim.ex.web.workload.brokers.WebBroker;
import org.cloudbus.cloudsim.ex.web.workload.freq.CompositeValuedSet;
import org.cloudbus.cloudsim.ex.web.workload.freq.FrequencyFunction;
import org.cloudbus.cloudsim.ex.web.workload.freq.PeriodicStochasticFrequencyFunction;
import org.cloudbus.cloudsim.ex.web.workload.sessions.ConstSessionGenerator;
import org.cloudbus.cloudsim.ex.web.workload.sessions.ISessionGenerator;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

import static org.cloudbus.cloudsim.Consts.HOUR;
import static org.cloudbus.cloudsim.Consts.DAY;
import static org.cloudbus.cloudsim.ex.web.experiments.ExperimentsUtil.HOURS;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class Simulation {

    private static final int refreshTime = 15;
    private static DataItem data = new DataItem(5);

    public static void main(String[] args) throws Exception {
        Properties props = new Properties();
        try (InputStream is = Files.newInputStream(Paths.get(args[1]))) {
            props.load(is);
        }
        props.put(CustomLog.FILE_PATH_PROP_KEY, args[0]);
        CustomLog.configLogger(props);

        CloudSim.init(1, Calendar.getInstance(), false);

        Datacenter datacenter0 = createDatacenter("Datacenter_0", 1);

        int refreshTime = 5;
        WebBroker broker = new WebBroker("Broker", refreshTime, 2 * 24 * 3600, 1, 30, datacenter0.getId());
        // Step 4: Create virtual machines
        List<Vm> vmlist = getVms(broker, 1);

        broker.submitVmList(vmlist);

        List<StatWorkloadGenerator> workload = generateWorkloads();
        long appId = broker.getLoadBalancers().entrySet().iterator().next().getValue().getAppId();
        broker.addWorkloadGenerators(workload, appId);
        broker.addAutoScalingPolicy(new SimpleAutoScalingPolicy(appId, 0.8, 0.1, 150));
        broker.recordUtilisationPeriodically(15);

        CloudSim.startSimulation();

        // Step 10 : stop the simulation and print the results
        CloudSim.stopSimulation();
        CustomLog.printResults(WebSession.class, broker.getServedSessions());

        for (Map.Entry<Double, Map<Integer, double[]>> e : broker.getRecordedUtilisations()
                .entrySet()) {
            // In the beginning it will be inaccurate ...
            double time = e.getKey();
            if (time < 15) {
                continue;
            }

            double[] vm1Observations = e.getValue().get(vmlist.get(0).getId());

            System.err.printf("Time=%.3f\tVM=%d;\tCPU=%.4f;\n", time, vmlist.get(0).getId(), vm1Observations[0]);
        }

        System.err.println();
        System.err.println("Test: Simulation is finished!");
    }

    private static List<Vm> getVms(WebBroker broker, int appServVmCount) {
        List<Vm> vmlist = new ArrayList<>();

        // VM description
        int mips = 250;
        int ioMips = 200;
        long size = 10000; // image size (MB)
        int ram = 512; // vm memory (MB)
        long bw = 1000;
        int pesNumber = 1; // number of cpus
        String vmm = "Xen"; // VMM name
        List<HddVm> appServList = new ArrayList<>();
        List<HddVm> dbServList = new ArrayList<>();

        for (int i=0; i< appServVmCount ;i++)
        {
            appServList.add(new HddVm("App-Srv", broker.getId(), mips, ioMips, pesNumber,
                    ram, bw, size, vmm, new HddCloudletSchedulerTimeShared(), new Integer[0]));
        }

        dbServList.add(new HddVm("App-Srv", broker.getId(), mips, ioMips, pesNumber,
                ram, bw, size, vmm, new HddCloudletSchedulerTimeShared(), new Integer[0]));

        ILoadBalancer balancer = new SimpleWebLoadBalancer(
                1, "127.0.0.1", appServList, new SimpleDBBalancer(dbServList));
        broker.addLoadBalancer(balancer);

        // add the VMs to the vmList
        vmlist.addAll(balancer.getAppServers());
        vmlist.addAll(balancer.getDbBalancer().getVMs());
        return vmlist;
    }

    /**
     * Creates the datacenter.
     *
     * @param name the name
     * @return the datacenter
     */
    private static Datacenter createDatacenter(String name, int hostCount) throws Exception {
        List<Host> hostList = new ArrayList<>();

        List<Pe> peList = new ArrayList<>();
        List<HddPe> hddList = new ArrayList<>();

        int mips = 1000;
        peList.add(new Pe(0, new PeProvisionerSimple(mips))); // need to store
        peList.add(new Pe(1, new PeProvisionerSimple(mips))); // need to store
        int iops = 1000;
        hddList.add(new HddPe(new PeProvisionerSimple(iops), data));

        for (int i = 0; i < hostCount; i++) {
            int ram = 2048; // host memory (MB)
            long storage = 1000000; // host storage
            int bw = 10000;

            hostList.add(
                    new HddHost(new RamProvisionerSimple(ram),
                            new BwProvisionerSimple(bw), storage, peList, hddList,
                            new VmSchedulerTimeSharedOverSubscription(peList),
                            new VmDiskScheduler(hddList))
                    );
        }

        // 5. Create a DatacenterCharacteristics object that stores the
        // properties of a data center: architecture, OS, list of
        // Machines, allocation policy: time- or space-shared, time zone
        // and its price (G$/Pe time unit).
        String arch = "x86"; // system architecture
        String os = "Linux"; // operating system
        String vmm = "Xen";
        double time_zone = 10.0; // time zone this resource located
        double cost = 3.0; // the cost of using processing in this resource
        double costPerMem = 0.05; // the cost of using memory in this resource
        double costPerStorage = 0.001; // the cost of using storage in this
        // resource
        double costPerBw = 0.0; // the cost of using bw in this resource
        LinkedList<Storage> storageList = new LinkedList<>(); // we are
        // not
        // adding
        // SAN
        // devices by now

        DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
                arch, os, vmm, hostList, time_zone, cost, costPerMem,
                costPerStorage, costPerBw);

        // 6. Finally, we need to create a PowerDatacenter object.
        return new HddDataCenter(name, characteristics, new VmAllocationPolicySimple(hostList), storageList, 0);
    }

    private static List<StatWorkloadGenerator> generateWorkloads() {
        double nullPoint = 0;
        String[] periods = new String[] {
                String.format("[%d,%d] m=6 std=1", HOURS[0], HOURS[5]),
                String.format("(%d,%d] m=20 std=2", HOURS[5], HOURS[6]),
                String.format("(%d,%d] m=40 std=2", HOURS[6], HOURS[7]),
                String.format("(%d,%d] m=50 std=4", HOURS[7], HOURS[8]),
                String.format("(%d,%d] m=80 std=4", HOURS[8], HOURS[9]),
                String.format("(%d,%d] m=100 std=5", HOURS[9], HOURS[12]),
                String.format("(%d,%d] m=50 std=2", HOURS[12], HOURS[13]),
                String.format("(%d,%d] m=90 std=5", HOURS[13], HOURS[14]),
                String.format("(%d,%d] m=100 std=5", HOURS[14], HOURS[17]),
                String.format("(%d,%d] m=80 std=2", HOURS[17], HOURS[18]),
                String.format("(%d,%d] m=50 std=2", HOURS[18], HOURS[19]),
                String.format("(%d,%d] m=40 std=2", HOURS[19], HOURS[20]),
                String.format("(%d,%d] m=20 std=2", HOURS[20], HOURS[21]),
                String.format("(%d,%d] m=6 std=1", HOURS[21], HOURS[24]) };
        return generateWorkload(nullPoint, periods);
    }

    private static List<StatWorkloadGenerator> generateWorkload(final double nullPoint, final String[] periods) {
        int asCloudletLength = 200;
        int asRam = 1;
        int dbCloudletLength = 50;
        int dbRam = 1;
        int dbCloudletIOLength = 50;
        int duration = 200;

        return generateWorkload(nullPoint, periods, asCloudletLength, asRam, dbCloudletLength, dbRam,
                dbCloudletIOLength, duration);
    }

    private static List<StatWorkloadGenerator> generateWorkload(final double nullPoint, final String[] periods,
                                                           final int asCloudletLength,
                                                           final int asRam, final int dbCloudletLength, final int dbRam, final int dbCloudletIOLength,
                                                           final int duration) {
        int numberOfCloudlets = duration / refreshTime;
        numberOfCloudlets = numberOfCloudlets == 0 ? 1 : numberOfCloudlets;

        ISessionGenerator sessGen = new ConstSessionGenerator(asCloudletLength, asRam, dbCloudletLength,
                dbRam, dbCloudletIOLength, duration, numberOfCloudlets, false, data);

        FrequencyFunction freqFun = new PeriodicStochasticFrequencyFunction((double) HOUR, (double) DAY, nullPoint,
                CompositeValuedSet.createCompositeValuedSet(periods));
        return Collections.singletonList(new StatWorkloadGenerator(freqFun, sessGen));
    }
}
