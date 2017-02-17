package ca.ncct.uottawa.selforg.ant.sim.logparser;

import java.awt.Color;
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
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.CategoryItemRenderer;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.jfree.data.category.DefaultCategoryDataset;

public class Grapher {
  public static void main(String[] args) throws Exception {
    File[] files = new File(args[0]).listFiles(file -> file.isFile() && file.toString().endsWith(".out"));

    for (File f:files) {
      makeGraph(f);
    }
  }

  private static void makeGraph(File f) throws IOException {
    Path path = f.toPath();
    String basename = FilenameUtils.getBaseName(path.getFileName().toString());
    Path outPath = path.getParent().resolve(basename+".png");

    List<String> lines = Files.readAllLines(path);
    lines.remove(0);
    DefaultCategoryDataset cpuDataset = new DefaultCategoryDataset();
    DefaultCategoryDataset sessionsDataset = new DefaultCategoryDataset();
    for (String line : lines) {
      String[] vals = line.split(",");

      cpuDataset.addValue(Double.valueOf(vals[2]), "CPU usage", Double.valueOf(vals[0]));
      sessionsDataset.addValue(Double.valueOf(vals[3]), "Sessions", Double.valueOf(vals[0]));
    }

    JFreeChart lineChartObject = ChartFactory.createLineChart("CPU usage", "Time (s)", "CPU usage",
        cpuDataset, PlotOrientation.VERTICAL, true, true, false);
    CategoryPlot plot = (CategoryPlot) lineChartObject.getPlot();
    plot.setDataset( 1, sessionsDataset );

    final NumberAxis axis2 = new NumberAxis("Session count");
    axis2.setAutoRangeIncludesZero(false);
    plot.setRangeAxis(1, axis2);
    plot.mapDatasetToRangeAxis(1, 1);

    LineAndShapeRenderer defaultRenderer = (LineAndShapeRenderer) plot.getRenderer(0);
    LineAndShapeRenderer splineRenderer = new LineAndShapeRenderer();
    splineRenderer.setSeriesPaint( 0, new Color( 191, 221, 255 ) );

    plot.setRenderer( 1, defaultRenderer );
    plot.setRenderer( 0, splineRenderer );

    int width = 640; /* Width of the image */
    int height = 480; /* Height of the image */
    ChartUtilities.saveChartAsPNG(outPath.toFile(), lineChartObject, width, height);
  }
}
