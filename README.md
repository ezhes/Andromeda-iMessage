# Andromeda(B)
A client for the [MessageProxy Server](https://github.com/shusain93/OSXMessageProxy) so as to provide an entirely open source implementation of iMessage on Android!

### Features

1. Getting and sending messages
2. Delivery status/read status
2. **GROUP CHATS!** This includes named and unnamed iMessage group chats (i.e. Person 1, person 2, person 3 AND "The Sushi Brigade")
3. Loading of attachments of any type (images are inline). *todo:* allow sending!
4. Live messaging (with sockets!)

A named iMessage Group|  Inline photos!
:-------------------------:|:-------------------------:
![image](Screenshots/Screenshot_20170428-005529.png)  | ![image](Screenshots/Screenshot_20170428-005453.png)

### Configuration / Setup!

1. Setup the MessageProxy first on a server first. 
2. [Download and install the latest client](https://github.com/shusain93/Andromeda-iMessage/releases) or build from source
4. Open the app and fill out the configuration. You'll want some sort of domain which will resolve both internally and externally. Setup a DNS server at home if needed. Fill in the *exact* same API protection token as you did previously. Remember, absolutely no special chars or spaces. Just A-z and 0-9. If you file an issue and your token includes this I will punch you in the face through your computer.
5. Install Pushbullet on your phone, sign in with the same account as you setup with IFTTT (or setup with some other push service, that's on you. I picked IFTTT because it's a very general API and you can pick how you get your notifications i.e. flashing your coffee pot)
6. Build and run on to your Android device. Mind you, this is for a high API target so if you're on KitKat shucks to your ducks
7. Hopefully it should work at this point. It works for me and I use it everyday so it's mildly unlikely there is a major connection bug here so if it doesn't get your messages check all your config files.

#### Crash reporting/analytics

I have setup Firebase crash and session analytics. Nothing personally identifiable is being stolen away (you can check through the code!) but if you are still not comfortable with this you can easily disable tracking globally by editing `AndroidManifest.xml`'s `firebase_analytics_collection_deactivated` key to be `true`. Given that this is beta, I'd recommend that you don't do this since I need crash reporting to make this better but I of course realize that messaging is a very sensitive data.