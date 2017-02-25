package ca.ncct.uottawa.selforg.ant.sim.logparser;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.io.FilenameUtils;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.renderer.xy.XYSplineRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

public class Grapher {

    public static final int MAX_GRAPH_TIME = 6 * 60 * 60;

    public static void main(String[] args) throws Exception {
        File[] files = new File(args[0]).listFiles(file -> file.isFile() && file.toString().endsWith(".out"));

        for (File f : files) {
            makeGraph(f);
        }

        File[] fileStats = new File(args[0]).listFiles(file -> file.isFile() && file.toString().endsWith(".stats"));

        for (File f : fileStats) {
            makeStatsGraph(f);
        }

        Properties props = new Properties();
        Path basePath = Paths.get(args[1]);
        try (InputStream is = Files.newInputStream(basePath)) {
            props.load(is);
        }

        for (Map.Entry<Object, Object> entry : props.entrySet()) {
            String simName = (String) entry.getKey();
            String[] simulationFiles = ((String) entry.getValue()).split(",");
            String workloadProperties = basePath.getParent().toString() + "/" + simulationFiles[1].trim();
            Properties workloadProps = new Properties();
            try (InputStream is = Files.newInputStream(Paths.get(workloadProperties))) {
                workloadProps.load(is);
            }

            int scaleFactor = intfromProps(workloadProps, "scaleFactor");
            XYSeries sessionsSeries = new XYSeries("Session load");
            for (int i = 0; i < 24; i++) {
                Integer load = scaleFactor * intfromProps(workloadProps, "m" + i);
                sessionsSeries.add(i, Double.valueOf(load));
            }

            writeLoadChart(sessionsSeries, simName, new File(args[0]).toPath());
        }
    }

    private static void makeStatsGraph(File f) throws IOException {
        Path path = f.toPath();
        String basename = FilenameUtils.getBaseName(path.getFileName().toString());
        List<String> lines = Files.readAllLines(path);

        XYSeries cpuSeries = new XYSeries("CPU usage");
        XYSeries sessionsSeries = new XYSeries("Session/Server");

        int timeCount = 0;

        for (String line : lines) {
            String[] vals = line.split(",");

            cpuSeries.add(timeCount, Double.valueOf(Double.valueOf(vals[0]) * 100));
            sessionsSeries.add(timeCount, Double.valueOf(vals[3]));

            timeCount++;
        }

        // write final image
        writeStatsChart(cpuSeries, sessionsSeries, basename, path);
    }

    private static void makeGraph(File f) throws IOException {
        Path path = f.toPath();
        String basename = FilenameUtils.getBaseName(path.getFileName().toString());
        List<String> lines = Files.readAllLines(path);
        lines.remove(0);

        XYSeries cpuSeries = new XYSeries("CPU usage");
        XYSeries sessionsSeries = new XYSeries("Sessions");
        XYSeries serversSeries = new XYSeries("Servers");

        int imageCount = 1;
        double maxTime = MAX_GRAPH_TIME;
        for (String line : lines) {
            String[] vals = line.split(",");
            Double time = Double.valueOf(vals[0]);
            if (time > maxTime) {
                writeChart(cpuSeries, sessionsSeries, serversSeries, basename, imageCount, path, maxTime);
                maxTime += MAX_GRAPH_TIME;
                imageCount++;
                cpuSeries.clear();
                sessionsSeries.clear();
                serversSeries.clear();
            }

            cpuSeries.add(time, Double.valueOf(Double.valueOf(vals[2]) * 100));
            sessionsSeries.add(time, Double.valueOf(vals[3]));
            serversSeries.add(time, Double.valueOf(vals[1]));
        }

        // write final image
        writeChart(cpuSeries, sessionsSeries, serversSeries, basename, imageCount, path, maxTime);
    }

