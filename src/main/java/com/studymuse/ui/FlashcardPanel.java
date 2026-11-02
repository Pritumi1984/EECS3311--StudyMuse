package com.studymuse.ui;

import com.studymuse.controller.StudyAgentController;
import com.studymuse.domain.Course;
import com.studymuse.domain.Deck;
import com.studymuse.domain.Flashcard;
import com.studymuse.domain.Material;
import com.studymuse.services.FlashcardService;
import com.studymuse.services.StudyException;

import javax.swing.BorderFactory;
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
import java.util.ArrayList;
import java.util.List;

/**
 * F05 flashcard generation in the GUI. Generated cards appear in an editable table; the student can
 * fix text or delete rows, and only "Save Deck" stores them.
 */
class FlashcardPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final transient StudyAgentController controller;
    private final transient WorkspaceContext ctx;

    private final JTextField conceptField = new JTextField(14);
    private final JSpinner countSpinner = new JSpinner(
            new SpinnerNumberModel(10, FlashcardService.MIN_CARDS, FlashcardService.MAX_CARDS, 1));
    private final DefaultTableModel tableModel = new DefaultTableModel(new Object[]{"Front (question)", "Back (answer)", "Topic"}, 0);
    private final JTable table = new JTable(tableModel);
    private final JLabel info = new JLabel("Generate cards from the selected material, or type a concept.");
    private final JButton saveButton = new JButton("Save Deck");
    private final JButton deleteButton = new JButton("Delete Selected Card");

    private transient Deck preview;
    private transient List<Flashcard> previewCards = new ArrayList<>();

    FlashcardPanel(StudyAgentController controller, WorkspaceContext ctx) {
        super(new BorderLayout(4, 4));
        this.controller = controller;
        this.ctx = ctx;

        JButton generate = new JButton("Generate Flashcards");
        generate.addActionListener(e -> onGenerateClicked());
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Concept (optional):"));
        top.add(conceptField);
        top.add(new JLabel("Cards:"));
        top.add(countSpinner);
        top.add(generate);

        deleteButton.addActionListener(e -> onDeleteClicked());
        saveButton.addActionListener(e -> onSaveClicked());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(deleteButton);
        buttons.add(saveButton);
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(buttons, BorderLayout.NORTH);
        info.setBorder(BorderFactory.createEmptyBorder(0, 6, 2, 6));
        bottom.add(info, BorderLayout.SOUTH);
        setPreviewButtons(false);

        table.setRowHeight(24);
        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
        setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
    }

    void onGenerateClicked() {
        Course course = ctx.selectedCourse();
        if (course == null) {
            ctx.showError("Select a course first.");
            return;
        }
        String concept = conceptField.getText().trim();
        Material material = ctx.selectedMaterial();
        if (concept.isEmpty() && material == null) {
            ctx.showError("Select a material or type a concept.");
            return;
        }
        String source = concept.isEmpty() ? material.getId() : concept;
        int count = (Integer) countSpinner.getValue();
        info.setText("Generating " + count + " cards ...");
        ctx.runInBackground("Generating flashcards with " + controller.getLlmName() + " ...",
                () -> controller.generateFlashcards(course.getId(), source, count),
                (Deck deck) -> showPreview(deck, count),
                () -> info.setText("Generation failed."));
    }

    private void showPreview(Deck deck, int requested) {
        preview = deck;
        previewCards = new ArrayList<>(deck.getCards());
        tableModel.setRowCount(0);
        for (Flashcard c : previewCards) {
            tableModel.addRow(new Object[]{c.getFront(), c.getBack(), c.getTopic()});
        }
        String note = deck.getCards().size() < requested
                ? " Only " + deck.getCards().size() + " valid cards were created (asked for " + requested + ")." : "";
        info.setText("Preview of '" + deck.getName() + "': edit cells or delete rows, then Save Deck." + note);
        setPreviewButtons(true);
    }

    void onDeleteClicked() {
        int row = table.getSelectedRow();
        if (preview == null || row < 0) {
            ctx.showError("Select a card row to delete.");
            return;
        }
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
        preview.removeCard(previewCards.get(row).getId());
        previewCards.remove(row);
        tableModel.removeRow(row);
    }

    void onSaveClicked() {
        Course course = ctx.selectedCourse();
        if (preview == null || course == null) {
            return;
        }
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
        for (int i = 0; i < previewCards.size(); i++) {
            previewCards.get(i).edit(String.valueOf(tableModel.getValueAt(i, 0)).trim(),
                    String.valueOf(tableModel.getValueAt(i, 1)).trim(), String.valueOf(tableModel.getValueAt(i, 2)).trim());
        }
        try {
            Deck saved = controller.saveDeck(course.getId(), preview);
            info.setText("Saved deck '" + saved.getName() + "' with " + saved.getCards().size() + " cards.");
            ctx.setStatus("Saved flashcard deck " + saved);
            preview = null;
            previewCards.clear();
            setPreviewButtons(false);
            ctx.reloadCourses();
        } catch (StudyException e) {
            ctx.showError(e.getMessage());
        }
    }

    private void setPreviewButtons(boolean enabled) {
        saveButton.setEnabled(enabled);
        deleteButton.setEnabled(enabled);
    }
}
