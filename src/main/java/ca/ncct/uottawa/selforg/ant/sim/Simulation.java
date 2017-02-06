package ca.ncct.uottawa.selforg.ant.sim;

import org.apache.commons.lang3.tuple.Pair;
import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.ex.IAutoscalingPolicy;
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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;

public class Simulation {

    private static final int refreshTime = 4;
    private static DataItem data = new DataItem(5);

    private static Function<Pair<Long, Properties>, IAutoscalingPolicy> supplierSimple = uid -> new SimpleAutoScalingPolicy(uid.getLeft(), 0.8, 0.1, 150);
    private static Function<Pair<Long, Properties>, IAutoscalingPolicy> supplierAnt = uid -> new AntAutoScalingPolicy(uid.getRight(), uid.getLeft());

    public static void main(String[] args) throws Exception {
        Properties props = new Properties();
        Path basePath = Paths.get(args[0]);
        try (InputStream is = Files.newInputStream(basePath)) {
            props.load(is);
        }

        for (Map.Entry<Object, Object> entry : props.entrySet()) {
            String simName = (String) entry.getKey();
            String[] simulationFiles = ((String) entry.getValue()).split(",");
            String cloudProperties = basePath.getParent().toString() + "/" + simulationFiles[0].trim();
            String workloadProperties = basePath.getParent().toString() + "/" + simulationFiles[1].trim();
            String outputProperties = basePath.getParent().toString() + "/" + simulationFiles[2].trim();
            String antProperties = basePath.getParent().toString() + "/" + simulationFiles[3].trim();

            runSimulation(simName, cloudProperties, workloadProperties, outputProperties, antProperties, supplierSimple, "base");
            runSimulation(simName, cloudProperties, workloadProperties, outputProperties, antProperties, supplierAnt, "ant");
        }
    }

    //private Function<Integer, IAutoscalingPolicy> supplierAnt = uid -> new AntAutoScalingPolicy(antProps, uid);

    private static void runSimulation(String simName, String cloudProperties, String workloadProperties,
                                      String outputProperties, String antProperties,
                                      Function<Pair<Long, Properties>, IAutoscalingPolicy> scalingPolicy,
                                      String type) throws Exception {
        System.err.println("Starting simulation " + simName);
        Properties cloudProps = new Properties();
        try (InputStream is = Files.newInputStream(Paths.get(cloudProperties))) {
            cloudProps.load(is);
        }

        Properties workloadProps = new Properties();
        try (InputStream is = Files.newInputStream(Paths.get(workloadProperties))) {
            workloadProps.load(is);
        }

        Properties antProps = new Properties();
        try (InputStream is = Files.newInputStream(Paths.get(antProperties))) {
            antProps.load(is);
        }

        Properties logProps = new Properties();
        try (InputStream is = Files.newInputStream(Paths.get(outputProperties))) {
            logProps.load(is);
        }
        logProps.setProperty("FilePath", logProps.getProperty("FilePath")+"-"+type+".log");
        CustomLog.configLogger(logProps);

        CloudSim.init(1, Calendar.getInstance(), false);

        Datacenter datacenter0 = createDatacenter("Datacenter_0", intfromProps(cloudProps, "hostCount"));

        WebBroker broker = new WebBroker("Broker", refreshTime,
                intfromProps(workloadProps, "simTime") * 24 * 3600, 1, 5, datacenter0.getId());
        // Step 4: Create virtual machines
        List<Vm> vmlist = getVms(broker, intfromProps(cloudProps, "vmCount"));

        broker.submitVmList(vmlist);

        List<StatWorkloadGenerator> workload = generateWorkloads(workloadProps);
        long appId = broker.getLoadBalancers().entrySet().iterator().next().getValue().getAppId();
        broker.addWorkloadGenerators(workload, appId);
        broker.addAutoScalingPolicy(scalingPolicy.apply(Pair.of(appId, antProps)));
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
        System.err.println("Simulation " + simName + " is finished!");
    }

    private static int intfromProps(Properties workloadProps, String propName) {
        return Integer.parseInt(workloadProps.getProperty(propName));
    }

    private static List<Vm> getVms(WebBroker broker, int appServVmCount) {
        List<Vm> vmlist = new ArrayList<>();

        // VM description
        int mips = 250;
        int ioMips = 200;
        long size = 10000; // image size (MB)
        int ram = 1024*8; // vm memory (MB)
        long bw = 1000;
        int pesNumber = 1; // number of cpus
        String vmm = "Xen"; // VMM name
        List<HddVm> appServList = new ArrayList<>();
        List<HddVm> dbServList = new ArrayList<>();

        for (int i = 0; i < appServVmCount; i++) {
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
            int ram = 1024*32; // host memory (MB)
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

    private static List<StatWorkloadGenerator> generateWorkloads(Properties workload) {
        int scaleFactor = intfromProps(workload, "scaleFactor");
        double nullPoint = 0;
        String[] periods = new String[24];

        for (int i = 0; i < 24; i++)
        {
            periods[i] = String.format("[%d,%d] m=%d std=%d", HOURS[i], HOURS[i + 1], scaleFactor * intfromProps(workload, "m" + i)
                    , intfromProps(workload, "std" + i));
            /*periods[i] = String.format("[%d,%d] m=%d std=%d", HOURS[i], HOURS[i + 1], 1, 0);*/
        }
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
