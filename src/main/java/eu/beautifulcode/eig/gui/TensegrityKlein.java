/*
 * Copyright (C)2008 Gerald de Jong - GNU General Public License
 * please see the LICENSE.TXT in this distribution for more details.
 */
package eu.beautifulcode.eig.gui;

import com.sun.opengl.util.Screenshot;
import eu.beautifulcode.eig.jogl.EllipsoidPainter;
import eu.beautifulcode.eig.jogl.Floor;
import eu.beautifulcode.eig.jogl.GLRenderer;
import eu.beautifulcode.eig.jogl.GLViewPlatform;
import eu.beautifulcode.eig.jogl.IntervalLabelPainter;
import eu.beautifulcode.eig.jogl.LinePainter;
import eu.beautifulcode.eig.jogl.PointOfView;
import eu.beautifulcode.eig.structure.Fabric;
import eu.beautifulcode.eig.structure.Interval;
import eu.beautifulcode.eig.structure.Physics;
import eu.beautifulcode.eig.structure.Span;
import eu.beautifulcode.eig.structure.Vertebra;
import eu.beautifulcode.eig.structure.Vertical;
import eu.beautifulcode.eig.transform.AboveFloor;
import eu.beautifulcode.eig.transform.ConnectVertebra;
import eu.beautifulcode.eig.transform.GrowVertebra;

