package ca.ncct.uottawa.selforg.ant.sim;

import java.util.*;
import java.util.function.Function;

public class HHAntOptimizer implements IAntOptimizer {

    private Set<Ant> ants = new HashSet<>();
    private Map<Ant, Integer> antToServerCount = new HashMap<>();
    private Map<Ant, Integer> antToNextId = new HashMap<>();
    private Random random = new Random();
    private Function<Integer, Integer> addFunction = x ->  x + Math.max(1, random.nextInt(x));
    private Function<Integer, Integer> removeFunction = x ->  x - Math.min(x - 1, Math.max(1, random.nextInt(x)));

    public void setAnts(Set<Ant> ants) {
        this.ants.clear();
        this.ants.addAll(ants);
        antToServerCount.clear();
    }

    @Override
    public int getAddServers() {
        initSolutions(addFunction);

        if (antToServerCount.size() == 1) {
            return antToServerCount.get(ants.iterator().next());
        }
    }

    @Override
    public int getRemoveServers() {
        initSolutions(removeFunction);
    }

    private void initSolutions(Function<Integer, Integer> generatorFunction) {
        for (Ant ant : ants) {
            antToServerCount.put(ant, generatorFunction.apply(ants.size()));
        }
    }
}
