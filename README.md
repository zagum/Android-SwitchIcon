Android-SwitchIcon
================

[![API](https://img.shields.io/badge/API-15%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=15)

Google launcher-style implementation of switch (enable/disable) icon

![image](https://github.com/zagum/Android-SwitchIcon/blob/master/art/sample.gif)

Compatibility
-------------

This library is compatible from API 15 (Android 4.0.3).

Download
--------

Add it in your root build.gradle at the end of repositories:

```groovy
allprojects {
    repositories {
        ...
        maven { url "https://jitpack.io" }
    }
}
```

Add the dependency

```groovy
dependencies {
todo
}
```

Usage
-----
Set icon (vector or image) to SwitchIconView and enjoy switchable icon in your app :)
Default implementation:

```xml
    <com.github.zagum.switchicon.SwitchIconView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        app:si_image="@drawable/ic_cloud"/>
```

Fully customized implementation:

```xml
    <com.github.zagum.switchicon.SwitchIconView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        app:si_animation_duration="500"
        app:si_disabled_alpha=".3"
        app:si_image="@drawable/ic_cloud"
        app:si_padding="8dp"
        app:si_tint_color="#ff3c00"/>
```

Public methods: 

```java

  public void setState(@State int state);

  public void setState(@State int state, boolean animate);

  public void switchState();

  public void switchState(boolean animate);
```

See [sample](https://github.com/zagum/Android-SwitchIcon/tree/master/switchicon-sample) project for more information.

TODO
-------

Icon scale now is not supported
Changing dash size not supported

License
-------

    Copyright 2016 Evgenii Zagumennyi
    
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
    
    http://www.apache.org/licenses/LICENSE-2.0
    
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
