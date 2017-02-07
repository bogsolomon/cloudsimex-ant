package ca.ncct.uottawa.selforg.ant.sim;

import java.util.*;
import java.util.function.Function;

public class HHAntOptimizer implements IAntOptimizer {

    private Set<Ant> ants = new HashSet<>();
    private Map<Ant, Nest> antToNest = new HashMap<>();
    private Random random = new Random();
    private Function<Integer, Integer> addFunction = x -> x + Math.max(1, random.nextInt(x));
    private Function<Integer, Integer> removeFunction = x -> x - Math.min(x - 1, Math.max(1, random.nextInt(x)));

    public void setAnts(Set<Ant> ants) {
        this.ants.clear();
        this.ants.addAll(ants);
        antToNest.clear();
    }

    @Override
    public int getAddServers() {
        initSolutions(addFunction);

        if (antToNest.size() == 1) {
            return antToNest.get(ants.iterator().next()).getServerCount();
        }

        for (Ant ant : antToNest.keySet()) {
            Nest nest = antToNest.get(ant);
            nest.getFitness().put(ant, ant.evaluateFitness(nest));
        }
    }

    @Override
    public int getRemoveServers() {
        initSolutions(removeFunction);
    }

    private void initSolutions(Function<Integer, Integer> generatorFunction) {
        for (Ant ant : ants) {
            antToNest.put(ant, new Nest());
            antToNest.get(ant).setServerCount(generatorFunction.apply(ants.size()));
        }
    }

    protected static class Nest {
        private int serverCount;
        private Map<Ant, Double> fitness = new HashMap<>();

        public int getServerCount() {
            return serverCount;
        }

        public void setServerCount(int serverCount) {
            this.serverCount = serverCount;
        }

        public Map<Ant, Double> getFitness() {
            return fitness;
        }
    }
}
