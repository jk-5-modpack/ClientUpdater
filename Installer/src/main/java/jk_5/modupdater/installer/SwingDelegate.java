package jk_5.modupdater.installer;

/**
 * No description given
 *
 * @author jk-5
 */
public interface SwingDelegate {

    public static final int OK_CANCEL_OPTION = 2;
    public static final int CANCEL_OPTION = 2;
    public static final int ERROR_MESSAGE = 0;
    public static final int INFORMATION_MESSAGE = 1;

    int showConfirmDialog(String message, String title, int type);
    void showMessageDialog(String message, String title, int type);
}
