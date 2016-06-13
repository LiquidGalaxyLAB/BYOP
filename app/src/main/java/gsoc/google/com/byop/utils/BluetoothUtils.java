package gsoc.google.com.byop.utils;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;

/**
 * Created by lgwork on 13/06/16.
 */
public class BluetoothUtils {

    public static boolean deviceHasBluetooth() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            return false;
        } else {
            return true;
        }
    }

    public static void ensureBluetoothIsEnabled(Activity activity, BluetoothAdapter bluetoothAdapter) {
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            activity.startActivityForResult(enableBtIntent, Constants.REQUEST_ENABLE_BT);
        }
    }
}

