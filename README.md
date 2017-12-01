# Andromeda(B)
A client for the [MessageProxy Server](https://github.com/shusain93/OSXMessageProxy) so as to provide an entirely open source implementation of iMessage on Android!  This is derived from Salman Husain's version and adds a few new features!

### New Features

1. Update Settings whenever you want rather than first run.  Launch from action menu.
2. More sophisticated time/date display for conversation list
2. Refresh conversation list option in action menu.

### Configuration / Setup!

1. Setup the MessageProxy first on a server first. 
2. [Download and install the latest client](https://github.com/shusain93/Andromeda-iMessage/releases) or build from source
4. Open the app and fill out the configuration. You'll want some sort of domain which will resolve both internally and externally. Setup a DNS server at home if needed. Fill in the *exact* same API protection token as you did previously. Remember, absolutely no special chars or spaces. Just A-z and 0-9. If you file an issue and your token includes this I will punch you in the face through your computer.
5. Install Pushbullet on your phone, sign in with the same account as you setup with IFTTT (or setup with some other push service, that's on you. I picked IFTTT because it's a very general API and you can pick how you get your notifications i.e. flashing your coffee pot)
6. Build and run on to your Android device. Mind you, this is for a high API target so if you're on KitKat shucks to your ducks
7. Hopefully it should work at this point. It works for me and I use it everyday so it's mildly unlikely there is a major connection bug here so if it doesn't get your messages check all your config files.

#### Crash reporting/analytics

As this still is functionally Salman's app, the firebase reporting still sends data to him.  Let him (and me) know if your issue happens on one of my builds.
