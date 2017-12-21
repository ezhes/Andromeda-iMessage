package eu.aero2x.andromedab;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.support.customtabs.CustomTabsIntent;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.github.javiersantos.appupdater.AppUpdater;
import com.github.javiersantos.appupdater.enums.UpdateFrom;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crash.FirebaseCrash;
import com.google.firebase.iid.FirebaseInstanceId;
import com.stfalcon.chatkit.commons.ImageLoader;
import com.stfalcon.chatkit.commons.models.IDialog;
import com.stfalcon.chatkit.commons.models.IMessage;
import com.stfalcon.chatkit.commons.models.IUser;
import com.stfalcon.chatkit.dialogs.DialogsList;
import com.stfalcon.chatkit.dialogs.DialogsListAdapter;
import com.stfalcon.chatkit.utils.DateFormatter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class ContactSelect extends AppCompatActivity {

    private DialogsListAdapter dialogsListAdapter;
    ArrayList<IDialog> conversationList;
    ArrayList<JSONObject> conversationDataSource;
    private String incomingNotificationContact = "";
    private FirebaseAnalytics mFirebaseAnalytics;
    public SocketClient socketClient;

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        //Check if we were notification launched
        Uri data = intent.getData();
        //Do we have a payload AND do we have a contact name?
        if (data != null && data.getLastPathSegment() != null) {
            Log.d("onNewIntent", "Launching with " + data.getLastPathSegment());
            Bundle bundle = new Bundle();
            bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, "View notification");
            mFirebaseAnalytics.logEvent("launch_with_notification", bundle);
            //Store our request and parse out broken characters since that's what the menu is
            incomingNotificationContact = data.getLastPathSegment().replaceAll("[^\\x00-\\x7F]", "");
            //Check if we need to load first if we've launched
            if (conversationList == null || conversationList.size() == 0) {
                Log.w("onNewIntent","We don't have a conversation list! We are currently getting one and we should be called later");
            }else {
                //Force a conversation reload so we actually go into the right one. We don't load twice because of the null check above.
                setupConversations();
            }

        }else {
            Log.d("onNewIntent","Launched without a URI param");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_select);

        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
        //Setup our update from github
        new AppUpdater(this)
                .setUpdateFrom(UpdateFrom.GITHUB)
                .setGitHubUserAndRepo("shusain93", "Andromeda-iMessage").showEvery(5)
                .start();
        System.out.println("Firebase notification token:" + FirebaseInstanceId.getInstance().getToken());
        //Load our config database
        final SharedPreferences sharedPreferences = getSharedPreferences("CONFIG",MODE_PRIVATE);
        //Check if we are not yet setup
        if (sharedPreferences.getString("apiIPEndpoint",null) == null) {
            settingsDialog(true);
        }else {
            //We have already configured
            prepareView();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_conversations, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.resetConfig:
                AlertDialog alertDialog = new AlertDialog.Builder(ContactSelect.this).create();
                alertDialog.setTitle("Are you sure you want to reset the application?");
                alertDialog.setMessage("This will remove the entire application configuration and close the application.");
                alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Reset",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                Bundle bundle = new Bundle();
                                bundle.putString(FirebaseAnalytics.Param.VALUE, "Erased settings");
                                mFirebaseAnalytics.logEvent("app_menu_reset_settings", bundle);
                                SharedPreferences.Editor editor = getSharedPreferences("CONFIG", MODE_PRIVATE).edit();
                                //Nuke all preferences
                                editor.putString("apiIPEndpoint",null);
                                editor.putInt("apiPort",0);
                                editor.putInt("socketPort",0);
                                //Force safe instantly.
                                editor.commit();
                                //Close the activity
                                finishAffinity();
                                //Kill ourselves so that it's a completely clean state.
                                System.exit(0);
                            }
                        });
                alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Cancel",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                Bundle bundle = new Bundle();
                                bundle.putString(FirebaseAnalytics.Param.VALUE, "Erase settings canceled");
                                mFirebaseAnalytics.logEvent("app_menu", bundle);
                                dialog.dismiss();
                            }
                        });
                alertDialog.show();
                return true;
            case R.id.openProjectPage:
                CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
                CustomTabsIntent customTabsIntent = builder.build();
                customTabsIntent.launchUrl(ContactSelect.this, Uri.parse("https://github.com/shusain93/Andromeda-iMessage"));

                return true;
            case R.id.settingsDialog:
                settingsDialog(false);

                return true;
            case R.id.refreshContent:
                prepareView();

                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void settingsDialog(boolean firstTime ) {
        //Load our config database
        final SharedPreferences sharedPreferences = getSharedPreferences("CONFIG",MODE_PRIVATE);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        //you should edit this to fit your needs
        builder.setTitle("Andromeda Configuration");
        builder.setMessage("To use Andromeda you must have a server running OSXMessageProxy. \n\nIf you make a mistake, open the menu up top and choose 'Reset configuration...'");

        final EditText apiIPEndPoint = new EditText(this);
        apiIPEndPoint.setHint("your.domain.com or 182.123.321.164");
        String oldIP = sharedPreferences.getString("apiIPEndpoint", null);
        if ( oldIP != null )
            apiIPEndPoint.setText(oldIP);

        final EditText apiPort = new EditText(this);
        apiPort.setHint("API port (default:8735)");
        if ( !firstTime ) {
            int oldAPIPort = sharedPreferences.getInt("apiPort", 8735);
            apiPort.setText(String.valueOf(oldAPIPort));
        }

        final EditText socketPort = new EditText(this);
        socketPort.setHint("Socket port (default:8736)");
        if ( !firstTime ) {
            int oldSocketPort = sharedPreferences.getInt("socketPort", 8736);
            socketPort.setText(String.valueOf(oldSocketPort));
        }

        final EditText apiProtectionKey = new EditText(this);
        apiProtectionKey.setHint("API key EXACTLY as in server");
        //Check if we have an old stored key so we can load that back
        String oldProtectionKey = sharedPreferences.getString("apiProtectionKey", null);
        if (oldProtectionKey != null) {
            apiProtectionKey.setText(oldProtectionKey);
        }

        //in my example i use TYPE_CLASS_NUMBER for input only numbers
        apiIPEndPoint.setInputType(InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS);
        apiProtectionKey.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        apiPort.setInputType(InputType.TYPE_CLASS_NUMBER);
        socketPort.setInputType(InputType.TYPE_CLASS_NUMBER);

        LinearLayout lay = new LinearLayout(this);
        lay.setOrientation(LinearLayout.VERTICAL);
        lay.addView(apiIPEndPoint);
        lay.addView(apiPort);
        lay.addView(socketPort);
        lay.addView(apiProtectionKey);
        builder.setView(lay);

        // Set up the buttons
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                String givenEndPoint = apiIPEndPoint.getText().toString().trim();
                String givenKey = apiProtectionKey.getText().toString().trim();

                String apiPortText = apiPort.getText().toString().trim();
                if (apiPortText.equals("")) {
                    //No API port given, default
                    apiPortText = "8735";
                }
                String socketPortText = socketPort.getText().toString().trim();
                if (socketPortText.equals("")) {
                    //No socket port given, default
                    socketPortText = "8736";
                }

                int givenAPIPort = Integer.valueOf(apiPortText);
                int givenSocketPort = Integer.valueOf(socketPortText);

                SharedPreferences.Editor editor = getSharedPreferences("CONFIG", MODE_PRIVATE).edit();
                editor.putString("apiIPEndpoint", givenEndPoint);
                editor.putInt("apiPort", givenAPIPort);
                editor.putInt("socketPort", givenSocketPort);
                editor.putString("apiProtectionKey", givenKey);
                //Write sync because we need this done before we can keep going
                editor.commit();
                //We're ready
                prepareView();
            }
        });

        if ( firstTime ) {
            builder.setNegativeButton("Exit", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    finishAffinity();
                }
            });
        }
        else {
            builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                }
            });

        }
        builder.show();
    }

    private void prepareView() {
        //Prepare our APP_CONSTANTS
        final SharedPreferences sharedPreferences = getSharedPreferences("CONFIG",MODE_PRIVATE);
        APP_CONSTANTS.SERVER_IP = sharedPreferences.getString("apiIPEndpoint","0.0.0.0");
        APP_CONSTANTS.SERVER_PROTECTION_TOKEN = sharedPreferences.getString("apiProtectionKey","noStoredProtectionToken");
        APP_CONSTANTS.SERVER_API_PORT = sharedPreferences.getInt("apiPort",0);
        APP_CONSTANTS.SERVER_SOCKET_PORT = sharedPreferences.getInt("socketPort",0);

        //Check for our conversation intents
        onNewIntent(this.getIntent());
        //Setup our conversation UI
        DialogsList dialogsListView = (DialogsList) findViewById(R.id.dialogsList);

        ImageLoader imageLoader = new ImageLoader() {
            @Override
            public void loadImage(ImageView imageView, String url) {
                //If you using another library - write here your way to load image
            }
        };

        //Build our adapter
        dialogsListAdapter = new DialogsListAdapter(imageLoader);

        dialogsListAdapter.setOnDialogClickListener(new DialogsListAdapter.OnDialogClickListener<IDialog>() {
            @Override
            public void onDialogClick(IDialog dialog) {
                Bundle bundle = new Bundle();
                bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, "Trying to show a conversation");
                mFirebaseAnalytics.logEvent("show_conversation", bundle);
                Intent i = new Intent(getApplicationContext(), Conversation.class);
                i.putExtra("conversationJSONString", conversationDataSource.get(Integer.valueOf(dialog.getId())).toString()); //send our conversation's JSON along
                startActivityForResult(i, UITools.DATA_NEEDS_REFRESH);
            }
        });

        dialogsListAdapter.setDatesFormatter( new DateFormatter.Formatter() {
            @Override
            public String format(Date date) {
                if (DateFormatter.isToday(date)) {
                    return DateFormatter.format(date, "h:mm a");
                } else if (DateFormatter.isYesterday(date)) {
                    return getString(R.string.yesterday);
                } else {
                    return DateFormatter.format(date, DateFormatter.Template.STRING_DAY_MONTH_YEAR);
                }
            }
        });
        dialogsListView.setAdapter(dialogsListAdapter,false);

        RemoteMessagesInterface.messagesEndPointReachable(this, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                try {
                    JSONObject versionObject = new JSONObject(response);
                    //Log the server version
                    Bundle bundle = new Bundle();
                    bundle.putString(FirebaseAnalytics.Param.VALUE, versionObject.getString("version"));
                    mFirebaseAnalytics.logEvent("server_version_" + versionObject.getString("version"), bundle);

                    Version serverVersion = new Version(versionObject.getString("version"));


                    //Check if our server version is below the app's required
                    if (serverVersion.compareTo(new Version(BuildConfig.MIN_SERVER_VERSION)) < 0) {
                        UITools.showAlertDialogSafe(ContactSelect.this,R.id.activity_contact_select,"Server version too old","Your server is running version " + serverVersion.get() + " but the BUILDCONFIG for the application demands that you be running at least " + BuildConfig.MIN_SERVER_VERSION + "\n\nYou can continue to use the application however behavior is entirely undocumented.");
                    }
                    //We are online!
                    UITools.showSnackBar(findViewById(android.R.id.content), "Successfully connected!", Snackbar.LENGTH_LONG);
                    //Since we can see the server, setup our contacts
                    setupConversations();
                }catch (JSONException e) {
                    UITools.showAlertDialogSafe(ContactSelect.this,R.id.activity_contact_select,"Server version too old","The server should have responded with a version number JSON at /isUp. You can continue to use the app but it is highly recommended that you update the server ASAP;\nServer said:" + response +"\n" + e.toString());
                    //Log the server version
                    Bundle bundle = new Bundle();
                    bundle.putString(FirebaseAnalytics.Param.VALUE, "<1.1.1");
                    mFirebaseAnalytics.logEvent("server_version", bundle);
                    //Since we can see the server, setup our contacts
                    setupConversations();
                }catch (IllegalArgumentException e) {
                    UITools.showAlertDialogSafe(ContactSelect.this,R.id.activity_contact_select,"Server version invalid","The server should have responded with a version number JSON at /isUp but we got:" + response +"\n" + e.toString());
                    //Log the server version
                    Bundle bundle = new Bundle();
                    bundle.putString(FirebaseAnalytics.Param.VALUE, response);
                    mFirebaseAnalytics.logEvent("server_version_cant_read", bundle);
                }

            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                //We couldn't connect, die.
                String err = (error.toString() == null) ? "Generic network error" : error.toString();
                error.printStackTrace();

                UITools.showAlertDialogSafe(ContactSelect.this,R.id.activity_contact_select,"Couldn't connect to endpoint","The server didn't respond."+"\n" + err.toString());

            }
        });
    }

    /**
     * Try to find a conversation (and then launch it) given incomingNotificationContact
     *
     * ****WARNING*****: THIS SEARCHES FIRST WORD ONLY SO IF YOU HAVE TWO PEOPLE WITH THE SAME FIRST NAME CHARGE THE DAMN THING
     * This is a bug from the way contacts are sent from RemoteMessages. Theoritically we are safe since the most recent match should be the one we want (since it's from a notification, it's NOW)
     *
     * ****WARNING*****: THIS LAUNCHES THE MOST RECENT MATCH OF USER
     * This is because our BBBulletin doesn't specify a conversation instead just a sender so we assume it's the latest given that this is sourced from notifications
     */
    private void tryToShowConversationWithContactName() {
        Log.d("Contacts","Trying to show!");
        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, "Trying to show a conversation based on contact name");
        mFirebaseAnalytics.logEvent("show_conversation", bundle);
        boolean foundConversation = false; //Are we successful?
        for (int i = 0; i != conversationList.size(); i++) { //Integrate all conversationJSONDatabase starting from top. We prioritize latest per WARNINGS above
                //Check if our search term is in the conversation name
            try {
                String conversationLabel = conversationDataSource.get(i).getString("IDs");
                if (conversationLabel.contains(incomingNotificationContact)) {
                    Log.d("ContactNotifier", "Found at " + i);
                    incomingNotificationContact = "";
                    //Record that we found it
                    foundConversation = true;

                    //We found it, let's show it
                    Intent launchIntent = new Intent(getApplicationContext(), Conversation.class);
                    launchIntent.putExtra("conversationJSONString", conversationDataSource.get(i).toString()); //send our conversation's JSON along
                    startActivityForResult(launchIntent, UITools.DATA_NEEDS_REFRESH);
                    //And kill the loop
                    break;
                }
            }catch (JSONException e) {
                FirebaseCrash.logcat(Log.WARN,"ContactNotifier","Couldn't find IDs for " + i);
                FirebaseCrash.report(e);}
        }

        //Check if we succeed
        if (!foundConversation) {
            incomingNotificationContact = "";
            UITools.showDismissableSnackBar(findViewById(android.R.id.content),"Unable to find conversation!\n" + incomingNotificationContact);
        }

    }

    private void setupConversations() {
        RemoteMessagesInterface.getConversations(this,new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
               handleConversationBundle(response);
            }
        },  new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                //We couldn't connect, die.
                String err = (error.toString()==null)?"Generic network error":error.toString();
                UITools.showDismissableSnackBar(findViewById(android.R.id.content),"Unable to load conversationJSONDatabase!\n" + err);
            }
        });
    }

    /**
     * Take the string response for conversations and prepare it for the UI
     * @param response
     */
    protected void handleConversationBundle(String response) {
        try {
            final JSONArray conversationJSONDatabase = new JSONArray(response);

            //Create our storage
            conversationList = new ArrayList();
            //Create our JSON object datasource
            conversationDataSource = new ArrayList<>();
            int conversationCount = conversationJSONDatabase.length();

            for (int i = 0; i != conversationCount; i++) {
                JSONObject conversation = conversationJSONDatabase.getJSONObject(i);
                conversationDataSource.add(i,conversation); //store our conversation
            }

            //Note to future self:: don't remove this sort here. If you do, you break the contact search
            Collections.sort(conversationDataSource, new Comparator<JSONObject>() {
                @Override
                public int compare(JSONObject t0, JSONObject t1) {
                    try {
                        return Integer.compare(t1.getJSONObject("lastMessage").getInt("date"),t0.getJSONObject("lastMessage").getInt("date"));
                    }catch (JSONException e) {
                        e.printStackTrace();
                        FirebaseCrash.log("Compare failed");
                        FirebaseCrash.report(e);
                        return -1;
                    }
                }
            });

            for (int i = 0; i != conversationCount; i++) {
                //Grab our conversation ahead of time
                final JSONObject conversation = conversationDataSource.get(i);

                final int dataSourcePosition = i;

                conversationList.add(new IDialog() {
                    @Override
                    public String getId() {
                        return "" + dataSourcePosition;
                    }

                    @Override
                    public String getDialogPhoto() {
                        return null;
                    }

                    @Override
                    public String getDialogName() {
                        try {
                            return conversation.getString("display_name");
                        }catch (JSONException e) {
                            FirebaseCrash.log("Nameless chat error");
                            return "Nameless chat error";
                        }
                    }

                    @Override
                    public List<IUser> getUsers() {
                        ArrayList<IUser> users = new ArrayList<>();
                        users.add(new IUser() {
                            @Override
                            public String getId() {
                                return "number";
                            }

                            @Override
                            public String getName() {
                                return "FIRST PERSON";
                            }

                            @Override
                            public String getAvatar() {
                                return null;
                            }
                        });
                        return users;
                    }

                    @Override
                    public IMessage getLastMessage() {
                        return new IMessage() {
                            @Override
                            public String getId() {
                                return null;
                            }

                            @Override
                            public String getText() {
                                try {
                                    return conversation.getJSONObject("lastMessage").getString("text");
                                } catch (JSONException e) {
                                    return "";
                                }
                            }

                            @Override
                            public IUser getUser() {
                                return new IUser() {
                                    @Override
                                    public String getId() {
                                        return "number";
                                    }

                                    @Override
                                    public String getName() {
                                        return "FIRST PERSON";
                                    }

                                    @Override
                                    public String getAvatar() {
                                        return null;
                                    }
                                };
                            }

                            @Override
                            public Date getCreatedAt() {
                                try {
                                    //Convert from cocoa to epoch hence 978307200 and then to ms
                                    return new Date((conversation.getJSONObject("lastMessage").getInt("date") + 978307200L)*1000);
                                } catch (JSONException e) {
                                    return new Date();
                                }

                            }
                        };
                    }

                    @Override
                    public void setLastMessage(IMessage message) {

                    }

                    @Override
                    public int getUnreadCount() {
                        return 0;
                    }
                });
            }

           /* Collections.sort(conversationList, new Comparator<IDialog>() {
                @Override
                public int compare(IDialog t0, IDialog t1) {
                        return t1.getLastMessage().getCreatedAt().compareTo(t0.getLastMessage().getCreatedAt());


                }
            });*/
            dialogsListAdapter.setItems(conversationList);
            Log.d("Contacts","Notification: " + incomingNotificationContact);
            //Now that we're done, check if we waited to launch a conversation
            if (incomingNotificationContact.equals("") == false) {
                Log.d("Contacts","We have a search from the dead!");
                //We have a search!
                tryToShowConversationWithContactName();
            }

        } catch (JSONException e) {
            FirebaseCrash.log("Couldn't parse conversation json");
            FirebaseCrash.report(e);
            UITools.showDismissableSnackBar(findViewById(android.R.id.content),"JSON error:\n" + e.toString());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == UITools.DATA_NEEDS_REFRESH) {
            Log.d("Conversation","Came back from conversations, checking for update payload");
            if (data != null && data.hasExtra("latestSocketConversationData")) {
                Log.d("Conversation","We have a data package to update for");
                String newData = data.getExtras().getString("latestSocketConversationData");
                handleConversationBundle(newData);
            }else {
                Log.d("Conversation","No latestSocketConversationData to use");
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();  // Always call the superclass method first
        Log.d("onPauseContacts","SUSPENDING SOCKET");
        if (socketClient != null && socketClient.socketThread != null) {
            socketClient.socketThread.cancel(false);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (socketClient != null && socketClient.socketThread.isCancelled() == false) {
            socketClient.socketThread.cancel(false);
        }

        //We need this check because onResume ignores config. Can't connect to an empty int. 0 is default int value.
        if (APP_CONSTANTS.SERVER_SOCKET_PORT != 0) {
            socketClient = new SocketClient(APP_CONSTANTS.SERVER_IP, APP_CONSTANTS.SERVER_SOCKET_PORT, new SocketResponseHandler() {

                @Override
                public void handleResponse(final String response) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                JSONObject bundle = new JSONObject(response);
                                if (bundle.getString("type").equals("conversations")) {
                                    //New conversation bundle!
                                    handleConversationBundle(bundle.getJSONArray("content").toString());
                                } else {
                                    //We don't want to handle this, notify children
                                }
                            } catch (JSONException e) {
                                Log.d("handleSocket", "failed to parse bundle " + response);
                                e.printStackTrace();
                            }
                        }
                    });
                }
            });
        }

    }

}
