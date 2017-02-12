package ca.ncct.uottawa.selforg.ant.sim;

import org.apache.commons.lang3.tuple.Pair;
import org.cloudbus.cloudsim.ex.util.CustomLog;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class HHAntOptimizer implements IAntOptimizer {

    private Set<Ant> ants = new HashSet<>();
    private LinkedHashMap<Ant, Nest> antToNest = new LinkedHashMap<>();
    private Random random = new Random();
    private Double maxPher = null;
    private Double optPher = null;

    /*private Function<Integer, Integer> addFunction = x -> x + Math.max(1, random.nextInt(x));
    private Function<Integer, Integer> removeFunction = x -> x - Math.min(x - 1, Math.max(1, random.nextInt(x)));*/
    private Function<Pair<Integer, Double>, Integer> addFunction = x ->
            x.getLeft() + (int) Math.round(
                    x.getLeft() / 2d * random.nextDouble() +
                    (x.getLeft() / 2d * Math.abs(x.getRight() - maxPher) / maxPher));
    private Function<Pair<Integer, Double>, Integer> removeFunction = x ->
            x.getLeft() -  (int) Math.round(
                    x.getLeft() / 2d * random.nextDouble() +
                            (x.getLeft() / 2d * Math.abs(x.getRight() - maxPher) / maxPher));

    public void setPheromones(Double maxPher, Double minPher) {
        this.maxPher = maxPher;
        this.optPher = (maxPher + minPher) / 2;
    }

    public void setAnts(Set<Ant> ants) {
        this.ants.clear();
        this.ants.addAll(ants);
        antToNest.clear();
    }

    @Override
    public int getAddServers() {
        return houseHunt(addFunction);
    }

    @Override
    public int getRemoveServers() {
        return houseHunt(removeFunction);
    }

    private int houseHunt(Function<Pair<Integer, Double>, Integer> generatorFunction) {
        int originalSize = ants.size();

        if (ants.size() % 2 == 1) {
            // odd number of ants, we create an extra fake ant
            ants.add(new Ant(ants.iterator().next()));
        }
        initSolutions(generatorFunction);

        for (Ant ant : antToNest.keySet()) {
            Nest nest = antToNest.get(ant);
            nest.getFitness().put(ant, ant.evaluateFitness(nest, originalSize, maxPher, optPher));
        }

        CustomLog.printf("Ant-HouseHunting init: %s", antToNest);

        while (!endCondition()) {
            recruitmentPhase(originalSize);
        }

        return Math.abs(antToNest.values().iterator().next().getServerCount() - originalSize);
    }

    private boolean endCondition() {
        // end condition is only one nest exists
        return antToNest.values().stream().distinct().collect(Collectors.toList()).size() == 1;
    }

    private void recruitmentPhase(int originalSize) {
        List<Pair<Ant, Ant>> recruitmentAnts = new ArrayList<>();
        Map<Ant, Nest> recruiterNests = new HashMap<>();

        antToNest = sortByValue(antToNest);
        CustomLog.printf("Ant-HouseHunting sorted nests: %s", antToNest);

        while (!antToNest.isEmpty()) {
            double rand = random.nextDouble();
            rand = Math.pow(rand, 0.5);
            int recruitIndex = (int) Math.max(Math.floor(rand * (antToNest.size() + 1)), 1);

            int recruitedIndex = -1;

            while (recruitedIndex == -1 || recruitedIndex == recruitIndex) {
                rand = random.nextDouble();
                rand = Math.pow(rand, 2.0);
                recruitedIndex = (int) Math.max(Math.floor(rand * (antToNest.size() + 1)), 1);
            }

            Ant recruiter = null;
            Ant recruited = null;

            int idx = 1;
            for (Ant ant: antToNest.keySet()) {
                if (idx == recruitIndex) {
                    recruiter = ant;
                }
                if (idx == recruitedIndex) {
                    recruited = ant;
                }
                if (recruiter != null && recruited != null) {
                    break;
                }
                idx++;
            }

            recruitmentAnts.add(Pair.of(recruiter, recruited));
            recruiterNests.put(recruiter, antToNest.get(recruiter));
            antToNest.remove(recruiter);
            antToNest.remove(recruited);
        }

        CustomLog.printf("Ant-HouseHunting recruitment: %s", recruitmentAnts);
        // update nests, recruiter and recruited go to recruiter nest
        for (Pair<Ant, Ant> recruitment : recruitmentAnts) {
            Nest nest = recruiterNests.get(recruitment.getLeft());
            antToNest.put(recruitment.getLeft(), nest);
            antToNest.put(recruitment.getRight(), nest);
            nest.getFitness().put(recruitment.getLeft(), recruitment.getLeft().evaluateFitness(nest, originalSize, maxPher, optPher));
            nest.getFitness().put(recruitment.getRight(), recruitment.getRight().evaluateFitness(nest, originalSize, maxPher, optPher));
        }
    }

    private void initSolutions(Function<Pair<Integer, Double>, Integer> generatorFunction) {
        for (Ant ant : ants) {
            antToNest.put(ant, new Nest());
            antToNest.get(ant).setServerCount(Math.max(1,
                    generatorFunction.apply(Pair.of(ants.size(), ant.getAveragePheromone()))));
        }
    }

    static class Nest implements  Comparable<Nest> {
        private int serverCount;
        private Map<Ant, Double> fitness = new HashMap<>();

        int getServerCount() {
            return serverCount;
        }

        void setServerCount(int serverCount) {
            this.serverCount = serverCount;
        }

        Map<Ant, Double> getFitness() {
            return fitness;
        }

        double getAverageFitness() {
            return fitness.values().stream().mapToDouble(x -> x).sum();
        }

        @Override
        public int compareTo(Nest o) {
            return (int) Math.signum(this.getAverageFitness() - o.getAverageFitness());
        }

        @Override
        public String toString() {
            return "Nest{" +
                    "serverCount=" + serverCount +
                    ", fitness=" + fitness +
                    '}';
        }
    }

    public static <K, V extends Comparable<? super V>> LinkedHashMap<K, V> sortByValue(LinkedHashMap<K, V> map) {
        return map.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue(Collections.reverseOrder()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }
}
