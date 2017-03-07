package eu.aero2x.andromedab;

import android.text.util.Linkify;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import com.stfalcon.chatkit.commons.models.IMessage;
import com.stfalcon.chatkit.messages.MessagesListAdapter;
import com.stfalcon.chatkit.utils.DateFormatter;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by Salman on 2/17/17.
 */

public class CustomIncomingMessage extends MessagesListAdapter.IncomingMessageViewHolder<Message> {
    public CustomIncomingMessage(View itemView) {
        super(itemView);
    }

    @Override
    public void onBind(Message message) {
        super.onBind(message);

        TextView messageStatus = (TextView)itemView.findViewById(R.id.messageStatus);
        TextView contactDisplayName = (TextView)itemView.findViewById(R.id.contactDisplayName);
        TextView messageText = (TextView)itemView.findViewById(R.id.messageText);

        messageText.setTextIsSelectable(true);
        Linkify.addLinks(messageText,Linkify.ALL);
        messageText.setLinksClickable(true);

        if (message.getId().equals(Conversation.lastMessageGUID)) { //Are we the last back?
            //Horray!
            /*if (message.isDelivered()) {
                if (message.isRead()) {
                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
                    try {
                        Date parsed = sdf.parse(message.getTimeRead());
                        //We are read
                        messageStatus.setText("Read at " + new SimpleDateFormat("h:mm a").format(parsed));
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }

                }else {
                    //We are delivered but not read
                    messageStatus.setText("Delivered");
                }
            }*/
            messageStatus.setText("");
        }else {
            //Not delivered/no label
            messageStatus.setText("");
            messageStatus.setHeight(0);
        }

        if (Conversation.IDs.split(",").length > 1) { //Do we have more than one other person? No use in labels if it's one dude
            int thisMessageIndex = Conversation.messageDataStore.indexOf(message); //Find ourselves in the stack

            //Do we have enough to safely search and then check if the person above us has the same name.
            if (Conversation.messageDataStore.size() > thisMessageIndex+1 && Conversation.messageDataStore.get(thisMessageIndex + 1).getUser().getName().equals(message.getUser().getName())) {
                //We don't need a label
                contactDisplayName.setText("");

            } else {
                contactDisplayName.setText(message.getUser().getName());
            }
        }else {
            //We don't need a label since we are alone
            contactDisplayName.setText("");
            //We can destroy the cell since we never re use them in this case
            contactDisplayName.setHeight(0);
        }

        time.setText(DateFormatter.format(message.getCreatedAt(), "h:mm a"));
    }


}
