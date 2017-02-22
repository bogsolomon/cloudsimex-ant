package ca.ncct.uottawa.selforg.ant.sim.logparser;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class StatsGen {
    private static Map<String, Results> resultsByTest = new HashMap<>();
    private static final int ONE_HOUR = 60*60;

    public static void main(String[] args) throws IOException {
        File[] files = new File(args[0]).listFiles(file -> file.isFile() && file.toString().endsWith(".out"));

        for (File f : files) {
            processFile(f);
        }

        for (Map.Entry<String, Results> entry : resultsByTest.entrySet()) {
            Path outPath = files[0].toPath().getParent().resolve(entry.getKey() + ".stats");

            Files.write(outPath, entry.getValue().getStats(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);

            Path outPath2 = files[0].toPath().getParent().resolve(entry.getKey() + ".stats2");
            List<String> stats2 = new ArrayList<>();

            stats2.add("Scale Up Count:" + entry.getValue().getScaleUpCount());
            stats2.add("Scale Down Count:" + entry.getValue().getScaleDownCount());
            stats2.add("Over provisioned time - 0.1:" + entry.getValue().getOverProvisionedTime(0.1));
            stats2.add("Over provisioned time - 0.2:" + entry.getValue().getOverProvisionedTime(0.2));
            stats2.add("Over provisioned time - 0.3:" + entry.getValue().getOverProvisionedTime(0.3));
            stats2.add("Over provisioned time - 0.4:" + entry.getValue().getOverProvisionedTime(0.4));
            stats2.add("Over provisioned time - 0.5:" + entry.getValue().getOverProvisionedTime(0.5));
            stats2.add("Under provisioned time - 0.7:" + entry.getValue().getUnderProvisionedTime(0.7));
            stats2.add("Under provisioned time - 0.8:" + entry.getValue().getUnderProvisionedTime(0.8));
            stats2.add("Under provisioned time - 0.9:" + entry.getValue().getUnderProvisionedTime(0.9));
            stats2.add("Under provisioned time - 0.95:" + entry.getValue().getUnderProvisionedTime(0.95));

            Files.write(outPath2, stats2, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        }
    }

    private static void processFile(File f) throws IOException {
        Path path = f.toPath();
        String filename = path.getFileName().toString();

        String testKey = filename.substring(0, filename.lastIndexOf("-"));
        Results newRes = resultsByTest.containsKey(testKey) ? resultsByTest.get(testKey) : new Results();

        List<String> lines = Files.readAllLines(path);
        lines.remove(0);
        lines.forEach(x ->newRes.addResult(parseLine(x), Double.valueOf(x.substring(0, x.indexOf(",")))));
        resultsByTest.put(testKey, newRes);
    }

    private static Triple<Double, Double, Double> parseLine(String line) {
        String[] lineSplit = line.split(",");
        return Triple.of(Double.parseDouble(lineSplit[1]), Double.parseDouble(lineSplit[2]), Double.parseDouble(lineSplit[3]));
    }

    private static class Results {
        private static Function<Pair<Double, Double>, Boolean> greaterThanComparison = vals -> vals.getLeft() > vals.getRight();
        private static Function<Pair<Double, Double>, Boolean> lessThanComparison = vals -> vals.getLeft() < vals.getRight();

        List<HourlyResult> hourlyResults = new ArrayList<>();

        void addResult(Triple<Double, Double, Double> results, Double time) {
            int resIndex = (int) (time / ONE_HOUR);
            if (hourlyResults.size() <= resIndex) {
                hourlyResults.add(new HourlyResult());
            }
            hourlyResults.get(resIndex).addResult(results, time);
        }

        List<String> getStats() {
            return hourlyResults.stream().map(HourlyResult::getAverages).collect(Collectors.toList());
        }

        int getScaleUpCount() {
            return getScaleCount(greaterThanComparison);
        }

        int getScaleDownCount() {
            return getScaleCount(lessThanComparison);
        }

        int getScaleCount(Function<Pair<Double, Double>, Boolean> func) {
            int scaleUpCount = 0;
            double lastServCount = hourlyResults.get(0).serverValues.get(0);

            for (HourlyResult res: hourlyResults) {
                for (Double servCount : res.serverValues) {
                    if (func.apply(Pair.of(servCount, lastServCount))) {
                        scaleUpCount++;
                    }
                    lastServCount = servCount;
                }
            }

            return scaleUpCount;
        }

        double getOverProvisionedTime(double threshold) {
            return getTime(lessThanComparison, threshold);
        }

        double getUnderProvisionedTime(double threshold) {
            return getTime(greaterThanComparison, threshold);
        }

        double getTime(Function<Pair<Double, Double>, Boolean> func, double threshold) {
            double totalTime = 0;
            double startTime = -1;
            double currTime = -1;
            double endTimeLast = -1;

            for (HourlyResult res: hourlyResults) {
                int idx = 0;
                for (Double cpuValue : res.cpuValues) {
                    if (res.timeValues.get(idx) < currTime) {
                        endTimeLast = currTime;
                    }
                    currTime = res.timeValues.get(idx);

                    if (res.serverValues.get(idx) != 1.0d) {
                        if (func.apply(Pair.of(cpuValue, threshold))) {
                            if (startTime == -1) {
                                startTime = currTime;
                                endTimeLast = -1;
                            }
                        } else if (startTime != -1) {
                            if (endTimeLast == -1) {
                                totalTime += currTime - startTime;
                            } else {
                                totalTime += endTimeLast - startTime;
                                totalTime += currTime - res.timeValues.get(0);
                            }
                            startTime = -1;
                        }
                    } else {
                        startTime = -1;
                    }
                    idx++;
                }
            }

            return totalTime;
        }
    }

    private static class HourlyResult {
        List<Double> cpuValues = new ArrayList<>();
        List<Double> sessionValues = new ArrayList<>();
        List<Double> serverValues = new ArrayList<>();
        List<Double> timeValues = new ArrayList<>();

        void addResult(Triple<Double, Double, Double> results, Double time) {
            serverValues.add(results.getLeft());
            cpuValues.add(results.getMiddle());
            sessionValues.add(results.getRight());
            timeValues.add(time);
        }

        String getAverages() {
            return String.valueOf(cpuValues.stream().mapToDouble(value -> value).average().orElse(0d)) + ',' +
                    sessionValues.stream().mapToDouble(value -> value).average().orElse(0d) + ',' +
                    serverValues.stream().mapToDouble(value -> value).average().orElse(0d);
        }
    }
}

