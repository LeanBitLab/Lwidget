#!/bin/bash
sed -i '/android:id="@+id\/row_date_color"/,+4d' app/src/main/res/layout/activity_main.xml
sed -i '/android:id="@+id\/row_date_color_custom"/,+4d' app/src/main/res/layout/activity_main.xml
