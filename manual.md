---
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