    private static void writeStatsChart(XYSeries cpuSeries, XYSeries sessionsSeries,
                                        String basename, Path path) throws IOException {
        XYSeriesCollection cpuDataset = new XYSeriesCollection();
        XYSeriesCollection sessionsDataset = new XYSeriesCollection();
        cpuDataset.addSeries(cpuSeries);
        sessionsDataset.addSeries(sessionsSeries);

        XYPlot plot = new XYPlot();
        plot.setDataset(0, cpuDataset);
        plot.setDataset(1, sessionsDataset);

        XYSplineRenderer splinerendererCPU = new XYSplineRenderer();
        splinerendererCPU.setBaseShapesVisible(false);
        plot.setRenderer(0, splinerendererCPU);

        XYSplineRenderer splinerenderer = new XYSplineRenderer();
        splinerenderer.setSeriesFillPaint(0, Color.BLUE);
        splinerenderer.setBaseShapesVisible(false);
        plot.setRenderer(1, splinerenderer);
        plot.setRangeAxis(0, new NumberAxis("CPU usage (%)"));
        plot.setRangeAxis(1, new NumberAxis("Average Session/Server"));
        plot.setDomainAxis(new NumberAxis("Time (Hours)"));

        plot.mapDatasetToRangeAxis(0, 0);
        plot.mapDatasetToRangeAxis(1, 1);

        int width = 640; /* Width of the image */
        int height = 480; /* Height of the image */
        Path outPath = path.getParent().resolve(basename + "stats.png");

        JFreeChart chart = new JFreeChart("Hourly Simulation Results", Font.getFont(Font.SANS_SERIF), plot, true);
        ChartUtilities.saveChartAsPNG(outPath.toFile(), chart, width, height);
    }

    private static void writeChart(XYSeries cpuSeries, XYSeries sessionsSeries, XYSeries serverSeries,
                                   String basename, int imageCount, Path path, double maxTime) throws IOException {
        XYSeriesCollection cpuDataset = new XYSeriesCollection();
        XYSeriesCollection sessionsDataset = new XYSeriesCollection();
        XYSeriesCollection serverDataset = new XYSeriesCollection();
        cpuDataset.addSeries(cpuSeries);
        sessionsDataset.addSeries(sessionsSeries);
        serverDataset.addSeries(serverSeries);

        XYPlot plot = new XYPlot();
        plot.setDataset(0, cpuDataset);
        plot.setDataset(1, sessionsDataset);
        plot.setDataset(2, serverDataset);

        XYSplineRenderer splinerendererCPU = new XYSplineRenderer();
        splinerendererCPU.setBaseShapesVisible(false);
        plot.setRenderer(0, splinerendererCPU);

        XYLineAndShapeRenderer splinerendererServ = new XYLineAndShapeRenderer();
        splinerendererServ.setSeriesPaint(0, Color.BLACK);
        splinerendererServ.setBaseShapesVisible(false);
        splinerendererServ.setSeriesStroke(0, new BasicStroke(2));
        plot.setRenderer(2, splinerendererServ);

        XYSplineRenderer splinerenderer = new XYSplineRenderer();
        splinerenderer.setSeriesPaint(0, Color.BLUE);
        splinerenderer.setBaseShapesVisible(false);
        plot.setRenderer(1, splinerenderer);
        plot.setRangeAxis(0, new NumberAxis("CPU usage (%)"));
        plot.setRangeAxis(1, new NumberAxis("Count"));
        plot.setDomainAxis(new NumberAxis("Time (s)"));
        plot.getDomainAxis().setRange(maxTime - MAX_GRAPH_TIME, maxTime);

        plot.mapDatasetToRangeAxis(0, 0);
        plot.mapDatasetToRangeAxis(1, 1);
        plot.mapDatasetToRangeAxis(2, 1);

        int width = 640; /* Width of the image */
        int height = 480; /* Height of the image */
        Path outPath = path.getParent().resolve(basename + imageCount + ".png");

        JFreeChart chart = new JFreeChart("Simulation Results", Font.getFont(Font.SANS_SERIF), plot, true);
        ChartUtilities.saveChartAsPNG(outPath.toFile(), chart, width, height);
    }

    private static void writeLoadChart(XYSeries sessionsSeries,String basename, Path path) throws IOException {
        XYSeriesCollection sessionsDataset = new XYSeriesCollection();
        sessionsDataset.addSeries(sessionsSeries);

        XYPlot plot = new XYPlot();
        plot.setDataset(0, sessionsDataset);

        XYSplineRenderer splinerendererCPU = new XYSplineRenderer();
        splinerendererCPU.setBaseShapesVisible(false);
        plot.setRenderer(0, splinerendererCPU);

        plot.setRangeAxis(0, new NumberAxis("Average Session/Server"));
        plot.setDomainAxis(new NumberAxis("Time (Hours)"));

        plot.mapDatasetToRangeAxis(0, 0);

        int width = 640; /* Width of the image */
        int height = 480; /* Height of the image */
        Path outPath = path.getParent().resolve(basename + "-workload.png");

        JFreeChart chart = new JFreeChart("Hourly Workload", Font.getFont(Font.SANS_SERIF), plot, true);
        ChartUtilities.saveChartAsPNG(outPath.toFile(), chart, width, height);
    }

    private static int intfromProps(Properties workloadProps, String propName) {
        return Integer.parseInt(workloadProps.getProperty(propName));
    }
}