import javax.media.opengl.GL;
import javax.media.opengl.GLCanvas;
import javax.swing.BorderFactory;
import javax.swing.BoundedRangeModel;
import javax.swing.DefaultBoundedRangeModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class TensegrityKlein extends Frame {
    private static final float LIGHT_POSITION[] = {1f, 0.1f, 2f, 0.5f};
    private Vertical vertical = new Vertical();
    private Physics physics = new Physics(vertical);
    private GLCanvas canvas = new GLCanvas();
    private Floor floor = new Floor();
    private PointOfView pointOfView = new PointOfView(10);
    private Queue<Runnable> jobs = new ConcurrentLinkedQueue<Runnable>();
    private Positioner positioner = new Positioner(jobs, pointOfView);
    private DefaultBoundedRangeModel timeModel = new DefaultBoundedRangeModel();
    private DoubleRangeModel gravityModel = new DoubleRangeModel(vertical.getAirGravity(), 100);
    private DoubleRangeModel dragModel = new DoubleRangeModel(vertical.getAirDrag(), 10);
    private DoubleRangeModel subGravityModel = new DoubleRangeModel(vertical.getLandGravity(), 100);
    private DoubleRangeModel subDragModel = new DoubleRangeModel(vertical.getLandDrag(), 10);
    private DoubleRangeModel ringSpan = new DoubleRangeModel(new IdealLength(Interval.Role.RING_CABLE, 0.6), 2);
    private DoubleRangeModel counterSpan = new DoubleRangeModel(new IdealLength(Interval.Role.COUNTER_CABLE, 0.4), 2);
    private DoubleRangeModel barSpan = new DoubleRangeModel(new IdealLength(Interval.Role.BAR, 1.7), 2);
    private DoubleRangeModel horizontalSpan = new DoubleRangeModel(new IdealLength(Interval.Role.HORIZONTAL_CABLE, 1.3), 2);
    private DoubleRangeModel verticalSpan = new DoubleRangeModel(new IdealLength(Interval.Role.VERTICAL_CABLE, 1.65), 2);
    private DoubleRangeModel springSpan = new DoubleRangeModel(new IdealLength(Interval.Role.RING_SPRING, 1.3), 2);
    private Map<Interval.Role, Physics.Value> spanMap = new TreeMap<Interval.Role, Physics.Value>();
    private Fabric fabric;
    private boolean running = true;
    private boolean physicsActive = true;
    private boolean recordMovie;
    private int step;

    public TensegrityKlein() {
        super("Tensegrity Demo");
//        floor.setMiddle(pointOfView.getFocus());
        canvas.setFocusable(true);
        GLViewPlatform viewPlatform = new GLViewPlatform(new Renderer(), pointOfView, 1, 180);
        canvas.addGLEventListener(viewPlatform);
        canvas.requestFocus();
        canvas.addKeyListener(new KeyHandler());
        canvas.addKeyListener(positioner.getKeyListener());
        canvas.addMouseListener(positioner.getMouseListener());
        canvas.addMouseMotionListener(positioner.getMouseMotionListener());
        canvas.addMouseWheelListener(positioner.getMouseWheelListener());
        add(canvas, BorderLayout.CENTER);
        add(createControlPanel(), BorderLayout.EAST);
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        setSize(screenSize.width, screenSize.height);
        wireUp();
        spanMap.put(Interval.Role.RING_CABLE, ringSpan.getPhysicsValue());
        spanMap.put(Interval.Role.COUNTER_CABLE, counterSpan.getPhysicsValue());
        spanMap.put(Interval.Role.BAR, barSpan.getPhysicsValue());
        spanMap.put(Interval.Role.RING_SPRING, springSpan.getPhysicsValue());
        spanMap.put(Interval.Role.HORIZONTAL_CABLE, horizontalSpan.getPhysicsValue());
        spanMap.put(Interval.Role.VERTICAL_CABLE, verticalSpan.getPhysicsValue());
    }

    private JPanel createControlPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridy = 0;
        gbc.gridx = 0;
        gbc.weightx = 1;
        p.add(createCylinderPanel(), gbc);
        gbc.gridy++;
        p.add(createPhysicsPanel(), gbc);
//        gbc.gridy++;
//        p.add(createMoviePanel(), gbc);
        createButton("Center", p, gbc, new Runnable() {
            public void run() {
                fabric.addTransformation(new AboveFloor(1));
                fabric.executeTransformations(physics);
            }
        });
        createButton("Freeze/Thaw", p, gbc, new Runnable() {
            public void run() {
                physicsActive = !physicsActive;
            }
        });
        gbc.gridwidth = 1;
        p.setPreferredSize(new Dimension(300, 600));
        return p;
    }

    private JPanel createPhysicsPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createTitledBorder("Physics"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridy = 0;
        timeModel.setMinimum(0);
        timeModel.setValue(10);
        timeModel.setMaximum(50);
        physics.setIterations(timeModel.getValue());
        createSlider("Time", timeModel, p, gbc, new Runnable() {
            public void run() {
                physics.setIterations(timeModel.getValue());
            }
        });
        createSlider("Gravity", gravityModel, p, gbc);
        createSlider("Drag", dragModel, p, gbc);
        createSlider("SubGravity", subGravityModel, p, gbc);
        createSlider("SubDrag", subDragModel, p, gbc);
        return p;
    }

    private JPanel createCylinderPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createTitledBorder("Create"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridy = 0;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        createCylinderButton(10, 16, p, gbc);
        createCylinderButton(16, 24, p, gbc);
        createCylinderButton(24, 24, p, gbc);
        createCylinderButton(30, 60, p, gbc);
        gbc.gridwidth = 1;
        createSlider("ringSpan", ringSpan, p, gbc);
        createSlider("counterSpan", counterSpan, p, gbc);
        createSlider("barSpan", barSpan, p, gbc);
        createSlider("horizontalSpan", horizontalSpan, p, gbc);
        createSlider("verticalSpan", verticalSpan, p, gbc);
        createSlider("springSpan", springSpan, p, gbc);
        return p;
    }

    private void createCylinderButton(final int girth, final int length, JPanel p, GridBagConstraints gbc) {
        createButton(String.format("%d x %d", girth, length), p, gbc, new Runnable() {
            public void run() {
                jobs.add(new KleinBuilder(girth, length));
            }
        });
    }

    private JPanel createMoviePanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createTitledBorder("Movie"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridy = 0;
        gbc.weightx = 1;
        createButton("Record", p, gbc, new Runnable() {
            public void run() {
                recordMovie = true;
            }
        });
        createButton("Stop", p, gbc, new Runnable() {
            public void run() {
                recordMovie = false;
            }
        });
        return p;
    }

    private void createButton(String name, JPanel p, GridBagConstraints gbc, final Runnable job) {
        JButton button = new JButton(name);
        button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                jobs.add(job);
            }
        });
        gbc.gridy++;
        p.add(button, gbc);
    }

    private void createSlider(String title, final DoubleRangeModel model, JPanel p, GridBagConstraints gbc) {
        createSlider(title, model.getModel(), p, gbc, new Runnable() {
            public void run() {
                model.modelToValue();
            }
        });
    }

    private void createField(String title, final IntHolder intHolder, JPanel p, GridBagConstraints gbc) {
        gbc.gridwidth = 1;
        gbc.gridx = 0;
        gbc.gridy++;
        final String initialValue = String.valueOf(intHolder.value);
        final JTextField field = new JTextField(initialValue, 10);
        JLabel label = new JLabel(title);
        label.setLabelFor(field);
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent focusEvent) {
                try {
                    intHolder.value = Integer.parseInt(field.getText().trim());
                    System.out.println("set value " + intHolder.value);
                }
                catch (NumberFormatException e) {
                    System.out.println("bad value " + field.getText());
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            field.setText(initialValue);
                        }
                    });
                }
                finally {
                    field.setBackground(String.valueOf(intHolder.value).equals(field.getText().trim()) ? Color.WHITE : Color.YELLOW);
                }
            }
        });
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent documentEvent) {
                checkValue();
            }

            @Override
            public void removeUpdate(DocumentEvent documentEvent) {
                checkValue();
            }

            @Override
            public void changedUpdate(DocumentEvent documentEvent) {
                checkValue();
            }

            public void checkValue() {
                field.setBackground(String.valueOf(intHolder.value).equals(field.getText().trim()) ? Color.WHITE : Color.YELLOW);
            }
        });
        p.add(label, gbc);
        gbc.gridx++;
        p.add(field, gbc);
        gbc.gridx = 0;
    }

    private JSlider createSlider(String title, final BoundedRangeModel model, JPanel p, GridBagConstraints gbc, final Runnable runnable) {
        gbc.gridy++;
        gbc.gridx = 0;
        JLabel titleLabel = new JLabel(title);
        p.add(titleLabel, gbc);
        gbc.gridx = 1;
        JSlider slider = new JSlider(model);
        p.add(slider, gbc);
        if (runnable != null) {
            model.addChangeListener(new ChangeListener() {
                public void stateChanged(ChangeEvent event) {
                    jobs.add(runnable);
                }
            });
        }
        return slider;
    }

    private void wireUp() {
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent windowEvent) {
                running = false;
            }
        });
    }

    public void kill() {
        running = false;
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                Thread.currentThread().interrupt();
            }
        });
    }

    private class StressRange implements Span.StressRange {
        public double minimum() {
            return -0.03;
        }

        public double maximum() {
            return 0.03;
        }
    }

    private class KleinBuilder implements Runnable {
        private int length, width;

        private KleinBuilder(int length, int width) {
            this.length = length;
            this.width = width;
        }

        @Override
        public void run() {
            fabric = new Fabric(null);
            GrowVertebra growVertebra = new GrowVertebra(width * 2);
            growVertebra.setSpanMap(spanMap);
            fabric.addTransformation(growVertebra);
            fabric.addTransformation(new AboveFloor(0));
            fabric.executeTransformations(physics);
            new TubeGrower(length).start();
        }
    }

    private class TubeGrower implements Runnable, ActionListener {
        private int length;
        private Timer timer = new Timer(100, this);

        private TubeGrower(int length) {
            this.length = length;
        }

        public void run() {
            if (!fabric.isAnySpanActive()) {
                Vertebra vertebra = fabric.getVertebras().get(fabric.getVertebras().size() - 1);
                GrowVertebra growVertebra = new GrowVertebra(vertebra, false); // false => omega
                growVertebra.setSpanMap(spanMap);
                fabric.addTransformation(growVertebra);
                length--;
                if (length <= 0) {
                    fabric.addTransformation(new RingRemover());
                    Vertebra vertebraA = fabric.getVertebras().get(fabric.getVertebras().size() - 1);
                    Vertebra vertebraB = fabric.getVertebras().get(0);
                    fabric.addTransformation(new ConnectVertebra(vertebraA, vertebraB, true));
                    timer.stop();
                }
            }
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            jobs.add(this);
        }

        public void start() {
            timer.start();
        }
    }

    private class Renderer implements GLRenderer {
        private StressRange stressRange = new StressRange();
        private EllipsoidPainter ellipsoidPainter = new EllipsoidPainter(stressRange);
        private LinePainter linePainter = new LinePainter(stressRange);
        private IntervalLabelPainter intervalLabelPainter = new IntervalLabelPainter(pointOfView);
        private DecimalFormat formatter = new DecimalFormat("00000");
        private int frameNumber;

        public void init(GL gl) {
            ellipsoidPainter.setWidth(0.01);
            intervalLabelPainter.setFeature(IntervalLabelPainter.Feature.ROLE);
        }

        public void display(GL gl, int width, int height) {
            gl.glLightfv(GL.GL_LIGHT0, GL.GL_POSITION, LIGHT_POSITION, 0);
            Fabric f = fabric;
            if (f != null) {
                renderFabric(gl, f);
            }
            while (!jobs.isEmpty()) {
                jobs.remove().run();
            }
            floor.display(gl);
            if (recordMovie) {
                try {
                    Screenshot.writeToTargaFile(new File("/tmp/TK" + formatter.format(frameNumber++) + ".tga"), width, height);
                }
                catch (IOException e) {
                    e.printStackTrace(System.out);
                    recordMovie = false;
                }
            }
            positioner.run();
        }

        void renderFabric(GL gl, Fabric fab) {
            if (physicsActive || step > 0) {
                fab.executeTransformations(physics);
                if (step > 0) {
                    step--;
                }
            }
            ellipsoidPainter.preVisit(gl);
            for (Interval interval : fab.getIntervals()) {
                switch (interval.getRole()) {
                    case BAR:
                    case SPRING:
                    case RING_SPRING:
                        interval.getUnit(true);
                        ellipsoidPainter.visit(interval);
                        break;
                }
            }
            linePainter.preVisit(gl);
            for (Interval interval : fab.getIntervals()) {
                switch (interval.getRole()) {
                    case CABLE:
                    case COUNTER_CABLE:
                    case RING_CABLE:
                    case HORIZONTAL_CABLE:
                    case TEMPORARY:
                    case VERTICAL_CABLE:
                        linePainter.visit(interval);
                        break;
                }
            }
            linePainter.postVisit(gl);
            intervalLabelPainter.preVisit(gl);
            for (Interval interval : fab.getIntervals()) {
                intervalLabelPainter.visit(interval);
            }
        }
    }

    private static void pause(long time) {
        try {
            Thread.sleep(time);
        }
        catch (InterruptedException e) {
            // eat it
        }
    }

    public void iterate() {
        if (!isVisible()) return;
        canvas.display();
    }

    private class KeyHandler extends KeyAdapter {
        public void keyPressed(KeyEvent event) {
            switch (event.getKeyCode()) {
                case KeyEvent.VK_S:
                    step++;
                    break;
            }
        }
    }

    private class RingRemover implements Fabric.Transformation {
        public void transform(Fabric fabric) {
            for (Interval interval : fabric.getIntervals()) {
                if (interval.getRole() == Interval.Role.RING_SPRING) {
                    fabric.getMods().getIntervalMod().remove(interval);
                }
            }
        }
    }

    private class IdealLength implements Physics.Value {
        private Interval.Role role;
        private double value;

        public IdealLength(Interval.Role role, double value) {
            this.role = role;
            this.value = value;
        }

        public String getName() {
            return role.toString();
        }

        public void set(double value) {
            this.value = value;
            jobs.add(new Runnable() {
                public void run() {
                    for (Interval interval : fabric.getIntervals()) {
                        if (interval.getRole() == role) {
                            interval.getSpan().setIdeal(IdealLength.this.value, 0);
                        }
                    }

                }
            });
        }

        public double get() {
            return value;
        }
    }

    private class IntHolder {
        int value;

        private IntHolder(int value) {
            this.value = value;
        }
    }

    static long delay = 10;

    public static void main(String[] args) {
        TensegrityKlein tensegrityKlein = new TensegrityKlein();
        pause(100);
        tensegrityKlein.setVisible(true);
        while (tensegrityKlein.running) {
            pause(delay);
            tensegrityKlein.iterate();
        }
        System.exit(0);
    }
}
