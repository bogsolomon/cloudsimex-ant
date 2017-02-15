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
    private static final Pattern SESSION_PATTERN = Pattern.compile("\\sssessions\\((?<sessions>\\d*.\\d*)\\)");

    public static void main(String[] args) throws IOException {
        Path outputFolderPath = Paths.get(args[1]);

        File[] files = new File(args[0]).listFiles(File::isFile);

        for (File f : files) {
            processFile(f, outputFolderPath);
        }
    }

    private static void processFile(File f, Path outputFolderPath) throws IOException {
        Path path = f.toPath();
        String basename = FilenameUtils.getBaseName(path.getFileName().toString());
        Path outPath = f.toPath().resolve(basename + ".out");

        List<String> lines = Files.readAllLines(path);
        List<String> outputLines = new ArrayList<>();

        List<String> scale = lines.stream().filter(l -> l.contains("Simple-Autoscale") || l.contains("Ant-Autoscale"))
                .collect(Collectors.toList());

        scale.forEach(x -> outputLines.add(parseLine(x)));
        Files.write(outPath, outputLines, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
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
        int sessCount = 0;
        count = 0;
        while (matcher.find()) {
            sessCount += Integer.valueOf(matcher.group("sessions"));
            count++;
        }
        buff.append((double) sessCount/count).append(',');

        return buff.toString();
    }
}
