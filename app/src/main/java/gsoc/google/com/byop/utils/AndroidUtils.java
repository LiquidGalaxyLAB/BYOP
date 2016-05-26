package gsoc.google.com.byop.utils;

import android.app.Activity;
import android.widget.Toast;

/**
 * Created by lgwork on 24/05/16.
 */
public class AndroidUtils {
    /**
     * Shows a toast message.
     */
    public static void showMessage(String message,Activity activity) {
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
    }

}
