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

Once added routes can be seen in the [Routes Page](#routes-page)

# Overview Page

The overview page is the main entrypoint of the app, it allows you to import a gpx file from your local storage, or configure a connected devices settings. The route history section shows when each route has been loaded onto the device, and allows a quick way to load routes from the past again (by tapping the route in the history section). The clear all button allows you to empty the route history.
Note: The `clear all` button only clears route history, it does not remove the routes, if you wish to remove routes use the [Routes Page](#routes-page).  
![](images/manual/overview.png)
![](images/manual/overview-cleared.png)

---

# Devices Page

The page for selecting which device to interact with. The selected device will appear in the title bar on all app pages. A device can only be selected when it is connected, the selected device will be highlighted. The cog next to the device can be touched to open the device settings for the breadcrumb datafield.  
![](images/manual/devices.png)

---

# App Settings Page

Configure the app settings  
![](images/manual/appsettings.png)
![](images/manual/appsettings-custom.png)

### Phone Hosted Tile Server

The phone hosts a tile server for the watch to query tiles from. The tiles downloaded are cached locally on the phone for use with offline map support.

Tile Server Enabled - enable/disable the tile server. You may want to disable the tile server if you are using a templated url on the watch, and not using the companion app to host offline tiles. This will remove some notifications, and not start the tile server process.  
Add Custom Server - Manually configure a tile server for use on the companion app, this tile server will be added to the  'Select Server' dropdown.  
Select Server - Select the tile server you wsh the companion app to serve tiles from.
Auth Token - will be used for any tile server that specifies '{authToken}' in the template url.

---

# Device Settings Page

This page is able to be accessed from two locations, the overview page (by clicking device settings), or the devices page (by clicking the settings cog next to the device). The device settings page is for configuring the on watch settings, it is similar to garmins connectiq settings, but more cusotomized eg. colour pickers. It also supports editing the per route settings (array settings are currently not suported through connectiq settings). For information on each of the settings see [Connect Iq Settings](https://github.com/pauljohnston2025/breadcrumb-garmin/blob/master/settings.md#garmin-settings-connect-iq-store)  
All the settings from connectiq are available, and the routes can be edited (unlike on the Garmin connect settings).   
![](images/manual/devicesettings.png)
![](images/manual/devicesettings-routes.png)
![](images/manual/devicesettings-routes-edit.png)
![](images/manual/devicesettings-colours.png)
![](images/manual/devicesettings-colours-rgb.png)

---

# Map View Page

A map view for displaying the routes that have been imported, preview a route before sending it to your device. 

Feature still in beta:
Known issues:
* map panning does not work

Features to come:
* cache tile layers around a route
* better ui
* cache tile layers for any panned to area
* tile server attribution
* maybe display route name on the map like the watch does

What does work:
* zooming in and out
* displaying route on map

![](images/manual/mapview-noroute.png)
![](images/manual/mapview-nolevation.png)
![](images/manual/mapview-elevation.png)

---

# Storage Info Page

Shows storage information, and allows removal of each storage type. Drag down from the top to refresh. 
The tiles server section shows the storage used by the cached tiles. Deleting tiles will only remove the cached tiles, not the tile server itself. see [App Settings](#app-settings-page) if you wish to remove custom tile servers.
Note: All routes deletion will remove all routes if you click the bin icon. This is not reversible, use with care.  
![](images/manual/storageinfo.png)
![](images/manual/storageinfo-loading.png)

---

# Routes Page

Shows all of the routes that have been added see [Adding A Gpx Route To The Watch](#adding-a-gpx-route-to-the-watch).  
![](images/manual/routes.png)  

Icons can be clicked to do the following:

* Pencil - Edit the route name
* Pin - Show the route on [Map View Page](#map-view-page)
* Play - Send the route to the watch
* Bin - Delete the route and associated storage

---

# Device Select Page

Appears whenever there is not an active device and we try and communicate with a device. The device can only be selected if it is connected. The active device is show in the title bar and on the devices page.       
![](images/manual/deviceselect-not-connected.png)
![](images/manual/deviceselect-connected.png)

---

# Debug Info Page

Shows all of the app logs, to aid in debugging whilst away from a computer. Please include screenshots or an export of this page when making a bug report. Ensure you obfuscate any personal information such as locations or tile server calls (location can be roughly calculated from tile coordinates).
![](images/manual/debugpage.png)

---
