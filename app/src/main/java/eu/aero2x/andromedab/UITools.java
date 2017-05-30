package eu.aero2x.andromedab;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
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

    /**
     * Showing an alert dialog with info from onCreate is needed however this can sometimes create a race condition which crashes the app if we get a response from the server before the activity is ready
     * https://stackoverflow.com/a/4713487/1166266
     * @param context The activity context
     * @param title The title for the alert
     * @param message The alert message
     */
    public static void showAlertDialogSafe(final Context context, final int viewToWaitForID, final String title, final String message) {
        //Supposedly you can create an activity reference from context
        final Activity activity = (Activity) context;
        View targetView = activity.findViewById(viewToWaitForID);
        //Check if we have a valid target and the activity isn't being destroyed under us.
        if (targetView != null && activity.isFinishing() == false) {

            targetView.post(new Runnable() {
                public void run() {
                    AlertDialog alertDialog = new AlertDialog.Builder(context).create();
                    alertDialog.setTitle(title);
                    alertDialog.setMessage(message);
                    alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });
                    alertDialog.show();
                }
            });
        }
    }
}
