package ca.ncct.uottawa.selforg.ant.sim.logparser;

import org.apache.commons.io.FilenameUtils;
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

public class StatsGen {
    static Map<String, Results> resultsByTest = new HashMap<>();
    private static int ONE_HOUR = 60*60;

    public static void main(String[] args) throws IOException {
        File[] files = new File(args[0]).listFiles(file -> file.isFile() && file.toString().endsWith(".out"));

        for (File f : files) {
            processFile(f);
        }

        for (Map.Entry<String, Results> entry : resultsByTest.entrySet()) {
            Path outPath = files[0].toPath().getParent().resolve(entry.getKey() + ".stats");

            Files.write(outPath, entry.getValue().getStats(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
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
        List<HourlyResult> hourlyResults = new ArrayList<>();

        void addResult(Triple<Double, Double, Double> results, Double time) {
            int resIndex = (int) (time / ONE_HOUR);
            if (hourlyResults.size() <= resIndex) {
                hourlyResults.add(new HourlyResult());
            }
            hourlyResults.get(resIndex).addResult(results);
        }

        List<String> getStats() {
            List<String> res = new ArrayList<>();
            for (HourlyResult hRes: hourlyResults) {
                res.add(hRes.getAverages());
            }

            return res;
        }
    }

    private static class HourlyResult {
        List<Double> cpuValues = new ArrayList<>();
        List<Double> sessionValues = new ArrayList<>();
        List<Double> serverValues = new ArrayList<>();

        void addResult(Triple<Double, Double, Double> results) {
            serverValues.add(results.getLeft());
            cpuValues.add(results.getMiddle());
            sessionValues.add(results.getRight());
        }

        String getAverages() {
            StringBuilder strBuilder = new StringBuilder();
            strBuilder.append(cpuValues.stream().mapToDouble(value -> value).average().orElse(0d)).append(',');
            strBuilder.append(sessionValues.stream().mapToDouble(value -> value).average().orElse(0d)).append(',');
            strBuilder.append(serverValues.stream().mapToDouble(value -> value).average().orElse(0d));

            return strBuilder.toString();
        }
    }
}

