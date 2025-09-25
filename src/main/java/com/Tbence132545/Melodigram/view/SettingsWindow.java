package com.Tbence132545.Melodigram.view;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.net.URL;
import java.util.Objects;

public class SettingsWindow extends JFrame {
    //Settings should be implemented for:
    //Importing and selecting soundfont files
    //Setting default midi input device so the user is not asked every time they practice
    public SettingsWindow() {

        super("Settings");
        JOptionPane.showMessageDialog(
                this,
                "Not yet implemented!",
                "Settings",
                JOptionPane.INFORMATION_MESSAGE
        );
        setSize(400,300);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    }

}
