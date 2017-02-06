package ca.ncct.uottawa.selforg.ant.sim;

import java.util.Set;

/**
 * Created by Bogdan on 2/5/2017.
 */
public interface IAntOptimizer {
    int getAddServers();
    int getRemoveServers();
    void setAnts(Set<Ant> ants);
}
