package eu.aero2x.andromedab;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.github.javiersantos.appupdater.AppUpdater;
import com.github.javiersantos.appupdater.enums.UpdateFrom;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crash.FirebaseCrash;
import com.stfalcon.chatkit.commons.ImageLoader;
import com.stfalcon.chatkit.commons.models.IDialog;
import com.stfalcon.chatkit.commons.models.IMessage;
import com.stfalcon.chatkit.commons.models.IUser;
import com.stfalcon.chatkit.dialogs.DialogsList;
import com.stfalcon.chatkit.dialogs.DialogsListAdapter;

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
                tryToShowConversationWithContactName();
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
        //Load our config database
        final SharedPreferences sharedPreferences = getSharedPreferences("CONFIG",MODE_PRIVATE);
        //Check if we are not yet setup
        if (sharedPreferences.getString("apiEndpoint",null) == null) {

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            //you should edit this to fit your needs
            builder.setTitle("Andromeda Configuration");
            builder.setMessage("To use Andromeda you must have a server running OSXMessageProxy. Enter the FULL API endpoint URL (i.e. http://<domain>:port) without any trailing slashes. In the next text field, enter your API key/password which you have selected. Make sure that all the data you enter is correct because it will not be validated and you will have to delete and re-install the app to reconfigure");

            final EditText apiText = new EditText(this);
            apiText.setHint("http://your.domain.com:8735");//optional
            final EditText apiProtectionKey = new EditText(this);
            apiProtectionKey.setHint("API key AS IT IS SET IN CONFIG.plist");//optional

            //in my example i use TYPE_CLASS_NUMBER for input only numbers
            apiText.setInputType(InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS);
            apiProtectionKey.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

            LinearLayout lay = new LinearLayout(this);
            lay.setOrientation(LinearLayout.VERTICAL);
            lay.addView(apiText);
            lay.addView(apiProtectionKey);
            builder.setView(lay);

            // Set up the buttons
            builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    String givenEndPoint = apiText.getText().toString().trim();
                    String givenKey = apiProtectionKey.getText().toString().trim();
                    SharedPreferences.Editor editor = getSharedPreferences("CONFIG", MODE_PRIVATE).edit();
                    editor.putString("apiEndpoint",givenEndPoint);
                    editor.putString("apiProtectionKey",givenKey);
                    //Write sync because we need this done before we can keep going
                    editor.commit();
                    //We're ready
                    prepareView();
                }
            });

            builder.setNegativeButton("Exit", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    finish();
                }
            });
            builder.show();
        }else {
            //We have already configured
            prepareView();
        }
    }

    private void prepareView() {
        //Prepare our APP_CONSTANTS
        final SharedPreferences sharedPreferences = getSharedPreferences("CONFIG",MODE_PRIVATE);
        APP_CONSTANTS.API_URL = sharedPreferences.getString("apiEndpoint","http://no.stored.url.com");
        APP_CONSTANTS.API_PROTECTION_TOKEN = sharedPreferences.getString("apiProtectionKey","noStoredProtectionToken");
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
        dialogsListView.setAdapter(dialogsListAdapter);

        RemoteMessagesInterface.messagesEndPointReachable(this, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                //We are online!
                UITools.showSnackBar(findViewById(android.R.id.content), "Successfully connected!", Snackbar.LENGTH_LONG);

                //Since we can see the server, setup our contacts
                setupConversations();

            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                //We couldn't connect, die.
                String err = (error.toString() == null) ? "Generic network error" : error.toString();
                UITools.showDismissableSnackBar(findViewById(android.R.id.content), "Unable to connect!\n" + err);
                error.printStackTrace();
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
        boolean foundConversation = false; //Are we successfull?
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
                    startActivity(launchIntent);
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
                                    FirebaseCrash.report(e);
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

                    /*
                    Collections.sort(conversationList, new Comparator<IDialog>() {
                        @Override
                        public int compare(IDialog t0, IDialog t1) {
                            return t1.getLastMessage().getCreatedAt().compareTo(t0.getLastMessage().getCreatedAt());
                        }
                    });
*/

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
        },  new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                //We couldn't connect, die.
                String err = (error.toString()==null)?"Generic network error":error.toString();
                UITools.showDismissableSnackBar(findViewById(android.R.id.content),"Unable to load conversationJSONDatabase!\n" + err);
            }
        });


    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == UITools.DATA_NEEDS_REFRESH) {
            setupConversations();
            Log.d("Conversation","GOT UPDATE NEEDED INTENT");
        }
    }

}
