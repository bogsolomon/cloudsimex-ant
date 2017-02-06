package ca.ncct.uottawa.selforg.ant.sim;

import java.util.Set;

/**
 * Created by Bogdan on 2/5/2017.
 */
public class SimpleAntOptimizer implements IAntOptimizer {
    @Override
    public int getAddServers() {
        return 1;
    }

    @Override
    public int getRemoveServers() {
        return 1;
    }

    @Override
    public void setAnts(Set<Ant> ants) {
        // NO-OP
    }
}
