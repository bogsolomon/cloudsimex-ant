package ca.ncct.uottawa.selforg.ant.sim;

import org.apache.commons.collections4.map.LinkedMap;
import org.apache.commons.lang3.tuple.Pair;
import org.cloudbus.cloudsim.ex.disk.HddVm;

import java.util.*;
import java.util.stream.Collectors;

public class Ant implements Comparable<Ant> {

    //contains server id, time since visit, pheromone level
    private LinkedHashMap<Integer, Double> antMemory = new LinkedHashMap<>();
    private final Map<HddVm, Pair<Integer, Double>> visitHistory = new HashMap<>();
    private final int uid;
    private final AntSystemConfig config;
    private int waitTime = 0;
    private HddVm nextNode = null;

    public Ant(int id, AntSystemConfig config) {
        uid = id;
        this.config = config;
    }

    public Ant(Ant next) {
        config = next.config;
        uid = -1;
        antMemory.putAll(next.antMemory);
    }

    // result is - next server id, sleep time before jumping, new pheromone level at current server
    Double controlStep(HddVm currentVM, Double pherLevel, Double fuzzyFactor, List<HddVm> knownServers, double timePassed) {
        if (waitTime > 0) {
            waitTime -= timePassed;
            return null;
        } else {
            Double newPheromone = calculatePheromone(fuzzyFactor) + pherLevel;
            antMemory.put(currentVM.getId(), newPheromone);
            if (antMemory.size() > config.getAntHistorySize()) {
                Iterator<Integer> iterator = antMemory.keySet().iterator();
                iterator.next();
                iterator.remove();
            }
            waitTime = updateTables(currentVM, newPheromone, fuzzyFactor, knownServers);
            nextNode = jumpNextNode(currentVM, knownServers);

            newPheromone = Math.min(config.getMaxMorphLevel() * 1.5, newPheromone);
            newPheromone = Math.max(0, newPheromone);

            return newPheromone;
        }
    }

    private double calculatePheromone(Double fuzzyFactor) {
        if (config.getMinBalanceLevel() <= fuzzyFactor && fuzzyFactor <= config.getMaxBalanceLevel()) {
            return 0.5 * config.getAntPheromone();
        } else if (config.getMinBalanceLevel() > fuzzyFactor) {
            return (0.5 + (config.getMinBalanceLevel() - fuzzyFactor)) * config.getAntPheromone();
        } else {
            return (0.5 - (fuzzyFactor - config.getMaxBalanceLevel())) * config.getAntPheromone();
        }
    }

    private int updateTables(HddVm currentVM, Double newPheromoneValue, Double fuzzyFactor, List<HddVm> knownServers) {
        Random rand = new Random();
        Double waitTime = Math.ceil(Math.min(60, config.getAntWaitTime() / (1 - fuzzyFactor)));
        Pair<Integer, Double> maxWait = Pair.of(0, 0d);
        if (!visitHistory.isEmpty()) {
            maxWait = visitHistory.entrySet().stream()
                    .max((entry1, entry2) -> entry1.getValue().getLeft() - entry2.getValue().getLeft()).get().getValue();
        }

        // update visit table
        visitHistory.replaceAll((k, v) -> {
            if (k.equals(currentVM)) {
                return Pair.of(0, newPheromoneValue);
            } else {
                return Pair.of(v.getLeft() + waitTime.intValue(), v.getRight());
            }
        });

        List<HddVm> unknown = knownServers.stream().filter(server -> !visitHistory.containsKey(server)).
                collect(Collectors.toList());

        int nextWait;
        if (maxWait.getLeft() == 0) {
            nextWait = waitTime.intValue();
        } else {
            nextWait = rand.nextInt(maxWait.getLeft()) + waitTime.intValue();
        }

        for (HddVm unknownServ : unknown) {
            if (unknownServ.equals(currentVM)) {
                visitHistory.put(unknownServ, Pair.of(0, newPheromoneValue));
            } else {
                visitHistory.put(unknownServ, Pair.of(nextWait, 0d));
            }
        }

        return waitTime.intValue();
    }

