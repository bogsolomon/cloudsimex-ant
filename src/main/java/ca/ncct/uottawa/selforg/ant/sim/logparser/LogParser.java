package ca.ncct.uottawa.selforg.ant.sim.logparser;


import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class LogParser {

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
        Path outPath = f.toPath().resolve(basename+".out");

        List<String> lines = Files.readAllLines(path);

        //lines.forEach();
    }
}
