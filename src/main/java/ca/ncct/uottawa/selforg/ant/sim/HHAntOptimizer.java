package ca.ncct.uottawa.selforg.ant.sim;

import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.function.Function;

public class HHAntOptimizer implements IAntOptimizer {

    private Set<Ant> ants = new HashSet<>();
    private Map<Ant, Nest> antToNest = new HashMap<>();
    private Random random = new Random();
    private Double maxPher = null;
    private Double optPher = null;

    /*private Function<Integer, Integer> addFunction = x -> x + Math.max(1, random.nextInt(x));
    private Function<Integer, Integer> removeFunction = x -> x - Math.min(x - 1, Math.max(1, random.nextInt(x)));*/
    private Function<Pair<Integer, Double>, Integer> combinedFunction = x -> new Double(x.getLeft() / 2d * random.nextInt() +
            x.getLeft() / 2d * Math.abs(x.getRight() - maxPher) / maxPher).intValue();

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
        initSolutions(combinedFunction);

        if (antToNest.size() == 1) {
            return antToNest.get(ants.iterator().next()).getServerCount();
        }

        int originalSize = antToNest.size();

        for (Ant ant : antToNest.keySet()) {
            Nest nest = antToNest.get(ant);
            nest.getFitness().put(ant, ant.evaluateFitness(nest, originalSize, maxPher, optPher));
        }


    }

    @Override
    public int getRemoveServers() {
        initSolutions(combinedFunction);
    }

    private void initSolutions(Function<Pair<Integer, Double>, Integer> generatorFunction) {
        for (Ant ant : ants) {
            antToNest.put(ant, new Nest());
            antToNest.get(ant).setServerCount(generatorFunction.apply(Pair.of(ants.size(), ant.getAveragePheromone())));
        }
    }

    static class Nest {
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
    }
}
