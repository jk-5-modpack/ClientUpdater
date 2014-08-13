package jk_5.modupdater.installer;

/**
 * No description given
 *
 * @author jk-5
 */
public class HeadlessDelegate implements SwingDelegate {

    public HeadlessDelegate() {
        System.out.println("Installer running in headless mode");
    }

    @Override
    public int showConfirmDialog(String message, String title, int type) {
        return 0;
    }

    @Override
    public void showMessageDialog(String message, String title, int type) {
        System.out.println(message);
    }
}
