package ca.ncct.uottawa.selforg.ant.sim;

import com.google.common.base.Functions;
import com.google.common.collect.Ordering;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.function.Function;

public class HHAntOptimizer implements IAntOptimizer {

    private Set<Ant> ants = new HashSet<>();
    private TreeMap<Ant, Nest> antToNest = new ValueComparableMap<>(Ordering.from(new NestComparator()));
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

        recruitmentPhase();
    }

    private void recruitmentPhase() {
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

    class ValueComparableMap<K extends Comparable<K>, V> extends TreeMap<K, V> {
        //A map for doing lookups on the keys for comparison so we don't get infinite loops
        private final Map<K, V> valueMap;

        ValueComparableMap(final Ordering<? super V> partialValueOrdering) {
            this(partialValueOrdering, new HashMap<K, V>());
        }

        private ValueComparableMap(Ordering<? super V> partialValueOrdering,
                                   HashMap<K, V> valueMap) {
            super(partialValueOrdering //Apply the value ordering
                    .onResultOf(Functions.forMap(valueMap)) //On the result of getting the value for the key from the map
                    .compound(Ordering.natural())); //as well as ensuring that the keys don't get clobbered
            this.valueMap = valueMap;
        }

        public V put(K k, V v) {
            if (valueMap.containsKey(k)) {
                //remove the key in the sorted set before adding the key again
                remove(k);
            }
            valueMap.put(k, v); //To get "real" unsorted values for the comparator
            return super.put(k, v); //Put it in value order
        }
    }

    class NestComparator implements Comparator<Nest> {

        @Override
        public int compare(Nest o1, Nest o2) {
            return o1.getFitness();
        }
    }
}
