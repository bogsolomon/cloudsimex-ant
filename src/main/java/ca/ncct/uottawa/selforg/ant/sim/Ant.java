package ca.ncct.uottawa.selforg.ant.sim;

import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.apache.commons.lang3.tuple.Triple;
import org.cloudbus.cloudsim.ex.disk.HddVm;

import java.util.List;

/**
 * Created by Bogdan on 1/31/2017.
 */
public class Ant {

    private final CircularFifoQueue<Triple<Integer, Integer, Double>> antMemory;
    private final int uid;
    private final AntSystemConfig config;

    public Ant(HddVm vm, int id, AntSystemConfig config) {
        antMemory = new CircularFifoQueue<>(config.getAntHistorySize());
        uid = id;
        this.config = config;
        antMemory.add(Triple.of(id, 0, 0d));
    }

    public Triple<Integer, Integer, Double> controlStep(HddVm currentVM, Double pherLevel, Double fuzzyFactor, List<HddVm> knownServers) {
        Double newPheromone = calculatePheromone(fuzzyFactor) + pherLevel;
        antMemory.add(Triple.of(currentVM.getId(), 0, newPheromone);
    }

    private double calculatePheromone(Double fuzzyFactor) {
        if (config.getMinBalanceLevel() <= fuzzyFactor && fuzzyFactor <= config.getMaxBalanceLevel()) {
            return 0.5 * config.getAntPheromone();
        } else if (config.getMinBalanceLevel() > fuzzyFactor) {
            return (0.5 - (config.getMinBalanceLevel() - fuzzyFactor)) * config.getAntPheromone();
        } else {
            return (0.5 + (fuzzyFactor - config.getMaxBalanceLevel())) * config.getAntPheromone();
        }
    }
}
