package com.studymuse.ui;

import com.studymuse.controller.StudyAgentController;
import com.studymuse.domain.Course;
import com.studymuse.domain.WeakTopicReport;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;

/** F09: shows the ranked weak topics with the AI's diagnosis, plus every topic's accuracy. */
class WeakTopicsPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final transient StudyAgentController controller;
    private final transient WorkspaceContext ctx;
    private final JTextArea area = new JTextArea();

    WeakTopicsPanel(StudyAgentController controller, WorkspaceContext ctx) {
        super(new BorderLayout(4, 4));
        this.controller = controller;
        this.ctx = ctx;
        JButton analyse = new JButton("Find Weak Topics");
        analyse.addActionListener(e -> onAnalyseClicked());
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(analyse);
        top.add(new JLabel("Uses your quiz answers and flashcard ratings for the selected course."));
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        area.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        add(top, BorderLayout.NORTH);
        add(new JScrollPane(area), BorderLayout.CENTER);
    }

    void onAnalyseClicked() {
        Course course = ctx.selectedCourse();
        if (course == null) {
            ctx.showError("Select a course first.");
            return;
        }
        area.setText("Analysing ...");
        ctx.runInBackground("Finding weak topics with " + controller.getLlmName() + " ...",
                () -> controller.getWeakTopics(course.getId()),
                (WeakTopicReport r) -> {
                    area.setText(r.toText());
                    area.setCaretPosition(0);
                },
                () -> area.setText(""));
    }
}
