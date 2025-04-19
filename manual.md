# Prerequisites

The [garmin connect app](https://play.google.com/store/apps/details?id=com.garmin.android.apps.connectmobile&hl=en_AU) must be installed to send routes to your device or configure the device settings. The tile server can be used without installing garmin connect. A warning will be show on the device selection screen, and a prompt when you open the app if garmin connect is not installed or up to date.  
The watch app will also need a bluetooth connection with the phone in order to send routes and load map tiles from the companion app (or directly from internet);


# Adding A Gpx Route To The Watch 

From Google Maps - You can create some directions and the click 'Share Directions'. The breadcrumb app should be listed as an app that you can share the directions too. Sometimes it is hidden under multiple 'more' buttons.  
Import a file - Open the breadcrumb app and select 'Import GPX', a file browser will be launched that you can select the route from.  
Open A Gpx File - When opening a gpx file select 'open with' and pick the breadcrumb app.  
Komoot - Similar to google maps, from the komoot app got to saved routes, select a route and `share with link`. The breadcrumb app should appear in the available app list to share with. You can also download the gpx route and open it through other means.  
WorldTopoMap App - Go To the tracks section, and click the info button on a track. There is then a `...` menu at the top. Select share, and then the breadcrumb app.  

The `ShareWith` functionality should work with any app that shares a gpx route. Please let me know if there is an app that does not work and I will try to add support for it (if I can access the app without a subscription).

# Overview Page

The overview page is the main entrypoint of the app, it allows you to import a gpx file from your local storage, or configure a connected devices settings. The route history section shows when each route has been loaded onto the device, and allows a quick way to load routes from the past again (by tapping the route in the history section). The clear all button allows you to empty the route history.

---

# Devices Page

The page for selecting which device to interact with. The selected device will appear in the title bar on all app pages.

---

# App Settings Page

Configure the app settings

### Phone Hosted Tile Server

The phone hosts a tile server for the watch to query tiles from. The tiles downloaded are cached locally on the phone for use with offline map support.

Tile Server Enabled - enable/disable the tile server. You may want to disable the tile server if you are using a templated url on the watch, and not using the companion app to host offline tiles. This will remove some notifications, and not start the tile server process.  
Add Custom Server - Manually configure a tile server for use on the companion app, this tile server will be added to the  'Select Server' dropdown.  
Select Server - Select the tile server you wsh the companion app to serve tiles from.

---

# Device Settings Page

This page is able to be accesed from two locations, the overview page (by clicking device settings), or the devices page (by clicking the settings cog next to the device). The device settings page is for configuring the on watch settings, it is similar to garmins connectiq settings, but more cusotomized eg. colour pickers. It also supports editing the per route settings (array settings are currently not suported through connectiq settings). For information on each of the settings see [Connect Iq Settings](https://github.com/pauljohnston2025/breadcrumb-garmin/blob/master/settings.md#garmin-settings-connect-iq-store)

---

# Map View Page

Feature comming soon - will support pre downloading tiles for a selected area, and possibly previewing routes on the app before sending to watch.

---

# Storage Info Page

Feature comming soon - will show how many tiles, and sizes are stored localy, and allow removing cached tiles per tile server

---

# Debug Info Page

Feature comming soon - will show debug information about the app state

---
