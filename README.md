# ProjectRTC

## WebRTC Live Streaming

- a very simple Android client for [ProjectRTC](https://github.com/pchab/ProjectRTC)

The android client can stream its back cam to multiple peers :
- Launch the app
- Enter a name in the top field and hit the "Stream" button
- Go to 54.214.218.3:3000 in a (Chrome) browser and choose your stream
- OR use the link at the bottom of the app

If you can't compile it yet, you can still download the apk [here](https://github.com/pchab/ProjectRTC/raw/master/AndroidRTC.apk).

## Libraries

- [libjingle peerconnection](https://code.google.com/p/libjingle/)
- [android-websockets](https://github.com/koush/android-websockets)

If you want to use them in your project, I recommend working with IntelliJ IDEA :

- Start a new android application project
- Add the .jar in the libs folder
- Right-click "add as library"

I tried Eclipse and Android Studio but this is just much easier.

## Author

- Pierre Chabardes
