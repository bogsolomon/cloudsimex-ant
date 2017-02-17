package ca.ncct.uottawa.selforg.ant.sim.logparser;

import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.io.FilenameUtils;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYSplineRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

public class Grapher {

  public static final int MAX_GRAPH_TIME = 6 * 60 * 60;

  public static void main(String[] args) throws Exception {
    File[] files = new File(args[0]).listFiles(file -> file.isFile() && file.toString().endsWith(".out"));

    for (File f:files) {
      makeGraph(f);
    }
  }

  private static void makeGraph(File f) throws IOException {
    Path path = f.toPath();
    String basename = FilenameUtils.getBaseName(path.getFileName().toString());
    List<String> lines = Files.readAllLines(path);
    lines.remove(0);

    XYSeries cpuSeries = new XYSeries("CPU usage");
    XYSeries sessionsSeries = new XYSeries("Sessions");

    int imageCount = 1;
    double maxTime = MAX_GRAPH_TIME;
    for (String line : lines) {
      String[] vals = line.split(",");
      Double time = Double.valueOf(vals[0]);
      if (time > maxTime) {
        writeChart(cpuSeries, sessionsSeries, basename, imageCount, path, maxTime);
        maxTime += MAX_GRAPH_TIME;
        imageCount++;
        cpuSeries.clear();
        sessionsSeries.clear();
      }

      cpuSeries.add(time, Double.valueOf(Double.valueOf(vals[2]) * 100));
      sessionsSeries.add(time, Double.valueOf(vals[3]));
    }

    // write final image
    writeChart(cpuSeries, sessionsSeries, basename, imageCount, path, maxTime);
  }

  private static void writeChart(XYSeries cpuSeries, XYSeries sessionsSeries,
      String basename, int imageCount, Path path, double maxTime) throws IOException {
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
    plot.setRangeAxis(1, new NumberAxis("Session count"));
    plot.setDomainAxis(new NumberAxis("Time (s)"));
    plot.getDomainAxis().setRange(maxTime - MAX_GRAPH_TIME, maxTime);

    plot.mapDatasetToRangeAxis(0, 0);
    plot.mapDatasetToRangeAxis(1, 1);

    int width = 640; /* Width of the image */
    int height = 480; /* Height of the image */
    Path outPath = path.getParent().resolve(basename + imageCount + ".png");

    JFreeChart chart = new JFreeChart("Simulation Results", Font.getFont(Font.SANS_SERIF), plot, true);
    ChartUtilities.saveChartAsPNG(outPath.toFile(), chart, width, height);
  }
}
