package eu.aero2x.andromedab;

import android.support.design.widget.Snackbar;
import android.view.View;

/**
 * Created by Salman on 2/17/17.
 */

public class UITools {

    public static int DATA_NEEDS_REFRESH = 581;

    /**
     * Show a dismissible snackbar
     * @param view On view
     * @param message With Message
     */
    public static void showDismissableSnackBar(View view,String message) {
        final Snackbar snackBar = Snackbar.make(view, message, Snackbar.LENGTH_INDEFINITE);

        snackBar.setAction("Dismiss", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                snackBar.dismiss();
            }
        });
        snackBar.show();
    }

    /**
     * Show a snackbar
     * @param view On view
     * @param message With Message
     * @param duration Snackbar duration
     */
    public static void showSnackBar(View view,String message,int duration) {
        final Snackbar snackBar = Snackbar.make(view, message, duration);
        snackBar.show();
    }
}