    private HddVm jumpNextNode(HddVm currentVM, List<HddVm> knownServers) {
        Random rand = new Random();
        int sumOfTimes = 0;
        Double sumOfPheromones = 0d;
        LinkedMap<HddVm, Double> probTable = new LinkedMap<>();

        if (knownServers.size() <= 1) {
            return currentVM;
        }

        for (Map.Entry<HddVm, Pair<Integer, Double>> serverDatum : visitHistory.entrySet()) {
            if (knownServers.contains(serverDatum.getKey()) && !serverDatum.getKey().equals(currentVM)) {
                sumOfTimes += serverDatum.getValue().getLeft();
                sumOfPheromones += serverDatum.getValue().getRight();
            }
        }

        for (Map.Entry<HddVm, Pair<Integer, Double>> serverDatum : visitHistory.entrySet()) {
            if (knownServers.contains(serverDatum.getKey()) && !serverDatum.getKey().equals(currentVM)) {
                Double randValue = ((serverDatum.getValue().getLeft().doubleValue() / sumOfTimes) + (serverDatum.getValue().getRight() / sumOfPheromones)) / 2;
                if (sumOfPheromones == 0) {
                    randValue = (serverDatum.getValue().getLeft().doubleValue() / sumOfTimes);
                }
                probTable.put(serverDatum.getKey(), randValue);
            }
        }

        probTable = probTable.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue(/*Collections.reverseOrder()*/))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedMap::new
                ));

        Double random = rand.nextDouble();
        Double sumOfProbs = 0d;
        Double lastProb = 0d;

        for (Map.Entry<HddVm, Double> prob : probTable.entrySet()) {
            if (lastProb < random && random < sumOfProbs + prob.getValue()) {
                return prob.getKey();
            } else {
                sumOfProbs += prob.getValue();
                lastProb = prob.getValue();
            }
        }

        return probTable.lastKey();
    }

    HddVm getNextNode() {
        return nextNode;
    }

    Morph morph() {
        Double sum = antMemory.values().stream().mapToDouble(x -> x).sum();

        if ((sum / antMemory.size()) < config.getMinMorphLevel()) {
            return Morph.MaxMorph;
        } else if  ((sum / antMemory.size()) > config.getMaxMorphLevel()) {
            return Morph.MinMorph;
        } else {
            return Morph.NoMorph;
        }
    }

    Double getMorphValue() {
        Double sum = antMemory.values().stream().mapToDouble(x -> x).sum();

        return sum / antMemory.size();
    }

    Double evaluateFitness(HHAntOptimizer.Nest nest, int originalSize, double maxPher, double optimalPher) {
        double scaleFactor = nest.getServerCount() / (double) originalSize;

        double avgPher;

        if (scaleFactor < 1) {
            // server removal case
            int scaledRemainingServers = Double.valueOf(antMemory.size() * scaleFactor).intValue();

            double remainingPher = 0;

            Iterator<Double> iter = antMemory.values().iterator();

            for (int i = 0; i < scaledRemainingServers; i++) {
                remainingPher += iter.next();
            }
            
            avgPher = remainingPher / antMemory.size();
        } else {
            // server addition
            double scaledAddedServers = (nest.getServerCount() - (double) originalSize) / nest.getServerCount();
            int scaledBackHistory = Double.valueOf(scaledAddedServers * antMemory.size()).intValue();

            double totalPher = 0;
            double addPher = 0;

            Iterator<Double> iter = antMemory.values().iterator();

            for (int i = 0; i < scaledBackHistory; i++) {
                addPher += optimalPher;
            }
            for (int i = scaledBackHistory; i < antMemory.size(); i++) {
                totalPher += iter.next();
            }

            avgPher = (totalPher + addPher) / scaledAddedServers;
        }

        return 1 - ((optimalPher - avgPher) / optimalPher);
    }

    double getAveragePheromone() {
        return antMemory.values().stream().mapToDouble(x -> x).sum()/antMemory.size();
    }

    @Override
    public int compareTo(Ant o) {
        return getUid() - o.getUid();
    }

    @Override
    public String toString() {
        return "Ant{" +
                "antMemory=" + antMemory +
                ", visitHistory=" + visitHistory +
                ", uid=" + getUid() +
                '}';
    }

    public int getUid() {
        return uid;
    }

    /*public void reinit() {
        antMemory.clear();
    }*/
}
