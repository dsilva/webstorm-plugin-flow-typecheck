package com.github.dsilva.flowtc;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class Settings implements Configurable {
    @Nls
    @Override
    public String getDisplayName() {
        return "Flow";
    }

    @Nullable
    @Override
    public String getHelpTopic() {
        return null;
    }

    private JTextField pathField;

    @Nullable
    @Override
    public JComponent createComponent() {
        JPanel panel = new JPanel(new BorderLayout());
        JLabel label = new JLabel("Flow location");
        label.setVerticalAlignment(SwingConstants.TOP);
        panel.add(label, BorderLayout.PAGE_START);


        pathField = new JTextField(readPath(), 100);
        JLabel pathLabel = new JLabel("Flow executable: ");
        pathLabel.setLabelFor(pathField);

        panel.setLayout(new GridBagLayout());

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.anchor = GridBagConstraints.EAST;
        constraints.gridwidth = GridBagConstraints.RELATIVE;
        constraints.fill = GridBagConstraints.NONE;
        constraints.weightx = 0.0;
        panel.add(pathLabel, constraints);

        constraints.gridwidth = GridBagConstraints.REMAINDER;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1.0;
        panel.add(pathField, constraints);

        return panel;
    }

    @Override
    public boolean isModified() {
        return pathField != null && !readPath().equals(pathField.getText());
    }

    @Override
    public void apply() throws ConfigurationException {
        if (pathField != null) {
            String path = pathField.getText();
            writePath(path);
        }
    }

    private static String KEY = "dsilva.flow.path";
    private static String DEFAULT = "/usr/local/bin/flow";

    private static void writePath(String path) {
        PropertiesComponent props = PropertiesComponent.getInstance();
        props.setValue(KEY, path);
    }

    public static String readPath() {
        PropertiesComponent props = PropertiesComponent.getInstance();
        return props.getValue(KEY, DEFAULT);
    }

    @Override
    public void reset() {
        if (pathField != null) {
            pathField.setText(readPath());
        }
    }

    @Override
    public void disposeUIResources() {
        if (pathField != null) {
            pathField = null;
        }
    }
}
