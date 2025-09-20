# Prerequisites

The [garmin connect app](https://play.google.com/store/apps/details?id=com.garmin.android.apps.connectmobile&hl=en_AU) must be installed to send routes to your device or configure the device settings. The tile server cannot be used without installing garmin connect. A warning will be show on the device selection screen, and a prompt when you open the app if garmin connect is not installed or up to date.  
The watch app will also need a bluetooth connection with the phone in order to send routes and load map tiles from the companion app (or directly from internet);


# Adding A Gpx Route To The Watch

From Google Maps - You can create some directions and the click 'Share Directions'. The breadcrumb app should be listed as an app that you can share the directions too. Sometimes it is hidden under multiple 'more' buttons.  
Import a file - Open the breadcrumb app and select 'Import GPX', a file browser will be launched that you can select the route from.  
Open A Gpx File - When opening a gpx file select 'open with' and pick the breadcrumb app.  
Komoot - Similar to google maps, from the komoot app got to saved routes, select a route and `share with link`. The breadcrumb app should appear in the available app list to share with. You can also download the gpx route and open it through other means.  
WorldTopoMap App - Go To the tracks section, and click the info button on a track. There is then a `...` menu at the top. Select share, and then the breadcrumb app.
Trail Router - With a route open cli share, select `share gpx` or `open gpx`, then select breadrumb from the app menu.
 

The `ShareWith` functionality should work with any app that shares a gpx route. Please let me know if there is an app that does not work and I will try to add support for it (if I can access the app without a subscription).

Once added routes can be seen in the [Routes Page](#routes-page)

# Overview Page

The overview page is the main entrypoint of the app, it allows you to import a gpx file from your local storage, or configure a connected devices settings. The route history section shows when each route has been loaded onto the device, and allows a quick way to load routes from the past again (by tapping the route in the history section). The clear all button allows you to empty the route history.
Like the routes page the buttons are.  
* Pin - Show the route on [Map View Page](#map-view-page)
* Play - Send the route to the watch
* Bin - Delete the history item


Note: The `clear all` button only clears route history, it does not remove the routes, if you wish to remove routes use the [Routes Page](#routes-page).  
![](images/manual/overview.png)
![](images/manual/overview-cleared.png)

---

# Devices Page

The page for selecting which device to interact with. The selected device will appear in the title bar on all app pages. A device can only be selected when it is connected, the selected device will be highlighted. The cog next to the device can be touched to open the device settings for the breadcrumb datafield.  
![](images/manual/devices.png)

---

# App Settings Page

Configure the app settings. Note some of the tile server settings will clear the maps cache on the watch, use with care if you do not want to loose cached tiles on the watch.  
![](images/manual/appsettings.png)
![](images/manual/appsettings-custom.png)

### Phone Hosted Tile Server

The phone hosts a tile server for the watch to query tiles from. The tiles downloaded are cached locally on the phone for use with offline map support. To stop the tile server (and stop getting the 'tile server running' notifications), you need to close the app, not just background it. ie. open recent apps draw on android and swipe the app away to close it.    

**Tile Server Enabled** - enable/disable the tile server. You may want to disable the tile server if you are using a templated url on the watch, and not using the companion app to host offline tiles. This will remove some notifications, and not start the tile server process.
**Tile Type** - Different ways of sending tiles to the watch, note: full colour may need a smaller tile size on the watch (64 was too large in some of my testing).  
**Add Custom Server** - Manually configure a tile server for use on the companion app, this tile server will be added to the  'Select Server' dropdown.  
**Select Server** - Select the tile server you wsh the companion app to serve tiles from.
**Auth Token** - will be used for any tile server that specifies '{authToken}' in the template url.

### Routes

**Coordinates Point Limit** - The maximum number of coordinates that will be sent to the watch, each coordinate is a latitude/longitude/altitude reading. A larger number of coordinates will result in a smoother route drawing, but will increase cpu and memory requirements on the watch.     
**Turn Point Limit** - The maximum number of turn-by-turn coordinates that will be sent to the watch, each coordinate is an index in the coordinates and a direction to turn. `Turn points` are extracted frm gpx waypoint data, or can be generated using `mock directions`. The gpx file should have a route or track, and also include waypoints for each turn. Komoot directions shared by url are special cased, and will include directions also.   
**Mock Directions** - Attempt to calculate directions from the coordinates of the route only, this setting only applies if the route loaded does not already contain directions (indicated by direction icon next to the route label).    

`Turn Point Limit` must be less than or equal `Coordinates Point Limit`, the settings will assert this when you change them.

Through testing on my device I have found that `Coordinates Point Limit` should not exceed ~400 to ensure the full route can be sent to the watch over bluetooth. A lower limit will be needed if there are more routes since each point has associated memory and cpu considerations (watchdog), the watch app will crash if either are exceeded.  
When using `Turn Point Limit`, `Coordinates Point Limit` should be lowered to account for the extra memory and cpu taken to process the upcoming turns.   
Do not forget when increasing these limits that the users current track they are moving along must also be considered. Once the track points fill up it has essentially the same memory/cpu cost as a route with that many points.  

When tweaking these settings I suggest loading 1 extra route on the watch to simulate a fully populated track, then go for a short walk to test stability.  

Ensure to enable [Turn Alerts Distance (m)](https://github.com/pauljohnston2025/breadcrumb-garmin/blob/master/settings.md#turn-alerts-distance-m) on the watch when sending turn directions to ensure the watch processes them.  

The best way I have found to create a route with directions is komoot, it allows you to import any gpx and then convert it to turn-by-turn. Once the route is saved in komoot its as simple as sharing the url to the breadcrumb app (saved routes/<desired route>/share/share with link). You can also use site like https://www.plotaroute.com/routeplanner to create a route that will have the correct waypoints for use with turn-by-turn. Note, importing routes seems to be a paid thing so this only works with freshly created routes.

---

# Profiles Page

Allows saving app and watch settings for later use, and allows quick switching between different settings profiles for different activity types. Profiles can be created from the current settings, or imported from json. See the [profile examples](profiles.md) for importing. When importing from json, you will be prompted for auth tokens for the app and watch when needed (if the tile server requires auth).  
Please share your created profiles as a [github issue](https://github.com/pauljohnston2025/breadcrumb-mobile/issues/new). Ensure that any sensitive data is removed before sharing. Include details such as what activity you use it for and what watch your using. I will add some of these as examples to help other users easily find them, it will also help me see how different users are using the app (and determine what features are more important).  

![](images/manual/profiles.png)
![](images/manual/profile-edit.png)
![](images/manual/profiles-import-json.png)

Icons can be clicked to do the following:

* Pencil - Edit the profile name and optionally reload the settings from the app or watch to update the profile to the currently configured settings.  
* Play - Apply the profiles settings to the watch and companion app.
* Copy - Copy the profile json to clipboard for sharing with other users, or saving for later use. Please ensure no private information such as auth tokens are included when sharing the profile json. 
* Bin - Delete the profile.

Steps to create a profile from a currently configured watch and companion app
1. Click the 'create new profile' button
1. Give the profile a name and click save 

Steps to import a profile from json
1. Click the 'import from json' button
1. Paste the json profile into the text box
1. Set any auth tokens
1. Click save

Steps to update a profile
1. Load the profile using the play button (this puts the profile onto the watch and companion app)
1. Edit settings on the watch and/or the companion app. Eg. Change the tile server on the companion app and the track colour on the watch app. The watch settings can be edited directly on the watch, through garmin connect or by using the companion app.  
1. Once all settings edits are complete and you are happy, click the pencil icon on the profile you wish to update. This will generally be the profile you started with but can be any profile.
1.  Click the 'load watch settings' toggle and/or the 'load app settings' toggle.
1.  Click save. The new settings will be pulled from the watch and/or the app depending on the options chosen in the previous step.



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

Functions:

Not all functions are available all the time, eg. route Features are removed when no route is being previewed.

* Cross - Close the currently previewed route, and just show the map view.
* Mountains - Show/hide the route elevation data
* Watch and Crosshair - Send the 'return to user' command to the watch, clears any current zoom or fixed location on the watch.
* Crosshair - Move the maps view to the users current location 


* Watch and Eye - Send the currently viewed location to the watch, this is to preview the area on the watch.
* Plus - Zoom in
* Minus - Zoom out


* Download - Download the currently viewed map area for offline map routing
* Watch and Download - Start caching the current map area on the watch. Note: This only works if the watch is configured to download a bounding box, if the watch is set to download the route only it will download the map tiles for the route that's currently loaded on the watch *NOT* the route being previewed on the phone.


![](images/manual/map-just-route.png)
![](images/manual/map-route-and-elevation.png)
![](images/manual/map-caching.png)
![](images/manual/map-no-route.png)
![](images/manual/map-no-route2.png)

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
