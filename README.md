A tool to help bypass the annoying WiFi login prompt at SHSID.

### Foreword ###

The following documentation is from the future. Treat all "features" mentioned
below as a todo-list. The app is in pre-pre-pre-alpha right now and may
do anything it wishes, from causing your phone to explode to eradicating all
life on earth.

### Why did you name it 'Swallow'? ###

Actually, the original name was 'SWAL', short for 'SHS(ID) WiFi Auto-Login'.
The all-caps name was boring, unimaginative, and looked too similar to 'swag',
so it was changed to 'Swallow', as in the bird with an unknown air-speed velocity.

### How do I use it? ###

Well, first you would need to compile it (or download a pre-built APK file)
and install it on your Android device of choice. When that's done, simply
launch the app, enter your WiFi username and password, and enable the
auto-login mode.

The app will run in the background and listen to WiFi update events, and
when it detects that you have connected to an SHSID network, it will log in
with the provided credentials. Simple as that, no need to open a web browser!
You don't even need to run the app; it will automatically start on boot!

### Is this really just malware that steals my account? ###

Not at all! That's why this app is fully open source: so you can check for
yourself! If you're truly paranoid though, feel free to monitor all of the
HTTP requests the app makes ;-)

### Will I get banned for using this app? ###

Theoretically, no. Included is a special "network profiles" feature, which
limits the WiFi access points you will connect to. You can simply choose
the profile(s) you want to use (or create your own!), and the app will only
automatically log in at those access points. This prevents you from logging
in from other buildings, which is a common source of bans (I would know;
I got banned for it myself! :p)

In practice, it's unlikely, seeing as the app doesn't really violate any
ToS agreement or anything. Of course, be prepared for the worst! If the
school notices the sudden increase of bandwidth usage due to the lower
effort required to log in, watch out!

### I haz an error. ###

#### Connection failed. ####
This error is displayed when the app cannot load the login page (http://192.255.255.94/).
This can occur for three reasons:

- You disconnected from the WiFi while the app was logging in (most likely)
- The network you are connecting to is not a SHSID network (configuration error)
- The school changed their WiFi login address (least likely)

If you have made sure that the error is not being caused by (1) and (2), please send a
bug report so I can fix the problem as soon as possible!

#### Unknown error occured. ####
This error is displayed when the app was able to load the login page, but does not know
how to handle the reply. In most cases, this error is due to some weird corner case that
I did not consider. The other possibility is that the school changed the format of their
WiFi login page. If you get this error, please send a bug report.

### Will there be an iOS/Windows Phone/Desktop version? ###

Unfortunately, iOS doesn't allow this type of app in the App Store, and I have no
experience with jailbroken iOS development, so probably not in the foreseeable
future. I'm not sure about Windows Phone, and I don't have any way to test on
it either, so no, sorry!

*However*, there is a solution for all platforms: there is a userscript (also
made by me [*sheepish grin*]) that does essentially the same thing, but requires
the use of a web browser that supports userscripts. There is an iOS tweak
(Userscripts Enabler) that allows this, and for the other platforms you may
download a browser/plugin that has userscript support.

Note that the userscript doesn't limit login locations, so don't be surprised
if you get banned for using it outside of your building.

You may find the userscript here: http://host-a.net/u/crossbowffs/shsidwifiautologin.user.js

### Does this app let me log in without an account? ###

No. I'm a programmer, not a hacker. Go ask someone else.