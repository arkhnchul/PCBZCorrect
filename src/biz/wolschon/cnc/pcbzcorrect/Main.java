package com.company;
        import java.awt.BorderLayout;
        import java.awt.Component;
        import java.awt.Dimension;
        import java.awt.GridLayout;
        import java.awt.HeadlessException;
        import java.awt.event.ActionEvent;
        import java.awt.event.ActionListener;
        import java.awt.event.WindowAdapter;
        import java.awt.event.WindowEvent;
        import java.awt.geom.Rectangle2D;
        import java.io.BufferedReader;
        import java.io.BufferedWriter;
        import java.io.File;
        import java.io.FileReader;
        import java.io.FileWriter;
        import java.io.IOException;
        import java.io.OutputStreamWriter;
        import java.io.PrintWriter;
        import java.io.StringWriter;
        import java.text.DecimalFormat;
        import java.text.DecimalFormatSymbols;
        import java.text.MessageFormat;
        import java.text.NumberFormat;
        import java.util.Locale;
        import java.util.StringTokenizer;
        import java.util.regex.Matcher;
        import java.util.regex.Pattern;
        import javax.swing.JButton;
        import javax.swing.JCheckBox;
        import javax.swing.JComboBox;
        import javax.swing.JFileChooser;
        import javax.swing.JFrame;
        import javax.swing.JLabel;
        import javax.swing.JOptionPane;
        import javax.swing.JPanel;
        import javax.swing.JScrollPane;
        import javax.swing.JTextArea;
        import javax.swing.JTextField;
        import javax.swing.filechooser.FileFilter;
        import org.apache.commons.io.FilenameUtils;

        import static java.lang.Math.round;

public class Main {
    private static final double MINVALUE = 1.0E-4D;
    private static final double MARGIN = 0.0D;
    private static final double IMPERIAL_TO_SANITY_CONVERSION_FACTOR = 25.4D;
    private static final int STARTVARRANGE = 100;
    private static final String UNIT_INCH = "Inch";
    private static final String UNIT_MM = "mm";
    private static String unit = null;
    private static final NumberFormat format;
    private static JTextArea textarea;
    private static JCheckBox checkboxMach3;
    private static JCheckBox checkboxConvert;
    private static JTextField inputGridX;
    private static JTextField inputGridY;
    private static JFrame gui;
    private static JComboBox selectBox;
    private static boolean mach3;
    private static int xsteps;
    private static int ysteps;
    private static boolean convertToMetric;

    public Main() {
    }

