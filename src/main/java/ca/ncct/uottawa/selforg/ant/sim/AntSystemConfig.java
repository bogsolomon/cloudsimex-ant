package ca.ncct.uottawa.selforg.ant.sim;

import java.util.Properties;

/**
 * Created by Bogdan on 2/4/2017.
 */
public class AntSystemConfig {
    private final int decayAmount;
    private final int decayRate;
    private final int antWaitTime;
    private final int minMorphLevel;
    private final int antPheromone;
    private final int antHistorySize;
    private final int maxMorphLevel;
    private final double maxBalanceLevel;
    private final double minBalanceLevel;

    public AntSystemConfig(Properties props) {
        decayAmount = Integer.parseInt(props.getProperty("decayAmount"));
        decayRate = Integer.parseInt(props.getProperty("decayRate"));
        antWaitTime = Integer.parseInt(props.getProperty("antWaitTime"));
        antPheromone = Integer.parseInt(props.getProperty("antPheromone"));
        antHistorySize = Integer.parseInt(props.getProperty("antHistorySize"));
        maxMorphLevel = Integer.parseInt(props.getProperty("maxMorphLevel"));
        minMorphLevel = Integer.parseInt(props.getProperty("minMorphLevel"));
        maxBalanceLevel = Double.parseDouble(props.getProperty("maxBalanceLevel"));
        minBalanceLevel = Double.parseDouble(props.getProperty("minBalanceLevel"));
    }

    public int getDecayAmount() {
        return decayAmount;
    }

    public int getDecayRate() {
        return decayRate;
    }

    public int getAntWaitTime() {
        return antWaitTime;
    }

    public int getMinMorphLevel() {
        return minMorphLevel;
    }

    public int getAntPheromone() {
        return antPheromone;
    }

    public int getAntHistorySize() {
        return antHistorySize;
    }

    public int getMaxMorphLevel() {
        return maxMorphLevel;
    }

    public double getMaxBalanceLevel() {
        return maxBalanceLevel;
    }

    public double getMinBalanceLevel() {
        return minBalanceLevel;
    }
}
