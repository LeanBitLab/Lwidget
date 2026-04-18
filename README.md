# Lwidget

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/images/banner_dark.svg">
  <source media="(prefers-color-scheme: light)" srcset="docs/images/banner_light.svg">
  <img alt="Lwidget Banner" src="docs/images/banner_light.svg">
</picture>

<div align="center">

[![Download](https://img.shields.io/github/v/release/LeanBitLab/Lwidget?label=Download&style=flat-square&color=7C4DFF)](https://github.com/LeanBitLab/Lwidget/releases/latest) [![Downloads](https://img.shields.io/github/downloads/LeanBitLab/Lwidget/total?style=flat-square&color=7C4DFF&label=Downloads)](https://github.com/LeanBitLab/Lwidget/releases) [![Stars](https://img.shields.io/github/stars/LeanBitLab/Lwidget?style=flat-square&color=7C4DFF)](https://github.com/LeanBitLab/Lwidget/stargazers)

</div>

**Lwidget** is a modern, open-source Android widget built with **Kotlin** and **Material 3** design principles. It provides essential information at a glance while adhering to your device's dynamic theme.

💬 **Feedback & Bugs:** [Telegram Group](https://t.me/leanbitlabchat) · [GitHub Discussions](https://github.com/LeanBitLab/Lwidget/discussions) · [Report a Bug](https://github.com/LeanBitLab/Lwidget/issues)

## Screenshots

<table>
  <tr>
    <td><img src="docs/images/1.png" height="400" alt="Screenshot 1"/></td>
    <td><img src="docs/images/2.png" height="400" alt="Screenshot 2"/></td>
    <td><img src="docs/images/3.png" height="400" alt="Screenshot 3"/></td>
    <td><img src="docs/images/4.png" height="400" alt="Screenshot 4"/></td>
  </tr>
  <tr>
    <td><img src="docs/images/5.png" height="400" alt="Screenshot 5"/></td>
    <td><img src="docs/images/6.png" height="400" alt="Screenshot 6"/></td>
    <td><img src="docs/images/7.png" height="400" alt="Screenshot 7"/></td>
    <td><img src="docs/images/8.png" height="400" alt="Screenshot 8"/></td>
  </tr>
</table>

## Features

-   **Material You**: Full dynamic color support.
-   **Configurable**: Adjust text sizes and visibility for all elements.
-   **Time & Date**: Clear, customizable display.
-   **Battery & Temperature**: Real-time device status.
-   **Step Counter**: Track your daily steps directly from the widget.
-   **Calendar Events**: Upcoming agenda at a glance.
-   **Task Integration**: Seamless integration with [Tasks.org](https://tasks.org/).
-   **Weather**: Current conditions and weekly forecast warnings via [Breezy Weather](https://github.com/breezy-weather/breezy-weather) integration.
-   **World Clock**: Track time in another zone.
-   **Next Alarm**: Display your next scheduled alarm.
-   **Daily Data Usage**: Monitor your daily data consumption.
-   **Screen Time**: Track your daily device usage directly from the widget.
-   **Internal Storage**: Monitor available device storage.
-   **Custom Formats**: Choose your preferred Time and Date formats.
-   **Light/Dark Mode**: Optimized contrast for readability.
-   **Privacy Focused**: No internet permission required.
-   **Accent Outline**: Adds a stylish border to the widget.
-   **Improved Settings**: Refined settings interface for better usability.
-   **Manual Transparency**: Fine-tune the widget's background transparency.
-   **Custom Colors:** Choose between Default, System Accent, or Custom colors for text and background.
-   **Outline Color**: Customize the widget outline color.

## Download

You can download the latest release from the [Play Store](https://play.google.com/store/apps/details?id=com.leanbitlab.lwidget) or the [GitHub Releases](https://github.com/LeanBitLab/Lwidget/releases) page.

<br>

<div align="center">
  <a href="https://play.google.com/store/apps/details?id=com.leanbitlab.lwidget">
    <img alt="Get it on Google Play" src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" height="60">
  </a>
  &nbsp;&nbsp;&nbsp;
  <a href="https://github.com/LeanBitLab/Lwidget/releases/latest">
    <img alt="Download from GitHub" src="https://img.shields.io/badge/GitHub-Releases-181717?style=for-the-badge&logo=github&logoColor=white" height="42">
  </a>
</div>

## Setup

-   **Calendar**: Required to display upcoming events on the widget.
-   **Activity Recognition**: Required for the Step Counter feature.
-   **Usage Access**: Required to display daily data consumption and screen time.
-   **Tasks.org Integration**: Requires Tasks.org to be installed for task integration.
-   **Notifications**: Required for the Step Counter foreground service.
-   **Breezy Weather Integration**: Requires [Breezy Weather](https://github.com/breezy-weather/breezy-weather) to be installed with DataBridge enabled. In Breezy Weather: Settings → External Modules → Send Gadgetbridge Data → toggle on Lwidget. Lwidget does not access location or the internet — it only reads weather data stored locally by Breezy Weather.
-   **Battery Optimization**: Lwidget works without any background service. However, some devices may kill the app to save battery, which can stop widget updates. To fix this:
    - Mark Lwidget as non-battery-optimized in your device settings.
    - Or, enable the Step Counter — it uses a lightweight foreground service that also keeps all other widget features updating live.

All permissions are requested only when you enable the corresponding feature.

## License

Lwidget is licensed under **GNU General Public License v3.0**.

See [LICENSE](LICENSE) file.


## Credits

-   Built with ❤️ by [LeanBitLab](https://github.com/LeanBitLab)
-   🛡️ LeanBitLab Ecosystem: 👉 [Check out our other projects!](https://github.com/LeanBitLab#-current-projects)

## Support the Development

Building and maintaining open-source apps takes time and resources. If you love Lwidget, please consider supporting the project!

<a href="https://github.com/sponsors/LeanBitLab">
  <img src="https://img.shields.io/static/v1?label=Sponsor&message=%E2%9D%A4&logo=GitHub&color=%23fe8e86" width="150" alt="Sponsor on GitHub"/>
</a>

Your support keeps the code **100% Free and Open Source**.

---

*Lwidget • Modern Material You Widget*
