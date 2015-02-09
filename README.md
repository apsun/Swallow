# Swallow

A tool to help bypass the annoying WiFi login prompt at SHSID. 

## Requirements

- An Android device running 4.0 Ice Cream Sandwich or newer
- An internet account at SHSID
- 3 minutes of spare time

## Usage

The app comes preconfigured with a auto-login profile for Xianmian Building 
('XMB'), so if you want to automatically log in from a different location, 
make sure to create your own profile (and disable the 'XMB' profile while 
you're at it).

After you're done setting up your profile, head back to the main screen, and 
fill out your username and password. Press the save button, and 
you're done! The next time you need to log in, Swallow will display a 
notification; just tap that and the app will take care of the rest!

## License

All code is licensed under [GPLv3](http://www.gnu.org/licenses/gpl-3.0.txt). 
Images are copyright their respective owners.

## FAQ

### Why did you name it 'Swallow'?

The name comes from the first four letters, which stand for 'SHS(ID) WiFi 
Auto-Login'. It's not an obscure reference to some ancient Greek god or 
anything; this isn't English class!

### Is this really just malware that steals my account?

Not at all! That's why this app is fully open source: so you can check for
yourself! If you're truly paranoid though, just don't use it ;-)

### Will I get banned for using this app?

Theoretically, no. However, if you set up your profile list incorrectly, you 
might get banned for logging in outside of your building. And of course, the 
school might begin to ban users of this app, so use it with caution.

### I haz an error!

Well, that's not a question, but here's a few of the more confusing error 
messages you might encounter:

#### Connection failed.

This error is displayed when the app cannot load the login page 
(http://192.255.255.94/). If you are certain that your profile is configured 
correctly, it is likely a problem with your WiFi connection. Try restarting 
your device.

#### Unknown error occurred.

This error is displayed when the app was able to load the login page, but 
does not know how to handle the data. This is likely a bug in the software; 
please send a bug report!

### Will there be an iOS/Windows Phone/Desktop version?

No. *However*, there is a solution for those who have a browser that supports 
userscripts. You may find the userscript here: 
http://host-a.net/u/crossbowffs/shsidwifiautologin.user.js

### Does this app let me log in without an account?

No. I'm a programmer, not a hacker. Go ask someone else. Also, how did you 
make it here without reading the requirements?