    private static void initGUI(final String[] args) {
        gui = new JFrame();
        gui.setTitle("PCBZCorrect V1.02");
        BorderLayout mainLayout = new BorderLayout();
        gui.setLayout(mainLayout);
        textarea = new JTextArea();
        JScrollPane scroller = new JScrollPane(textarea);
        scroller.setMinimumSize(new Dimension(500, 300));
        scroller.setPreferredSize(new Dimension(500, 300));
        gui.getContentPane().add(scroller, "South");
        JButton start = new JButton("start");
        start.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent arg0) {
                Main.mach3 = !Main.checkboxMach3.isSelected();
                Main.convertToMetric = Main.checkboxConvert.isSelected();
                Main.xsteps = Integer.parseInt(Main.inputGridX.getText());
                Main.ysteps = Integer.parseInt(Main.inputGridY.getText());
                Main.doWork(args, true);
            }
        });
        JPanel north = new JPanel();
        north.setLayout(new GridLayout(5, 2));
        inputGridX = new JTextField("10");
        inputGridY = new JTextField("5");
        north.add(new JLabel("Probe grid X"));
        north.add(inputGridX);
        north.add(new JLabel("Probe grid Y"));
        north.add(inputGridY);
        checkboxMach3 = new JCheckBox();
        north.add(new JLabel("EMC2 instead of MACH3"));
        north.add(checkboxMach3);
        checkboxConvert = new JCheckBox();
        north.add(new JLabel("Convert to metric (if needed)"));
        north.add(checkboxConvert);
        String[] items = new String[]{"Metric", "Imperial"};
        selectBox = new JComboBox(items);
        selectBox.setSelectedIndex(0);
        north.add(new JLabel("Units"));
        north.add(selectBox);
        gui.getContentPane().add(north, "North");
        gui.getContentPane().add(start, "Center");
        File infile = new File(args[0]);
        log("File: " + infile.getAbsolutePath());
        gui.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        });
        gui.pack();
        gui.setVisible(true);
    }

    public static void main(String[] args) {
        boolean graphical = false;

        try {
            if(args.length == 0) {
                System.out.println("input: g-code for milling a PCB");
                System.out.println("program asks for Z-height of PCB at different points");
                System.out.println("output: g-code for milling a PCB with z=0 being the surface of the uneven/warped PCB");
                System.out.println("usage: java -jar pcbzcorrect <in.gcode>");
                JFileChooser e = new JFileChooser();
                String t = (new File("")).getAbsolutePath();
                e.setCurrentDirectory(new File(t));
                e.setFileFilter(new FileFilter() {
                    public String getDescription() {
                        return "g-code";
                    }

                    public boolean accept(File file) {
                        String name = file.getName().toLowerCase();
                        return name.endsWith(".gcode") || name.endsWith(".ngc") || name.endsWith(".tap") || name.endsWith(".txt") || file.isDirectory();
                    }
                });
                if(e.showOpenDialog((Component)null) != 0) {
                    JOptionPane.showMessageDialog((Component)null, "aborted");
                    return;
                }

                args = new String[]{e.getSelectedFile().getAbsolutePath()};
                graphical = true;
                initGUI(args);
            } else {
                doWork(args, graphical);
            }
        } catch (HeadlessException var4) {
            var4.printStackTrace();
        }

    }

    private static void doWork(String[] args, boolean graphical) {
        File infile = new File(args[0]);
        log("determining dimensions of " + infile.getName() + "...");
        Rectangle2D max = null;

        try {
            max = getMaxDimensions(infile, 0.0D);
        } catch (Exception var24) {
            logError("cannot determine maximum dimensions");
            PrintWriter fileNameWithOutExt = new PrintWriter(new OutputStreamWriter(System.err));
            var24.printStackTrace(fileNameWithOutExt);
            fileNameWithOutExt.flush();
            fileNameWithOutExt.close();
            if(graphical) {
                JOptionPane.showMessageDialog((Component)null, "cannot determine maximum dimensions [" + var24.getClass().getName() + "] " + var24.getMessage());
            }

            return;
        }

        String msg = "dimensions with  margins : (" + max.getMinX() + "," + max.getMinY() + unit + ") - " + "(" + max.getMaxX() + "," + max.getMaxY() + unit + ")" + "(width=" + max.getWidth() + ", height=" + max.getHeight() + unit + ")";
        log(msg);

        String fileExt;
        String var26;
        try {
            String outfile = infile.getAbsolutePath();
            var26 = FilenameUtils.removeExtension(outfile);
            fileExt = FilenameUtils.getExtension(infile.getAbsolutePath());
        } catch (Exception var23) {
            log("Error get file name:" + var23.getMessage());
            return;
        }

        log("New file: " + var26 + "_zprobed." + fileExt);
        File var27 = new File(var26 + "_zprobed." + fileExt);
        if(var27.exists()) {
            log("overwriting output file!");
            var27.delete();
        }

        log("Modifying g-code. Output to " + var27.getName() + "...");

        BufferedWriter out;
        try {
            out = new BufferedWriter(new FileWriter(var27));
        } catch (IOException var22) {
            logError("cannot open output file " + var27.getAbsolutePath());
            StringWriter maxdist = new StringWriter();
            PrintWriter exout = new PrintWriter(maxdist);
            var22.printStackTrace(exout);
            exout.flush();
            exout.close();
            logError(maxdist.toString());
            return;
        }

        String newline = System.getProperty("line.separator");
        double var28 = distance(max.getMinX(), max.getMinY(), max.getMaxX(), max.getMaxY()) / 6.0D;

        StringWriter sw;
        PrintWriter exout1;
        try {
            out.write("(Things you can change:)");
            out.write(newline);
            if(args.length != 0 || selectBox.getSelectedIndex() == 0) {
                out.write("#1=20 (Safe height)");
                out.write(newline);
                out.write("#2=2 (Travel height)");
                out.write(newline);
                out.write("#3=0 (Z offset)");
                out.write(newline);
                out.write("#4=-1 (Probe depth)");
                out.write(newline);
                out.write("#5=25 (Probe plunge feedrate)");
                out.write(newline);
                out.write("");
                out.write(newline);
                out.write("(Things you should not change:)");
                out.write(newline);
                out.write("G21 (mm)");
                out.write(newline);
            } else {
                out.write("#1=1 (Safe height)");
                out.write(newline);
                out.write("#2=0.5 (Travel height)");
                out.write(newline);
                out.write("#3=0 (Z offset)");
                out.write(newline);
                out.write("#4=-1 (Probe depth)");
                out.write(newline);
                out.write("#5=25 (Probe plunge feedrate)");
                out.write(newline);
                out.write("");
                out.write(newline);
                out.write("(Things you should not change:)");
                out.write(newline);
                out.write("G20 (inch)");
                out.write(newline);
            }

            out.write("G90 (Abs coords)");
            out.write(newline);
            out.write("");
            out.write(newline);
            out.write("M05 (Stop Motor)");
            out.write(newline);
            out.write("G00 Z[#1] (Safe height)");
            out.write(newline);
            out.write("G00 X0 Y0 (.. on the ranch)");
            out.write(newline);
            out.write("");
            out.write(newline);

            for(int e = 0; e < xsteps; ++e) {
                int var29 = 0;
                byte var30 = 1;
                if(e % 2 == 1) {
                    var29 = ysteps - 1;
                    var30 = -1;
                }

                for(int yi = var29; yi < ysteps && yi >= 0; yi += var30) {
                    int arrayIndex = 100 + e + xsteps * yi;
                    double xLocation = getXLocation(e, xsteps, max);
                    double yLocation = getYLocation(yi, ysteps, max);
                    out.write("(PROBE[" + e + "," + yi + "] " + format.format(xLocation) + " " + format.format(yLocation) + " -> " + arrayIndex + ")");
                    out.write(newline);
                    out.write("G00 X" + format.format(xLocation) + " Y" + format.format(yLocation) + " Z[#2]");
                    out.write(newline);
                    if(mach3) {
                        out.write("G31 Z[#4] F[#5]");
                        out.write(newline);
                        out.write("#" + arrayIndex + "=#2002");
                        out.write(newline);
                    } else {
                        out.write("G38.2 Z[#4] F[#5]");
                        out.write(newline);
                        out.write("#" + arrayIndex + "=#5063");
                        out.write(newline);
                    }

                    out.write("G00 Z[#2]");
                    out.write(newline);
                }
            }

            out.write("( PROBING DONE, remove probe now, then press CYCLE START)");
            out.write(newline);
            out.write("M0");
            out.write(newline);
        } catch (IOException var25) {
            logError("cannot write header for g-code");
            sw = new StringWriter();
            exout1 = new PrintWriter(sw);
            var25.printStackTrace(exout1);
            exout1.flush();
            exout1.close();
            logError(sw.toString());
            return;
        }

        try {
            ModifyGCode(infile, out, max, xsteps, ysteps, var28);
        } catch (IOException var21) {
            logError("cannot modify g-code");
            sw = new StringWriter();
            exout1 = new PrintWriter(sw);
            var21.printStackTrace(exout1);
            exout1.flush();
            exout1.close();
            logError(sw.toString());
            return;
        }

        log("done!");
        if(graphical) {
            JOptionPane.showMessageDialog((Component)null, "done!");
            System.exit(0);
        }

    }

    private static void logError(String message) {
        System.err.println(message);
        if(textarea != null) {
            textarea.setText("ERROR: " + textarea.getText() + "\n" + message);
        }

    }

    private static void log(String message) {
        System.out.println(message);
        if(textarea != null) {
            textarea.setText(textarea.getText() + "\n" + message);
        }

    }

    private static double distance(double x1, double y1, double x2, double y2) {
        double xdist = x2 - x1;
        double ydist = y2 - y1;
        return Math.sqrt(xdist * xdist + ydist * ydist);
    }

    private static void ModifyGCode(File infile, BufferedWriter out, Rectangle2D max, int xsteps, int ysteps, double maxdistance) throws IOException {
        BufferedReader in = new BufferedReader(new FileReader(infile));
        String line = null;
        String newline = System.getProperty("line.separator");
        Double currentX = null;
        Double currentY = null;
        Double oldX = null;
        Double oldY = null;
        double lastZ = 1.7976931348623157E308D;

        while((line = in.readLine()) != null) {
            Pattern p = Pattern.compile("\\w-{0,1}[\\d|\\.]+");
            Matcher m = p.matcher(line);
            StringBuilder outline = new StringBuilder();

            boolean found = false;
            boolean foundZ = false;

            try {
                double xdist;
                while(m.find()) {
                    String e = m.group();
                    if(e.startsWith("G21") && convertToMetric) {
                        e = e.replaceAll("G21", "G20");
                    } else if(e.startsWith("X")) {
                        oldX = currentX;
                        currentX = Double.valueOf(convert(Double.parseDouble(e.substring(1))));
                        e = "X{0}";
                        found = true;
                    } else if(e.startsWith("Y")) {
                        oldY = currentY;

                        try {
                            currentY = Double.valueOf(convert(Double.parseDouble(e.substring(1))));
                        } catch (Exception var30) {
                            log(line);
                            log(var30.getMessage());
                        }

                        e = "Y{1}";
                        found = true;
                    } else if(e.startsWith("F") && convertToMetric) {
                        oldY = currentY;
                        xdist = convert(Double.parseDouble(e.substring(1)));
                        e = "F" + format.format(xdist);
                    } else if(e.startsWith("Z")) {
                        lastZ = convert(Double.parseDouble(e.substring(1)));
                        if(currentX != null && currentY != null) {
                            e = "Z{2}";
                        } else if(lastZ < 0.0D) {
                            logError("Code contains a Z value < 0 before the first X or Y value.");
                            logError("Writing unchanged Z value for this location");
                        }

                        foundZ = true;
                    }

                    outline.append(e).append(' ');
                    if(outline.length() > 100) {
                        logError("line too long: \'" + outline.toString() + "\'");
                        System.exit(-2);
                    }
                }

                if(lastZ <= 0.0D && oldX != null && oldY != null && found && distance(currentX.doubleValue(), currentY.doubleValue(), oldX.doubleValue(), oldY.doubleValue()) >= maxdistance) {
                    int var32 = (int)Math.ceil(distance(currentX.doubleValue(), currentY.doubleValue(), oldX.doubleValue(), oldY.doubleValue()) / maxdistance);
                    out.write("( BROKEN UP INTO " + var32 + " MOVEMENTS )");
                    out.write(newline);
                    xdist =         currentX.doubleValue() - oldX.doubleValue();
                    double ydist =  currentY.doubleValue() - oldY.doubleValue();

                    for(int i = 1; i < var32 + 1; ++i) {
                        double xinterpolated = Math.rint(100000.0 * (oldX.doubleValue() + (double)i * xdist / (double)var32) ) / 100000.0;
                        double yinterpolated = Math.rint(100000.0 * (oldY.doubleValue() + (double)i * ydist / (double)var32) ) / 100000.0;
                        writeGCodeLine(max, xsteps, ysteps, out, newline, Double.valueOf(xinterpolated), Double.valueOf(yinterpolated), lastZ, outline, found, foundZ);
                    }
                } else {
                    writeGCodeLine(max, xsteps, ysteps, out, newline, currentX, currentY, lastZ, outline, found, foundZ);
                }
            } catch (NumberFormatException var31) {
                var31.printStackTrace();
            }
        }

        out.close();
        in.close();
    }

    private static void writeGCodeLine(Rectangle2D max, int xsteps, int ysteps, BufferedWriter out, String newline, Double currentX, Double currentY, double lastZ, StringBuilder outline, boolean found, boolean foundZ) throws IOException {
        String changedZ;
        if(!found && !foundZ) {
            out.write(outline.toString());
        } else {
            changedZ = format.format(lastZ);
            String xstr = "";
            String ystr = "";
            if(currentX != null && currentY != null) {
                changedZ = "[" + changedZ + " + #3 + " + getInterpolatedZ(currentX.doubleValue(), currentY.doubleValue(), max, xsteps, ysteps) + "]";
                xstr = format.format(currentX);
                ystr = format.format(currentY);
            }

            String formated = MessageFormat.format(outline.toString(), new Object[]{xstr, ystr, changedZ});
            out.write(formated);
        }

        if(found && !foundZ && lastZ < 1.7976931348623157E308D) {
            changedZ = "[" + format.format(lastZ) + " + #3 + " + getInterpolatedZ(currentX.doubleValue(), currentY.doubleValue(), max, xsteps, ysteps) + "]";
            out.write("Z" + changedZ);
        }

        out.write(newline);
    }

    private static String getInterpolatedZ(double lastX, double lastY, Rectangle2D max, int xsteps, int ysteps) {
        double xlength = lastX - max.getMinX();
        double ylength = lastY - max.getMinY();
        double xstep = max.getWidth() / (double)(xsteps - 1);
        double ystep = max.getHeight() / (double)(ysteps - 1);
        if(Math.abs(xlength) < 1.0E-4D) {
            xlength = 0.0D;
        }

        if(Math.abs(ylength) < 1.0E-4D) {
            ylength = 0.0D;
        }

        if(xlength < 0.0D) {
            throw new IllegalArgumentException("xlength(=" + xlength + "=lastX(" + lastX + ")-minX(" + max.getMinX() + ")) < 0");
        } else if(xlength > max.getWidth()) {
            throw new IllegalArgumentException("xlength(=" + xlength + "=lastX(" + lastX + ")-minX(" + max.getMinX() + ")) > width(=" + max.getWidth() + ")");
        } else if(ylength < 0.0D) {
            throw new IllegalArgumentException("ylength(=" + ylength + ") < 0");
        } else if(ylength > max.getHeight()) {
            throw new IllegalArgumentException("ylength(=" + ylength + ") > height");
        } else {
            int xindex = (int)Math.floor(xlength / xstep);
            double xfactor = (xlength - (double)xindex * xstep) / xstep;
            int yindex = (int)Math.floor(ylength / ystep);
            double yfactor = (ylength - (double)yindex * ystep) / ystep;
            if(xindex >= xsteps) {
                throw new IllegalArgumentException("xindex(=" + xindex + "=" + "floor(xlength=" + xlength + " / xstep=" + xstep + ")" + ") >= xsteps(=" + xsteps + ")");
            } else {
                String x1;
                if(yindex == ysteps - 1) {
                    x1 = linearInterpolateX(xindex, yindex, xfactor, 1.0D, xsteps, ysteps);
                    return x1;
                } else if(yfactor < 0.0D) {
                    throw new IllegalArgumentException("yfactor < 0");
                } else if(yfactor > 1.0D) {
                    throw new IllegalArgumentException("yfactor > 1");
                } else {
                    x1 = linearInterpolateX(xindex, yindex, xfactor, 1.0D - yfactor, xsteps, ysteps);
                    String x2 = linearInterpolateX(xindex, yindex + 1, xfactor, yfactor, xsteps, ysteps);
                    return x1 + " + " + x2;
                }
            }
        }
    }

    private static String linearInterpolateX(int xindex, int yindex, double xfactor, double yFactor, int xsteps, int ysteps) {
        if(xfactor < 0.0D) {
            throw new IllegalArgumentException("xfactor < 0");
        } else if(xfactor > 1.0D) {
            throw new IllegalArgumentException("xfactor > 1");
        } else if(xindex >= xsteps) {
            throw new IllegalArgumentException("xindex(=" + xindex + ") >= xsteps(=" + xsteps + ")");
        } else if(yindex >= ysteps) {
            throw new IllegalArgumentException("yindex(=" + yindex + ") >= ysteps(=" + ysteps + ")");
        } else {
            int leftIndex = 100 + xindex + yindex * xsteps;
            if(xindex == xsteps - 1) {
                return format.format(yFactor) + "*" + "#" + leftIndex;
            } else {
                int rightIndex = 100 + xindex + 1 + yindex * xsteps;
                return format.format(xfactor * yFactor) + " * " + "#" + rightIndex + " + " + format.format((1.0D - xfactor) * yFactor) + " * " + "#" + leftIndex;
            }
        }
    }

    private static double getXLocation(int xindex, int xsteps, Rectangle2D dimensions) {
        double stepLength = dimensions.getWidth() / (double)(xsteps - 1);
        return dimensions.getMinX() + stepLength * (double)xindex;
    }

    private static double getYLocation(int yindex, int ysteps, Rectangle2D dimensions) {
        double stepLength = dimensions.getHeight() / (double)(ysteps - 1);
        return dimensions.getMinY() + stepLength * (double)yindex;
    }

    private static Rectangle2D getMaxDimensions(File infile, double margins) throws IOException {
        BufferedReader in = new BufferedReader(new FileReader(infile));
        String line = null;
        double maxX = 4.9E-324D;
        double minX = 1.7976931348623157E308D;
        double maxY = 4.9E-324D;
        double minY = 1.7976931348623157E308D;

        while((line = in.readLine()) != null) {
            line = line.toUpperCase();


            if(line.startsWith("G20")) {
                unit = "Inch";
            }

            if(line.contains("G20")) {
                unit = "Inch";
            }
            if(line.contains("G21")) {
                unit = "mm";
            }

            if(line.startsWith("G21")) {
                unit = "mm";
            }

            Pattern p = Pattern.compile("\\w-{0,1}[\\d|\\.]+");
            Matcher m = p.matcher(line);

            while(m.find()) {
                String token = m.group();

                try {
                    Double marginY;
                    if(token.startsWith("X")) {
                        marginY = Double.valueOf(Double.parseDouble(token.substring(1)));
                        maxX = Math.max(maxX, marginY.doubleValue());
                        minX = Math.min(minX, marginY.doubleValue());
                    } else if(token.startsWith("Y")) {
                        marginY = Double.valueOf(Double.parseDouble(token.substring(1)));
                        maxY = Math.max(maxY, marginY.doubleValue());
                        minY = Math.min(minY, marginY.doubleValue());
                    }
                } catch (NumberFormatException var26) {
                    logError(var26.getMessage());
                }
            }
        }

        in.close();
        double marginX1 = (maxX - minX) * margins;
        double marginY1 = (maxY - minY) * margins;
        double minXmargin = minX + marginX1;
        double maxXmargin = maxX - marginX1;
        double minYmargin = minY + marginY1;
        double maxYmargin = maxY - marginY1;
        java.awt.geom.Rectangle2D.Double max = new java.awt.geom.Rectangle2D.Double(minXmargin, minYmargin, maxXmargin - minXmargin, maxYmargin - minYmargin);
        return max;
    }

    private static double convert(double distance) {
        return convertToMetric && unit.equals("Inch")?distance * 25.4D:distance;
    }

    static {
        format = new DecimalFormat("###.#####", new DecimalFormatSymbols(Locale.US));
        mach3 = true;
        xsteps = 10;
        ysteps = 5;
        convertToMetric = false;
    }
}
