import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.Paint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

class SvgStoneLoader {
  static BufferedImage loadStone(String path) {
    try {
      File file = new File(path);
      if (!file.exists()) {
        return null;
      }
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      dbf.setNamespaceAware(false);
      Document doc = dbf.newDocumentBuilder().parse(file);
      Element root = doc.getDocumentElement();

      int width = parseLength(root.getAttribute("width"), 500);
      int height = parseLength(root.getAttribute("height"), 500);
      BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g2 = image.createGraphics();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      Map<String, LinearGradientDef> linearDefs = new HashMap<>();
      Map<String, RadialGradientDef> radialDefs = new HashMap<>();
      parseGradientDefs(doc, linearDefs, radialDefs);

      NodeList paths = root.getElementsByTagName("path");
      for (int i = 0; i < paths.getLength(); i++) {
        Element pathEl = (Element) paths.item(i);
        drawPath(g2, pathEl, linearDefs, radialDefs);
      }
      g2.dispose();
      return image;
    } catch (Exception e) {
      return tryRasterLoad(path);
    }
  }

  private static BufferedImage tryRasterLoad(String path) {
    try {
      return ImageIO.read(new File(path));
    } catch (Exception ignored) {
      return null;
    }
  }

  private static void parseGradientDefs(
      Document doc,
      Map<String, LinearGradientDef> linearDefs,
      Map<String, RadialGradientDef> radialDefs
  ) {
    NodeList linear = doc.getElementsByTagName("linearGradient");
    for (int i = 0; i < linear.getLength(); i++) {
      Element el = (Element) linear.item(i);
      String id = el.getAttribute("id");
      if (id.isEmpty()) {
        continue;
      }
      NodeList stops = el.getElementsByTagName("stop");
      List<Float> fractions = new ArrayList<>();
      List<Color> colors = new ArrayList<>();
      for (int s = 0; s < stops.getLength(); s++) {
        Element stopEl = (Element) stops.item(s);
        Map<String, String> style = parseStyle(stopEl.getAttribute("style"));
        float offset = parseFraction(stopEl.getAttribute("offset"));
        String colorHex = style.getOrDefault("stop-color", "#000000");
        float alpha = parseFloat(style.getOrDefault("stop-opacity", "1"), 1f);
        Color c = parseColor(colorHex, alpha);
        fractions.add(offset);
        colors.add(c);
      }
      if (!fractions.isEmpty()) {
        linearDefs.put(id, new LinearGradientDef(toFloatArray(fractions), colors.toArray(new Color[0])));
      }
    }

    NodeList radial = doc.getElementsByTagName("radialGradient");
    for (int i = 0; i < radial.getLength(); i++) {
      Element el = (Element) radial.item(i);
      String id = el.getAttribute("id");
      if (id.isEmpty()) {
        continue;
      }
      String href = el.getAttribute("xlink:href");
      if (href.isEmpty()) {
        href = el.getAttribute("href");
      }
      if (href.startsWith("#")) {
        href = href.substring(1);
      }
      float cx = parseFloat(el.getAttribute("cx"), 0f);
      float cy = parseFloat(el.getAttribute("cy"), 0f);
      float fx = parseFloat(el.getAttribute("fx"), cx);
      float fy = parseFloat(el.getAttribute("fy"), cy);
      float r = parseFloat(el.getAttribute("r"), 1f);
      AffineTransform at = parseMatrix(el.getAttribute("gradientTransform"));
      radialDefs.put(id, new RadialGradientDef(href, cx, cy, fx, fy, r, at));
    }
  }

  private static void drawPath(
      Graphics2D g2,
      Element pathEl,
      Map<String, LinearGradientDef> linearDefs,
      Map<String, RadialGradientDef> radialDefs
  ) {
    float cx = parseFloat(pathEl.getAttribute("sodipodi:cx"), Float.NaN);
    float cy = parseFloat(pathEl.getAttribute("sodipodi:cy"), Float.NaN);
    float rx = parseFloat(pathEl.getAttribute("sodipodi:rx"), Float.NaN);
    float ry = parseFloat(pathEl.getAttribute("sodipodi:ry"), Float.NaN);
    if (Float.isNaN(cx) || Float.isNaN(cy) || Float.isNaN(rx) || Float.isNaN(ry)) {
      return;
    }

    Map<String, String> style = parseStyle(pathEl.getAttribute("style"));
    float opacity = parseFloat(style.getOrDefault("opacity", "1"), 1f);
    float fillOpacity = parseFloat(style.getOrDefault("fill-opacity", "1"), 1f);
    float alphaMultiplier = opacity * fillOpacity;

    String fill = style.getOrDefault("fill", "");
    Paint paint = null;
    if (fill.startsWith("url(#") && fill.endsWith(")")) {
      String gradientId = fill.substring(5, fill.length() - 1);
      RadialGradientDef rg = radialDefs.get(gradientId);
      if (rg != null) {
        LinearGradientDef lg = linearDefs.get(rg.linearRefId);
        if (lg != null && lg.fractions.length >= 2) {
          Color[] adjusted = applyAlpha(lg.colors, alphaMultiplier);
          paint = new RadialGradientPaint(
              new Point2D.Float(rg.cx, rg.cy),
              rg.r,
              new Point2D.Float(rg.fx, rg.fy),
              lg.fractions,
              adjusted,
              RadialGradientPaint.CycleMethod.NO_CYCLE,
              RadialGradientPaint.ColorSpaceType.SRGB,
              rg.gradientTransform
          );
        }
      }
    } else if (fill.startsWith("#")) {
      paint = parseColor(fill, alphaMultiplier);
    }

    if (paint == null) {
      return;
    }

    Shape ellipse = new Ellipse2D.Float(cx - rx, cy - ry, rx * 2f, ry * 2f);
    AffineTransform pathTransform = parseMatrix(pathEl.getAttribute("transform"));
    Shape shape = pathTransform.createTransformedShape(ellipse);
    g2.setPaint(paint);
    g2.fill(shape);
  }

