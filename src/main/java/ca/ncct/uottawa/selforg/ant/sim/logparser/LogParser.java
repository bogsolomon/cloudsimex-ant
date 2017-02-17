package ca.ncct.uottawa.selforg.ant.sim.logparser;


import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class LogParser {

    private static final Pattern TIME_PATTERN = Pattern.compile("\\d*:\\d*:\\d*\\t(?<time>.*?)\\t.*\\d*:.*");
    private static final Pattern CPU_PATTERN = Pattern.compile("\\scpu\\((?<cpuValue>\\d*.\\d*)\\)");
    private static final Pattern SESSION_PATTERN = Pattern.compile("\\ssessions\\((?<sessions>\\d*)\\)");
    private static final Pattern PHER_PATTERN = Pattern.compile("\\spheromone\\(\\d*=(?<pheromone>\\d*.\\d*)\\)");

    public static void main(String[] args) throws IOException {
        File[] files = new File(args[0]).listFiles(file -> file.isFile() && file.toString().endsWith(".log"));

        for (File f : files) {
            processFile(f);
        }
    }

    private static void processFile(File f) throws IOException {
        Path path = f.toPath();
        String basename = FilenameUtils.getBaseName(path.getFileName().toString());
        Path outPath = path.getParent().resolve(basename + ".out");

        List<String> lines = Files.readAllLines(path);
        List<String> outputLines = new ArrayList<>();

        List<String> scale = lines.stream().filter(l -> l.contains("Simple-Autoscale") || l.contains("Ant-Autoscale"))
                .collect(Collectors.toList());

        outputLines.add("Time,Servers,AverageCPU,Sessions,Pheromone");
        scale.forEach(x -> outputLines.add(parseLine(x)));
        Files.write(outPath, outputLines, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static String parseLine(String line) {
        StringBuilder buff = new StringBuilder();
        Matcher matcher = TIME_PATTERN.matcher(line);
        while (matcher.find()) {
            buff.append(matcher.group("time").trim()).append(',');
        }
        matcher = CPU_PATTERN.matcher(line);
        double cpuSum = 0;
        int count = 0;
        while (matcher.find()) {
            cpuSum += Double.valueOf(matcher.group("cpuValue"));
            count++;
        }
        buff.append(count).append(',').append(cpuSum/count).append(',');
        matcher = SESSION_PATTERN.matcher(line);
        double sessCount = 0;
        count = 0;
        while (matcher.find()) {
            sessCount += Double.valueOf(matcher.group("sessions"));
            count++;
        }
        buff.append(sessCount).append(',');
        matcher = PHER_PATTERN.matcher(line);
        double pher = 0;
        count = 0;
        while (matcher.find()) {
            pher += Double.valueOf(matcher.group("pheromone"));
            count++;
        }
        buff.append(pher/count).append(',');
        buff.deleteCharAt(buff.length() - 1);

        return buff.toString();
    }
}
