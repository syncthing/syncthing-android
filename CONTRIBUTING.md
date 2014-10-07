## Reporting An Issue

Please search for existing issues before opening a new one.

### Where To Report

Issues might be related either to syncthing, syncthing-android or both. The general rule of thumb is this:
If the issue has to do with synchronization, discovery, the web interface, or can be reproduced on a desktop, it should be posted at [syncthing](https://github.com/calmh/syncthing/issues).

If the issue is related to the Android UI, background service, or can't be reproduced on a desktop running the same syncthing version, it should be posted here.

For general usage help or questions, you should post to [discourse](http://discourse.syncthing.net/category/support).

### Bug Reports

A bug report should include the following information:

Description of the problem.

Steps to reproduce:

1. This is the first step
2. This is the second step
3. Further steps, etc.

Observed behaviour and expected behaviour.

syncthing-android version: x.x.x

Device name and Android version:

versions of involved syncthing nodes: vx.x.x, ...

logcat: *link to file* (over the entire procedure, limited to the package `com.nutomic.syncthingandroid` if you can, [How to Logcat](http://forum.xda-developers.com/showthread.php?t=1726238) [Android Studio Logging Guide](http://stackoverflow.com/a/17648663/1837158)).

config.xml: *link to file* (if it might be related, located in `/data/data/com.nutomic.syncthingandroid`, requires root)

screenshots: *link to file* (only for UI problems)

## Pull Requests

Always welcome.

Code should follow the [Android Code Style Guidelines](https://source.android.com/source/code-style.html#java-language-rules). This can be done automatically in Android Studio.

Unit tests are available, and can be executed with `gradle connectedAndroidTest`, or from Android Studio. New code should always add or improve related tests.

Lint warnings should be fixed. If that's not possible, they should be ignored as specifically as possible.