  private static int parseLength(String value, int fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    String cleaned = value.replaceAll("[^0-9.\\-]", "");
    if (cleaned.isEmpty()) {
      return fallback;
    }
    try {
      return Math.max(1, Math.round(Float.parseFloat(cleaned)));
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  private static float parseFraction(String offset) {
    if (offset == null || offset.isBlank()) {
      return 0f;
    }
    String s = offset.trim();
    if (s.endsWith("%")) {
      return clamp01(parseFloat(s.substring(0, s.length() - 1), 0f) / 100f);
    }
    return clamp01(parseFloat(s, 0f));
  }

  private static float clamp01(float v) {
    return Math.max(0f, Math.min(1f, v));
  }

  private static float parseFloat(String value, float fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    try {
      return Float.parseFloat(value.trim());
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  private static AffineTransform parseMatrix(String transform) {
    if (transform == null || transform.isBlank()) {
      return new AffineTransform();
    }
    String t = transform.trim();
    if (!t.startsWith("matrix(") || !t.endsWith(")")) {
      return new AffineTransform();
    }
    String[] parts = t.substring(7, t.length() - 1).split(",");
    if (parts.length != 6) {
      return new AffineTransform();
    }
    double[] m = new double[6];
    for (int i = 0; i < 6; i++) {
      try {
        m[i] = Double.parseDouble(parts[i].trim());
      } catch (NumberFormatException e) {
        return new AffineTransform();
      }
    }
    return new AffineTransform(m);
  }

  private static Map<String, String> parseStyle(String style) {
    Map<String, String> map = new HashMap<>();
    if (style == null || style.isBlank()) {
      return map;
    }
    String[] parts = style.split(";");
    for (String part : parts) {
      int idx = part.indexOf(':');
      if (idx <= 0 || idx >= part.length() - 1) {
        continue;
      }
      map.put(part.substring(0, idx).trim(), part.substring(idx + 1).trim());
    }
    return map;
  }

  private static Color parseColor(String hex, float alphaMultiplier) {
    String s = hex.trim();
    if (!s.startsWith("#")) {
      return new Color(0, 0, 0, Math.round(255 * clamp01(alphaMultiplier)));
    }
    if (s.length() == 4) {
      int r = Integer.parseInt(s.substring(1, 2) + s.substring(1, 2), 16);
      int g = Integer.parseInt(s.substring(2, 3) + s.substring(2, 3), 16);
      int b = Integer.parseInt(s.substring(3, 4) + s.substring(3, 4), 16);
      int a = Math.round(255 * clamp01(alphaMultiplier));
      return new Color(r, g, b, a);
    }
    int rgb = Integer.parseInt(s.substring(1), 16);
    int r = (rgb >> 16) & 0xFF;
    int g = (rgb >> 8) & 0xFF;
    int b = rgb & 0xFF;
    int a = Math.round(255 * clamp01(alphaMultiplier));
    return new Color(r, g, b, a);
  }

  private static Color[] applyAlpha(Color[] colors, float alphaMultiplier) {
    Color[] out = new Color[colors.length];
    for (int i = 0; i < colors.length; i++) {
      int a = Math.round(colors[i].getAlpha() * clamp01(alphaMultiplier));
      out[i] = new Color(colors[i].getRed(), colors[i].getGreen(), colors[i].getBlue(), a);
    }
    return out;
  }

  private static float[] toFloatArray(List<Float> values) {
    float[] arr = new float[values.size()];
    for (int i = 0; i < values.size(); i++) {
      arr[i] = values.get(i);
    }
    return arr;
  }

  private record LinearGradientDef(float[] fractions, Color[] colors) {}

  private record RadialGradientDef(
      String linearRefId,
      float cx,
      float cy,
      float fx,
      float fy,
      float r,
      AffineTransform gradientTransform
  ) {}
}
