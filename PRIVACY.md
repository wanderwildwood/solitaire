# Privacy

Solitaire collects nothing and sends nothing anywhere. It asks for no permissions, and has no
internet permission, so it cannot.

It keeps, in its own private storage on the phone: the game of each kind you have under way
(the deal and the moves made since), which game was open, the two settings, and how many games
of each kind you have played and won. None of it is backed up by the app, and uninstalling it
removes all of it.

The About dialog's last line opens a web address in your browser, and only when you press it.

The one permission in its manifest is not one you are asked for: Android's support library adds
it to every app built with it (`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`), so that the app's own
internal messages can reach only the app itself.
