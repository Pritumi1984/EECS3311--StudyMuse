package com.studymuse.ui;

import com.studymuse.controller.StudyAgentController;
import com.studymuse.domain.Course;
import com.studymuse.progress.ProgressEvent;
import com.studymuse.progress.ProgressObserver;
import com.studymuse.progress.ProgressStats;
import com.studymuse.services.StudyException;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.util.List;

/**
 * F11 progress dashboard. Design pattern: OBSERVER (concrete observer).
 * It subscribes to the ProgressTracker through the controller and refreshes itself whenever a quiz is
 * graded, a flashcard is reviewed or a plan session is ticked off - no service ever calls it directly.
 */
class DashboardView extends JPanel implements ProgressObserver {

    private static final long serialVersionUID = 1L;
    private static final Color ACCENT = new Color(37, 99, 235);

    private final transient StudyAgentController controller;
    private final transient WorkspaceContext ctx;
    private final JLabel header = new JLabel();
    private final JLabel[] tiles = new JLabel[5];
    private final TrendChart chart = new TrendChart();
    private final JLabel lastEvent = new JLabel(" ");

    DashboardView(StudyAgentController controller, WorkspaceContext ctx) {
        super(new BorderLayout(8, 8));
        this.controller = controller;
        this.ctx = ctx;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        header.setFont(header.getFont().deriveFont(Font.BOLD, 16f));
        JPanel tileRow = new JPanel(new GridLayout(1, tiles.length, 8, 8));
        for (int i = 0; i < tiles.length; i++) {
            tiles[i] = new JLabel("", SwingConstants.CENTER);
            tiles[i].setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(200, 205, 215)),
                    BorderFactory.createEmptyBorder(8, 6, 8, 6)));
            tileRow.add(tiles[i]);
        }
        JPanel north = new JPanel(new BorderLayout(4, 8));
        north.add(header, BorderLayout.NORTH);
        north.add(tileRow, BorderLayout.CENTER);
        add(north, BorderLayout.NORTH);
        add(chart, BorderLayout.CENTER);
        lastEvent.setForeground(new Color(90, 90, 90));
        add(lastEvent, BorderLayout.SOUTH);
        controller.addProgressObserver(this);
    }

    /** Called by the ProgressTracker (possibly from a background thread). */
    @Override
    public void onProgressUpdated(ProgressEvent event) {
        SwingUtilities.invokeLater(() -> {
            Course c = ctx.selectedCourse();
            if (c != null && c.getId().equals(event.courseId())) {
                lastEvent.setText("Updated automatically: " + event.description());
                refresh();
            }
        });
    }

    /** Reloads the statistics for the selected course. */
    void refresh() {
        Course c = ctx.selectedCourse();
        if (c == null) {
            render(null);
            return;
        }
        try {
            render(controller.getProgress(c.getId()));
        } catch (StudyException e) {
            header.setText(e.getMessage());
        }
    }

    void render(ProgressStats s) {
        if (s == null) {
            header.setText("Select a course to see its progress.");
            for (JLabel t : tiles) {
                t.setText("");
            }
            chart.setScores(List.of());
            return;
        }
        header.setText(s.hasActivity() ? "Progress - " + s.courseName()
                : "No activity yet in " + s.courseName() + ": generate flashcards or take a quiz to get started.");
        tiles[0].setText(tile("Quizzes taken", String.valueOf(s.quizzesTaken())));
        tiles[1].setText(tile("Average score", s.quizzesTaken() == 0 ? "-" : String.format("%.0f%%", s.averageScore())));
        tiles[2].setText(tile("Cards mastered", s.cardsMastered() + " / " + s.cardsTotal()));
        tiles[3].setText(tile("Minutes studied", "~" + s.minutesStudied()));
        tiles[4].setText(tile("Plan sessions", s.planSessionsTotal() == 0 ? "-"
                : s.planSessionsDone() + " / " + s.planSessionsTotal()));
        chart.setScores(s.scoreTrend());
    }

    private static String tile(String label, String value) {
        return "<html><div style='text-align:center'><span style='font-size:18pt;color:#2563eb'><b>" + value
                + "</b></span><br><span style='color:#555'>" + label + "</span></div></html>";
    }

    /** Line chart of the last quiz scores. */
    private static final class TrendChart extends JPanel {
        private static final long serialVersionUID = 1L;
        private transient List<Double> scores = List.of();

        TrendChart() {
            setPreferredSize(new Dimension(400, 260));
            setBackground(Color.WHITE);
            setBorder(BorderFactory.createTitledBorder("Quiz score trend (last 10 attempts)"));
        }

        void setScores(List<Double> s) {
            scores = List.copyOf(s);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int left = 50;
            int top = 30;
            int w = getWidth() - left - 30;
            int h = getHeight() - top - 40;
            g.setColor(new Color(225, 228, 235));
            g.setFont(getFont().deriveFont(11f));
            for (int pct = 0; pct <= 100; pct += 25) {
                int y = top + h - h * pct / 100;
                g.drawLine(left, y, left + w, y);
                g.setColor(new Color(110, 110, 110));
                g.drawString(pct + "%", 14, y + 4);
                g.setColor(new Color(225, 228, 235));
            }
            if (scores.isEmpty()) {
                g.setColor(new Color(120, 120, 120));
                g.drawString("Take a quiz to see your score trend here.", left + 10, top + h / 2);
                return;
            }
            int n = scores.size();
            int prevX = -1;
            int prevY = -1;
            for (int i = 0; i < n; i++) {
                int x = n == 1 ? left + w / 2 : left + i * w / (n - 1);
                int y = top + h - (int) Math.round(h * Math.max(0, Math.min(100, scores.get(i))) / 100.0);
                g.setColor(ACCENT);
                g.setStroke(new BasicStroke(2.5f));
                if (prevX >= 0) {
                    g.drawLine(prevX, prevY, x, y);
                }
                g.fillOval(x - 5, y - 5, 10, 10);
                g.setColor(new Color(60, 60, 60));
                g.drawString(String.format("%.0f%%", scores.get(i)), x - 12, y - 10);
                g.drawString("#" + (i + 1), x - 8, top + h + 18);
                prevX = x;
                prevY = y;
            }
        }
    }
}
