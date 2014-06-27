# AndroidRTC

## WebRTC Live Streaming

- an Android client for [ProjectRTC](https://github.com/pchab/ProjectRTC)

If you can't compile it yet, you can still download the apk [here](https://github.com/pchab/ProjectRTC/raw/master/AndroidRTC.apk).

## Libraries

- [libjingle peerconnection](https://code.google.com/p/webrtc/)
- [android-websockets](https://github.com/koush/android-websockets)

If you want to use them in your project, I recommend working with IntelliJ IDEA :

- Start a new android application project
- Add the .jar and .so in the libs folder
- Right-click "add as library" 

## How To

You need [ProjectRTC](https://github.com/pchab/ProjectRTC) up and running, and it must be somewhere that your android can access. (You can quickly test this with your android browser).

When you launch the app, you will be given several options to send a message : "Call someone"
Use this menu to send the link to your stream. This link can be opened with a WebRTC-capable browser or by another AndroidRTC.
The video call should then start.

Your stream should appear as "android_test" in ProjectRTC, so you can also use the call feature there.

## Author

- Pierre Chabardes
