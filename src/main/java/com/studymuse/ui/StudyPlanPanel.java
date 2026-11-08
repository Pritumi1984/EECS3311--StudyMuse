package com.studymuse.ui;

import com.studymuse.controller.StudyAgentController;
import com.studymuse.domain.Course;
import com.studymuse.domain.PlanRequest;
import com.studymuse.domain.StudyDay;
import com.studymuse.domain.StudyPlan;
import com.studymuse.domain.StudySession;
import com.studymuse.services.StudyException;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;

/** F10 "Study Plan" form (exam date, hours per day, topics) and the plan shown day by day in a table. */
class StudyPlanPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final transient StudyAgentController controller;
    private final transient WorkspaceContext ctx;

    private final JTextField examField = new JTextField(LocalDate.now().plusDays(7).toString(), 9);
    private final JSpinner hoursSpinner = new JSpinner(new SpinnerNumberModel(2.0, 0.5, 12.0, 0.5));
    private final JTextField topicsField = new JTextField(24);
    protected final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"Date", "Day", "Activity", "Topic", "Minutes", "Done"}, 0) {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(model);
    protected final JLabel info = new JLabel("Enter your exam date and daily hours. Leave topics empty to use all course topics.");

    StudyPlanPanel(StudyAgentController controller, WorkspaceContext ctx) {
        super(new BorderLayout(4, 4));
        this.controller = controller;
        this.ctx = ctx;
        JButton generate = new JButton("Generate Plan");
        generate.addActionListener(e -> onGenerateClicked());
        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        form.add(new JLabel("Exam date (YYYY-MM-DD):"));
        form.add(examField);
        form.add(new JLabel("Hours/day:"));
        form.add(hoursSpinner);
        JPanel form2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        form2.add(new JLabel("Topics (optional, comma-separated):"));
        form2.add(topicsField);
        form2.add(generate);
        JPanel top = new JPanel(new BorderLayout());
        top.add(form, BorderLayout.NORTH);
        top.add(form2, BorderLayout.SOUTH);
        table.setRowHeight(22);
        table.getColumnModel().getColumn(3).setPreferredWidth(220);
        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(info, BorderLayout.SOUTH);
    }

    void onGenerateClicked() {
        Course course = ctx.selectedCourse();
        if (course == null) {
            ctx.showError("Select a course first.");
            return;
        }
        LocalDate exam;
        try {
            exam = LocalDate.parse(examField.getText().trim());
        } catch (DateTimeParseException e) {
            ctx.showError("Exam date must look like 2026-11-20.");
            return;
        }
        String topics = topicsField.getText().trim();
        PlanRequest req = new PlanRequest(exam, (Double) hoursSpinner.getValue(),
                topics.isEmpty() ? List.of() : Arrays.asList(topics.split(",")));
        info.setText("Planning ...");
        ctx.runInBackground("Finding weak topics and planning with " + controller.getLlmName() + " ...",
                () -> controller.generatePlan(course.getId(), req),
                (StudyPlan p) -> showPlan(p),
                () -> info.setText("Plan generation failed."));
    }

    /** Shows the course's saved plan, if any (called when the selected course changes). */
    void showSavedPlan() {
        Course course = ctx.selectedCourse();
        model.setRowCount(0);
        if (course == null) {
            return;
        }
        try {
            controller.getPlan(course.getId()).ifPresentOrElse(this::showPlan,
                    () -> info.setText("No plan yet for " + course.getName() + "."));
        } catch (StudyException e) {
            info.setText(e.getMessage());
        }
    }

    protected void showPlan(StudyPlan plan) {
        model.setRowCount(0);
        for (StudyDay d : plan.getDays()) {
            for (StudySession s : d.getSessions()) {
                model.addRow(new Object[]{d.getDate().toString(), d.getDate().getDayOfWeek().toString().substring(0, 3),
                        s.getActivity().getLabel(), s.getTopic(), s.getMinutes(), s.isDone() ? "yes" : ""});
            }
        }
        String omitted = plan.getOmittedTopics().isEmpty() ? ""
                : "  Not enough time for: " + String.join(", ", plan.getOmittedTopics()) + ".";
        info.setText("Exam " + plan.getExamDate() + " - " + plan.getDays().size() + " days, " + plan.totalMinutes()
                + " min total (" + plan.getSource() + ")." + omitted);
    }
}
