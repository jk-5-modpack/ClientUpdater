package jk_5.modupdater.installer;

import javax.swing.*;

/**
 * No description given
 *
 * @author jk-5
 */
public class DefaultDelegate implements SwingDelegate {

    public DefaultDelegate() {
        System.out.println("Installer running in swing mode");
    }

    @Override
    public int showConfirmDialog(String message, String title, int type) {
        return JOptionPane.showConfirmDialog(null, message, title, type);
    }

    @Override
    public void showMessageDialog(String message, String title, int type) {
        JOptionPane.showMessageDialog(null, message, title, type);
    }
}
