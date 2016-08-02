## Reporting Bugs

Please file bugs in the [Github Issue
Tracker](https://github.com/syncthing/syncthing-android/issues). Bugs that
are not specific to Syncthing-Android should be reported to the
[main project](https://github.com/syncthing/syncthing/issues) instead.
Include at least the following in your issue report:

 - What happened

 - What did you expect to happen instead of what *did* happen, if it's
   not crazy obvious

 - What version of Android, Syncthing and Syncthing-Android you are
   running

 - Screenshot if the issue concerns something visible in the GUI

 - Console log entries, where possible and relevant

You can get logs in various ways:

 - Log window in the app: Settings -> Open Log -> Android Log

 - In the terminal over USB: `adb logcat`

 - Using one of the various "logcat apps" on Google Play and F-Droid

## Contributing Translations

All translations are done via
[Transifex](https://www.transifex.com/projects/p/syncthing-android/). If you
wish to contribute to a translation, just head over there and sign up.
Before every release, the language resources are updated from the
latest info on Transifex.

## Contributing Code

Every contribution is welcome. If you want to contribute but are unsure
where to start, any open issues are fair game!

Code should follow the
[Android Code Style Guidelines](https://source.android.com/source/code-style.html#java-language-rules),
which are used by default in Android Studio.

Unit tests are available, and can be executed from Android Studio, or from
the terminal with `gradle connectedAndroidTest`.
