/*
 * StartServers.java
 *
 * Created on July 21, 2007, 11:32 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package sagetcpserver;

import gkusnick.sagetv.api.*;
import gkusnick.sagetv.api.MediaFileAPI.MediaFile;
import java.util.*;
import sagetcpserver.utils.SageLogger;
import sage.SageTVPlugin;
import sage.SageTVPluginRegistry;


/**
 * This is where SageServer starts, from which the individual SageTCPServer's 
 * will be created.
 * 
 * @author Rob
 */
public final class StartServers implements SageTVPlugin {
    
    public static final String VERSION = "2.3.0";
    public static String Password = "";
    private TCPServerMedia MediaServer = null;
    private ArrayList<TCPServerPlayer> Players = new ArrayList<TCPServerPlayer>();
    private SageTVPluginRegistry Registry;


    private API SageApi = API.apiNullUI;
    private FavoriteAPI sageApiFavorite = new FavoriteAPI(SageApi);
    private MediaFileAPI sageApiMediaFile = new MediaFileAPI(SageApi);
    private SystemMessageAPI systemMessageApi = new SystemMessageAPI(SageApi);
    private SageLogger Logger = new SageLogger();
    static public final String OPT_PREFIX = "sageTCPServer/";
    static public final String OPT_PREFIX_NAME = "sagex/uicontexts/";
    static public ArrayList<String> configSettings = new ArrayList<String>();
    static public ArrayList<Integer> listOfAllPorts = new ArrayList<Integer>();
    static public ArrayList<Integer> listOfClientPorts = new ArrayList<Integer>();
    static public ArrayList<Integer> listOfExtenderPorts = new ArrayList<Integer>();
    static public ArrayList<String> listOfClients = new ArrayList<String>();
    static public ArrayList<String> listOfClientNames = new ArrayList<String>();
    static public ArrayList<String> listOfExtenderIPs = new ArrayList<String>();
    static public ArrayList<String> listOfExtenderNames = new ArrayList<String>();
    static public ArrayList<String> listOfExtenderMacIDs = new ArrayList<String>();
    static public ArrayList<ClientType> listOfExtenderTypes = new ArrayList<ClientType>();
    static public boolean autoReboot = true;
    static public int ServerPort = 0;

    /** Creates a new instance of StartServers */
    public StartServers(SageTVPluginRegistry reg) {
        Logger.Message("Plugin constructor of version " + VERSION);
        Registry = reg;

        String[] settings = new String[] {"autoReboot", "debugOn", "password", "server",
            "extendersName", "extendersPort", "clientsName", "clientsPort",
            "button1", "button2", "button3", "button4", "button5", "button6", "button7",
            "streamingVLCPort", "streamingVLCType",
            "streamingVLCOptions", "streamingVLCPath", "streamingTranscodeOptions",
            "streamingTranscodeOptions1", "streamingTranscodeOptions2",
            "streamingTranscodeOptions3", "streamingTranscodeOptions4",
            "streamingTranscodeOptions5", "streamingTranscodeOptions6"};

        for (int i = 0; i < settings.length; i++)
            configSettings.add(OPT_PREFIX + settings[i]);
   }

     /** Get the configuration from the sage.properties. */
    public void GetConfigInfo(){
        // Get port configuration information out of the sage.config file
        try {
            String returnVal = SageApi.configuration.GetProperty(OPT_PREFIX + "debugOn", "false");
            if (returnVal.equalsIgnoreCase("true")) {
                SageLogger.ShowDebug = true;
                Logger.Message("Debug messages on");
            }
            else {
                SageLogger.ShowDebug = false;
                Logger.Message("Debug messages off (received " + returnVal + ")");                
            }

            autoReboot = SageApi.configuration.GetProperty(OPT_PREFIX + "autoReboot", "true").equalsIgnoreCase("true");
            Password = SageApi.configuration.GetProperty(OPT_PREFIX + "password", "");

            returnVal = SageApi.configuration.GetProperty(OPT_PREFIX + "clients", null);
            if (returnVal != null && !returnVal.isEmpty()) upgrade(returnVal);
            else readCurrentSettings();
            subscribe();

            returnVal = SageApi.configuration.GetProperty(OPT_PREFIX + "streamingTranscodeOptions", null);
            if (returnVal != null && !returnVal.isEmpty()) SageMedia.StreamingTranscodeOptions = returnVal;

            returnVal = SageApi.configuration.GetProperty(OPT_PREFIX + "streamingTranscodeOptions1", null);
            if (returnVal != null && !returnVal.isEmpty()) SageMedia.StreamingTranscodeOptions1 = returnVal;

            returnVal = SageApi.configuration.GetProperty(OPT_PREFIX + "streamingTranscodeOptions2", null);
            if (returnVal != null && !returnVal.isEmpty()) SageMedia.StreamingTranscodeOptions2 = returnVal;

            returnVal = SageApi.configuration.GetProperty(OPT_PREFIX + "streamingTranscodeOptions3", null);
            if (returnVal != null && !returnVal.isEmpty()) SageMedia.StreamingTranscodeOptions3 = returnVal;

            returnVal = SageApi.configuration.GetProperty(OPT_PREFIX + "streamingTranscodeOptions4", null);
            if (returnVal != null && !returnVal.isEmpty()) SageMedia.StreamingTranscodeOptions4 = returnVal;

            returnVal = SageApi.configuration.GetProperty(OPT_PREFIX + "streamingTranscodeOptions5", null);
            if (returnVal != null && !returnVal.isEmpty()) SageMedia.StreamingTranscodeOptions5 = returnVal;

            returnVal = SageApi.configuration.GetProperty(OPT_PREFIX + "streamingTranscodeOptions6", null);
            if (returnVal != null && !returnVal.isEmpty()) SageMedia.StreamingTranscodeOptions6 = returnVal;

            returnVal = SageApi.configuration.GetProperty(OPT_PREFIX + "streamingVLCOptions", null);
            if (returnVal != null && !returnVal.isEmpty()) SageMedia.StreamingVLCOptions = returnVal;

            returnVal = SageApi.configuration.GetProperty(OPT_PREFIX + "streamingVLCPath", null);
            if (returnVal != null && !returnVal.isEmpty()) SageMedia.StreamingVLCPath = returnVal;

            returnVal = SageApi.configuration.GetProperty(OPT_PREFIX + "streamingVLCPort", null);
            if (returnVal != null && !returnVal.isEmpty()) SageMedia.StreamingPort = Integer.valueOf(returnVal);

            returnVal = SageApi.configuration.GetProperty(OPT_PREFIX + "streamingVLCType", null);
            if (returnVal != null && !returnVal.isEmpty()) SageMedia.StreamingType = returnVal;

            returnVal = SageApi.configuration.GetProperty(OPT_PREFIX + "button1", null);
            if (returnVal != null && !returnVal.isEmpty()) SageMedia.Button1 = returnVal;

            returnVal = SageApi.configuration.GetProperty(OPT_PREFIX + "button2", null);
            if (returnVal != null && !returnVal.isEmpty()) SageMedia.Button2 = returnVal;

            returnVal = SageApi.configuration.GetProperty(OPT_PREFIX + "button3", null);
            if (returnVal != null && !returnVal.isEmpty()) SageMedia.Button3 = returnVal;

            returnVal = SageApi.configuration.GetProperty(OPT_PREFIX + "button4", null);
            if (returnVal != null && !returnVal.isEmpty()) SageMedia.Button4 = returnVal;

            returnVal = SageApi.configuration.GetProperty(OPT_PREFIX + "button5", null);
            if (returnVal != null && !returnVal.isEmpty()) SageMedia.Button5 = returnVal;

            returnVal = SageApi.configuration.GetProperty(OPT_PREFIX + "button6", null);
            if (returnVal != null && !returnVal.isEmpty()) SageMedia.Button6 = returnVal;

            returnVal = SageApi.configuration.GetProperty(OPT_PREFIX + "button7", null);
            if (returnVal != null && !returnVal.isEmpty()) SageMedia.Button7 = returnVal;

            //returnVal = SageApi.configuration.GetProperty(OPT_PREFIX + "streamingRTSPType", null);
            //if (returnVal.isEmpty()) SageServer.StreamingRTSPType = returnVal;
         }
        catch (Throwable t) {
            Logger.Error(t);
        }
    }

    public void start() {
        Logger.Message("Start");
        GetConfigInfo();
    }

    public void subscribe(){ // Only start events when needed?
        Logger.Message("Subscribing to events");
        Registry.eventSubscribe(this, "ClientConnected");
        Registry.eventSubscribe(this, "ClientDisconnected");
        Registry.eventSubscribe(this, "ConflictStatusChanged");
        Registry.eventSubscribe(this, "FavoriteAdded");
        Registry.eventSubscribe(this, "FavoriteModified");
        Registry.eventSubscribe(this, "FavoriteRemoved");
        Registry.eventSubscribe(this, "PlaybackFinished");
        Registry.eventSubscribe(this, "PlaybackStarted");
        Registry.eventSubscribe(this, "PlaybackStopped");
        Registry.eventSubscribe(this, "MediaFileImported");
        Registry.eventSubscribe(this, "MediaFileRemoved");
        Registry.eventSubscribe(this, "RecordingCompleted");
        Registry.eventSubscribe(this, "RecordingScheduleChanged");
        Registry.eventSubscribe(this, "RecordingStarted");
        Registry.eventSubscribe(this, "SystemMessagePosted");
    }

    public void stop() {
        Logger.Message("Stopping");
        MediaServer.shutdown();
        for (TCPServerPlayer player : Players) player.shutdown();

        MediaServer = null;
        Players = new ArrayList<TCPServerPlayer>();
    }

    public void destroy() { }

    public String[] getConfigSettings() {
        String[] settings = new String[configSettings.size()];

        for (int i = 0; i < configSettings.size(); i++)
            settings[i] = configSettings.get(i);

        return settings;
    }

    public String getConfigValue(String setting) {
        if(setting.equalsIgnoreCase(OPT_PREFIX + "streamingVLCOptions"))
            return SageApi.configuration.GetProperty(setting, SageMedia.StreamingVLCOptions);
        else if(setting.equalsIgnoreCase(OPT_PREFIX + "streamingVLCPath"))
            return SageApi.configuration.GetProperty(setting, SageMedia.StreamingVLCPath);
        else if(setting.equalsIgnoreCase(OPT_PREFIX + "streamingVLCPort"))
            return SageApi.configuration.GetProperty(setting, String.valueOf(SageMedia.StreamingPort));
        else if(setting.equalsIgnoreCase(OPT_PREFIX + "streamingVLCType"))
            return SageApi.configuration.GetProperty(setting, SageMedia.StreamingType);
        else if(setting.equalsIgnoreCase(OPT_PREFIX + "streamingTranscodeOptions"))
            return SageApi.configuration.GetProperty(setting, SageMedia.StreamingTranscodeOptions);
        else if(setting.equalsIgnoreCase(OPT_PREFIX + "streamingTranscodeOptions1"))
            return SageApi.configuration.GetProperty(setting, SageMedia.StreamingTranscodeOptions1);
        else if(setting.equalsIgnoreCase(OPT_PREFIX + "streamingTranscodeOptions2"))
            return SageApi.configuration.GetProperty(setting, SageMedia.StreamingTranscodeOptions2);
        else if(setting.equalsIgnoreCase(OPT_PREFIX + "streamingTranscodeOptions3"))
            return SageApi.configuration.GetProperty(setting, SageMedia.StreamingTranscodeOptions3);
        else if(setting.equalsIgnoreCase(OPT_PREFIX + "streamingTranscodeOptions4"))
            return SageApi.configuration.GetProperty(setting, SageMedia.StreamingTranscodeOptions4);
        else if(setting.equalsIgnoreCase(OPT_PREFIX + "streamingTranscodeOptions5"))
            return SageApi.configuration.GetProperty(setting, SageMedia.StreamingTranscodeOptions5);
        else if(setting.equalsIgnoreCase(OPT_PREFIX + "streamingTranscodeOptions6"))
            return SageApi.configuration.GetProperty(setting, SageMedia.StreamingTranscodeOptions6);
        else if(setting.equalsIgnoreCase(OPT_PREFIX + "button1"))
            return SageApi.configuration.GetProperty(setting, SageMedia.Button1);
        else if(setting.equalsIgnoreCase(OPT_PREFIX + "button2"))
            return SageApi.configuration.GetProperty(setting, SageMedia.Button2);
        else if(setting.equalsIgnoreCase(OPT_PREFIX + "button3"))
            return SageApi.configuration.GetProperty(setting, SageMedia.Button3);
        else if(setting.equalsIgnoreCase(OPT_PREFIX + "button4"))
            return SageApi.configuration.GetProperty(setting, SageMedia.Button4);
        else if(setting.equalsIgnoreCase(OPT_PREFIX + "button5"))
            return SageApi.configuration.GetProperty(setting, SageMedia.Button5);
        else if(setting.equalsIgnoreCase(OPT_PREFIX + "button6"))
            return SageApi.configuration.GetProperty(setting, SageMedia.Button6);
        else if(setting.equalsIgnoreCase(OPT_PREFIX + "button7"))
            return SageApi.configuration.GetProperty(setting, SageMedia.Button7);
                else if(setting.equalsIgnoreCase(OPT_PREFIX + "clientsName")){
            StringBuilder sb = new StringBuilder();

            for (int i = 0; i < listOfClientNames.size(); i++){
                if (i > 0) sb.append(",");
                sb.append(listOfClientNames.get(i));
            }

            return sb.toString();
        }
        else if(setting.equalsIgnoreCase(OPT_PREFIX + "clientsPort")){
            StringBuilder sb = new StringBuilder();
 
            for (int i = 0; i < listOfClientPorts.size(); i++){
                if (i > 0) sb.append(",");
                sb.append(listOfClientPorts.get(i));
            }

            return sb.toString();
        }
        else if(setting.equalsIgnoreCase(OPT_PREFIX + "extendersName")){
            StringBuilder sb = new StringBuilder();

            for (int i = 0; i < listOfExtenderNames.size(); i++){
                if (i > 0) sb.append(",");
                sb.append(listOfExtenderNames.get(i));
            }

            return sb.toString();
        }
        else if(setting.equalsIgnoreCase(OPT_PREFIX + "extendersPort")){
            StringBuilder sb = new StringBuilder();

            for (int i = 0; i < listOfExtenderPorts.size(); i++){
                if (i > 0) sb.append(",");
                sb.append(listOfExtenderPorts.get(i));
            }

            return sb.toString();
        }
        else if(setting.equalsIgnoreCase(OPT_PREFIX + "autoReboot"))
            return SageApi.configuration.GetProperty(setting, String.valueOf(autoReboot));
        else return SageApi.configuration.GetProperty(setting, "");
    }

    // client/extender could use this instead of comma limited lists...
    // The profiles are another cadidate...
    public String[] getConfigValues(String setting) { return null; }

    public int getConfigType(String setting) {
        if (setting.equalsIgnoreCase(OPT_PREFIX + "autoReboot") ||
                setting.equalsIgnoreCase(OPT_PREFIX + "debugOn")) return CONFIG_BOOL;
        else if (setting.equalsIgnoreCase(OPT_PREFIX + "password")) return CONFIG_PASSWORD;
        else return CONFIG_TEXT;
    }

    public void setConfigValue(String setting, String value) {
        Logger.Message("Set config value: " + setting + " = " + value);
        String oldValue = SageApi.configuration.GetProperty(setting, null);
        // Should handle port reallocation: client <--> server <--> extender
        if (!value.equalsIgnoreCase(oldValue)){
            if (setting.equalsIgnoreCase(OPT_PREFIX + "server")){
                StringTokenizer strTok = new StringTokenizer(value, ",");
                listOfAllPorts.remove(listOfAllPorts.indexOf(MediaServer.Port));
                MediaServer.shutdown();
                int port = Integer.parseInt(strTok.nextToken().trim());
                startMediaServer(port);
                SageApi.configuration.SetProperty(setting, String.valueOf(port));
                return;
            }
            else if(setting.equalsIgnoreCase(OPT_PREFIX + "extendersName")){
                String name, oldName, server;
                StringTokenizer strTok = new StringTokenizer(value, ",");
                int index = 0;

                while (strTok.hasMoreTokens()) {
                    name = strTok.nextToken().trim();
                    server = listOfExtenderMacIDs.get(index);

                    oldName = SageApi.configuration.GetProperty(OPT_PREFIX_NAME + server + "/name", server);;

                    if (!oldName.equalsIgnoreCase(name)){
                        SageApi.configuration.SetProperty(OPT_PREFIX_NAME + server + "/name", name);
                        listOfExtenderNames.set(index, name);
                    }
                    index++;
                }

                return;
            }
            else if(setting.equalsIgnoreCase(OPT_PREFIX + "extender")){
                boolean needUpdate = false;
                String server;
                StringTokenizer strTok = new StringTokenizer(value, ",");
                int index = -1, oldPort = 9260, port = 9260;

                while (strTok.hasMoreTokens()) {
                    port = Integer.parseInt(strTok.nextToken().trim());
                    oldPort = listOfExtenderPorts.get(index);
                    server = listOfExtenderMacIDs.get(index++);

                    if (oldPort != port){
                        Players.get(index).shutdown();
                        Players.remove(index);
                        listOfAllPorts.remove(listOfAllPorts.indexOf(oldPort));
                        ClientType extenderType = listOfExtenderTypes.get(index);
                        String name = listOfExtenderNames.get(index);
                        String extenderIP = listOfExtenderIPs.get(index);
                        listOfExtenderIPs.remove(index);
                        listOfExtenderMacIDs.remove(index);
                        listOfExtenderPorts.remove(index);
                        listOfExtenderNames.remove(index);
                        listOfExtenderTypes.remove(index);
                        startExtender(port, server, false, extenderType, extenderIP, name);
                        needUpdate = true;
                    }
                }

                if (needUpdate) updateExtenderConfig();
                return;
            }
            else if(setting.equalsIgnoreCase(OPT_PREFIX + "clientsName")){
                String oldName, name, server;
                StringTokenizer strTok = new StringTokenizer(value, ",");
                int index = 0;

                while (strTok.hasMoreTokens()) {
                    name = strTok.nextToken().trim();
                    server = listOfClients.get(index);

                    oldName = SageApi.configuration.GetProperty(OPT_PREFIX_NAME + server + "/name", server);;

                    if (!oldName.equalsIgnoreCase(name)){
                        SageApi.configuration.SetProperty(OPT_PREFIX_NAME + server + "/name", name);
                        listOfClientNames.set(index, name);
                    }
                    index++;
                }

                return;
            }
            else if(setting.equalsIgnoreCase(OPT_PREFIX + "clientsPort")){
                boolean needUpdate = false;
                String context, server;
                StringTokenizer strTok = new StringTokenizer(value, ",");
                int index = 0, oldPort, port;

                while (strTok.hasMoreTokens()) {
                    port = Integer.parseInt(strTok.nextToken().trim());
                    oldPort = listOfClientPorts.get(index);
                    server = listOfClients.get(index++);

                    if (oldPort != port){
                        context = Players.get(index).uiContext;
                        Players.get(index).shutdown();
                        Players.remove(index);
                        listOfAllPorts.remove(listOfAllPorts.indexOf(oldPort));
                        listOfClients.remove(index);
                        listOfClientPorts.remove(index);
                        listOfClientNames.remove(index);
                        startClient(port, server, context);
                        needUpdate = true;
                    }
                }

                if (needUpdate) updateClientConfig();
                return;
            }
            else if(setting.equalsIgnoreCase(OPT_PREFIX + "debugOn")){
                if (value.equalsIgnoreCase("true")) {
                    SageLogger.ShowDebug = true;
                    Logger.Message("Debug messages on");
                    // Might want to set SageTV's debug on too...
                }
                else {
                    SageLogger.ShowDebug = false;
                    Logger.Message("Debug messages off (received " + value + ")");
                }
            }
            else if(setting.equalsIgnoreCase(OPT_PREFIX + "autoReboot"))
                autoReboot = value.equalsIgnoreCase("true");

            SageApi.configuration.SetProperty(setting, value);
        }
    }

    public void setConfigValues(String setting, String[] values) { }

    public String[] getConfigOptions(String setting) { return null; }

    public String getConfigHelpText(String setting) {
        String[] help = new String[] {"Auto reboot all the extenders on the restart of this plugin",
        "Enable for debugging purposes", "Password protect the access",
        "Port (default = 9250) to access the various lists and more",
        "The format is Name1,Name2 as in Kitchen,Living room",
        "The format is port1,port2 as in 9260,9261",
        "Used for the server's UI in service mode or other software clients, format: Name1,Name2 as in Kitchen,Living room",
        "Used for the server's UI in service mode or other software clients, format: port1,port2 as in 9262,9263",
        "The format: Name,Command as in Ratio,CMD:Aspect Ratio Toggle",
        "The format: Name,Command as in Ratio,CMD:Aspect Ratio Toggle",
        "The format: Name,Command as in Ratio,CMD:Aspect Ratio Toggle",
        "The format: Name,Command as in Ratio,CMD:Aspect Ratio Toggle",
        "The format: Name,Command as in Ratio,CMD:Aspect Ratio Toggle",
        "The format: Name,Command as in Ratio,CMD:Aspect Ratio Toggle",
        "The format: Name,Command as in Ratio,CMD:Aspect Ratio Toggle",
        "Recommended ports are 554, 80, 8080, 7070", "Only rtsp seems to work properly",
        "Options when launching VLC",
        "Full path to the VLC executable, default = C:/Program Files/VideoLAN/VLC/vlc.exe",
        "Default VLC transcoding profile", "VLC transcoding profile 1",
        "VLC transcoding profile 2", "VLC transcoding profile 3",
        "VLC transcoding profile 4", "VLC transcoding profile 5", "VLC transcoding profile 6"};

        return help[configSettings.indexOf(setting)];
    }

    public String getConfigLabel(String setting) {
        String[] labels = new String[] {"Auto reboot", "Debug", "Password", "Port for the server",
        "Extender(s) name", "Extender(s) port", "Client(s) name", "Client(s) port",
        "Custom button 1", "Custom button 2", "Custom button 3", "Custom button 4",
        "Custom button 5", "Custom button 6", "Custom button 7",
        "VLC streaming port", "VLC streaming type", "VLC launch options",
        "VLC path", "VLC profile", "VLC profile 1", "VLC profile 2", "VLC profile 3",
        "VLC profile 4", "VLC profile 5", "VLC profile 6"};

        return labels[configSettings.indexOf(setting)];
    }

    public void resetConfig() { Logger.Message("Reset config");  }

    public void sageEvent(String eventName, Map eventVars) {
        Logger.Message("Event: " + eventName);
        if (eventName.equalsIgnoreCase("ClientConnected")){
            String ip = (String) eventVars.get("IPAddress");
            String Mac = (String) eventVars.get("MACAddress");
            Logger.Message("A client connected " + ip + ":" + Mac);

            if (Mac == null) updateClients(ip);
            else updateExtender(Mac, ip);
        }
        else if (eventName.equalsIgnoreCase("ClientDisconnected")){
            //String IP = (String) eventVars.get("IPAddress");
            //String Mac = (String) eventVars.get("MACAddress");
            Logger.Message("A client disconnected " + eventVars.get("IPAddress")
                    + ":" + eventVars.get("MACAddress"));
        }
        else if (eventName.equalsIgnoreCase("ConflictStatusChanged"))
            MediaServer.getConflicts();
        else if (eventName.equalsIgnoreCase("FavoriteAdded")){
            Object obj = eventVars.get("Favorite");
            MediaServer.sendFavorite(sageApiFavorite.Wrap(obj), "Add");
        }
        else if (eventName.equalsIgnoreCase("FavoriteModified")){
            Object obj = eventVars.get("Favorite");
            MediaServer.sendFavorite(sageApiFavorite.Wrap(obj), "Mod");
        }
        else if (eventName.equalsIgnoreCase("FavoriteRemoved")){
            Object obj = eventVars.get("Favorite");
            MediaServer.sendFavorite(sageApiFavorite.Wrap(obj), "Del");
        }
        else if (eventName.equalsIgnoreCase("MediaFileImported")){
            Object obj = eventVars.get("MediaFile");
            MediaServer.sendMF(sageApiMediaFile.Wrap(obj), "Add");
        }
        else if (eventName.equalsIgnoreCase("MediaFileRemoved")){
            Object obj = eventVars.get("MediaFile");
            MediaServer.sendMF(sageApiMediaFile.Wrap(obj), "Del");
        }
        else if (eventName.equalsIgnoreCase("PlaybackFinished")){
            Object obj = eventVars.get("MediaFile");
            String context = (String) eventVars.get("UIContext");
            sendPlayback(context, sageApiMediaFile.Wrap(obj), "Finish");
        }
        else if (eventName.equalsIgnoreCase("PlaybackStarted")){
            Object obj = eventVars.get("MediaFile");
            String context = (String) eventVars.get("UIContext");
            sendPlayback(context, sageApiMediaFile.Wrap(obj), "Start");
        }
        else if (eventName.equalsIgnoreCase("PlaybackStopped")){
            Object obj = eventVars.get("MediaFile");
            String context = (String) eventVars.get("UIContext");
            sendPlayback(context, sageApiMediaFile.Wrap(obj), "Stop");
        }
        else if (eventName.equalsIgnoreCase("RecordingCompleted"))
            MediaServer.getNumberOfCurrentlyRecordingFiles();
        else if (eventName.equalsIgnoreCase("RecordingScheduleChanged"))
            MediaServer.getUpcomingRecordingsList();
        else if (eventName.equalsIgnoreCase("RecordingStarted"))
            MediaServer.getNumberOfCurrentlyRecordingFiles();
        else if (eventName.equalsIgnoreCase("SystemMessagePosted")){
            Object obj = eventVars.get("SystemMessage");
            MediaServer.sendSystemMessage(systemMessageApi.Wrap(obj));
        }
    }

    private void readCurrentSettings(){
        Logger.Message("Read current settings");
        Integer port = 9250;
        StringTokenizer strTok;
        String server;
        String returnVal = SageApi.configuration.GetProperty(OPT_PREFIX + "server", "9250");

        if (!returnVal.isEmpty()) {
            boolean needUpdate = false;
            strTok = new StringTokenizer(returnVal, ",");

            while (strTok.hasMoreTokens())  {
                port = Integer.parseInt(strTok.nextToken().trim());
                if (ServerPort == 0) startMediaServer(port);
                else needUpdate = true;
            }

            if (needUpdate) SageApi.configuration.SetProperty(OPT_PREFIX + "server", String.valueOf(port));
        }
        else {
            startMediaServer(port);
            SageApi.configuration.SetProperty(OPT_PREFIX + "server", String.valueOf(port));
        }

        returnVal = SageApi.configuration.GetProperty(OPT_PREFIX + "extender", null);
        if (returnVal != null && !returnVal.isEmpty()) {
            boolean needUpdate = false;
            strTok = new StringTokenizer(returnVal, ",");

            while (strTok.hasMoreTokens()) {
                StringTokenizer strTok2 = new StringTokenizer(strTok.nextToken(), ".");
                server = strTok2.nextToken().toLowerCase().trim();
                port = Integer.parseInt(strTok2.nextToken().trim());
                String extenderTypeText = SageApi.configuration.GetProperty(OPT_PREFIX_NAME + server + "/type", null);
                String extenderIP = SageApi.configuration.GetProperty(OPT_PREFIX_NAME + server + "/ip", null);
                String extenderName = SageApi.configuration.GetProperty(OPT_PREFIX_NAME + server + "/name", server);
                ClientType extenderType = (extenderTypeText == null ? ClientType.Hardware : ClientType.valueOf(extenderTypeText));

                if (!listOfExtenderMacIDs.contains(server)) startExtender(port, server, autoReboot, extenderType, extenderIP, extenderName);
                else needUpdate = true;
            }

            if (needUpdate) updateExtenderConfig();
        }

        returnVal = SageApi.configuration.GetProperty(OPT_PREFIX + "client", null);
        if (returnVal != null && !returnVal.isEmpty()) {
            boolean needUpdate = false;
            strTok = new StringTokenizer(returnVal, ",");

            while (strTok.hasMoreTokens()) {
                String serverPortPair = strTok.nextToken();

                if (serverPortPair.startsWith("/")){  // Remove the leading /
                    serverPortPair = serverPortPair.substring(1);
                    needUpdate = true;
                }

                StringTokenizer strTok2 = new StringTokenizer(serverPortPair, ":");
                server = strTok2.nextToken().toLowerCase().trim();
                port = Integer.parseInt(strTok2.nextToken().trim());

                if (!listOfClients.contains(server)) startClient(port, server, null);
                else needUpdate = true;
            }

            if (needUpdate) updateClientConfig();
        }
    }

    private void sendPlayback(String context, MediaFile mf, String type){
        for (TCPServerPlayer player : Players){
            if (player.uiContext.equalsIgnoreCase(context)){
                player.sendPlayback(mf, type);
                break;
            }
        }
    }

    private void updateClients(String ip){
        // Only need to do that when gathering the specific setting
        // Need to properly handle the client connect/disconnect/reconnect
        Logger.Message("Get UI context for the clients");
        String[] listOfExternalContextNames = SageApi.global.GetConnectedClients();

        if (listOfExternalContextNames.length > 0)
            Logger.Message("Number of external context: " + listOfExternalContextNames.length);
        else{
            Logger.Message("No external context!");
            return;
        }

        int port = 9260, index = -1;
        String[] parts;
        boolean needUpdate = false;

        for (String contextName : listOfExternalContextNames){
            Logger.Message("Context name: " + contextName);
            //if (contextName == null) continue; // How could it be null?
            parts = contextName.substring(1).split(":"); // Remve the leading /
            index = listOfClients.indexOf(parts[0]); // Could use the hostname instead of the IP...

            if (index == -1) { // Could we get multiple clients from one?
                Logger.Message("This is a new client");
                for (int i = 0; i < 20; i++){
                    port = 9260 + i;
                    if (!listOfAllPorts.contains(port)) break;
                }

                startClient(port, parts[0], contextName);
                needUpdate = true;
            }
            else{
                Logger.Message("This is an existing client");
                boolean startIt = true;
                for (TCPServerPlayer player : Players){
                    if (player.IP != null && player.IP.equalsIgnoreCase(parts[0])){
                        startIt = false;
                        //if (!ip.equalsIgnoreCase("127.0.0.1")){
                            Logger.Message("Set context for: " + contextName);
                            player.uiContext = contextName;
                        break;
                    }
                }

                if (startIt){
                    Logger.Message("Starting the new client: " + contextName);
                    TCPServerPlayer server = new TCPServerPlayer(contextName, port, parts[0], false, ClientType.Software);
                    Players.add(server);
                    Thread thread = new Thread(server);
                    thread.start();
                }
            }
        }

        if (needUpdate) updateClientConfig();
    }

    private void updateExtender(String contextName, String ip){
            boolean addedAny = false;
            int port = 9260;

            if (!listOfExtenderMacIDs.contains(contextName)) {
                for (int i = listOfExtenderPorts.size(); i < 20; i++){
                    port = 9260 + i;
                    if (!listOfAllPorts.contains(port)) break;
                }

                startExtender(port, contextName, false, ClientType.Hardware, ip, contextName);
                addedAny = true;
           }

            if (addedAny) updateExtenderConfig();
    }

    private void startClient(int port, String ip, String contextName){
        Logger.Message("Starting client");
        listOfAllPorts.add(port);
        listOfClientPorts.add(port);
        listOfClients.add(ip);

        TCPServerPlayer server = new TCPServerPlayer(contextName, port, ip, false, ClientType.Software);
        Players.add(server);
        Thread thread = new Thread(server);
        thread.start();

        String returnVal = SageApi.configuration.GetProperty(OPT_PREFIX_NAME + ip + "/name", null);
        if (returnVal != null && !returnVal.isEmpty())
            listOfClientNames.add(returnVal);
        else{
            listOfClientNames.add(ip);
            SageApi.configuration.SetProperty(OPT_PREFIX_NAME + ip + "/name", ip);
        }
    }

    private void startExtender(int port, String contextName, boolean initialStart, ClientType extenderType, String extenderIP, String name){
        Logger.Message("Starting extender");
        listOfAllPorts.add(port);
        listOfExtenderIPs.add(extenderIP);
        listOfExtenderPorts.add(port);
        listOfExtenderMacIDs.add(contextName);
        listOfExtenderTypes.add(extenderType);
        listOfExtenderNames.add(name);
        SageApi.configuration.SetProperty(OPT_PREFIX_NAME + contextName + "/name", name);

        TCPServerPlayer server = new TCPServerPlayer(contextName, port, extenderIP, initialStart, extenderType);
        Players.add(server);
        Thread thread = new Thread(server);
        thread.start();
    }

    private void startMediaServer(int port){
        Logger.Message("Starting media server");
        ServerPort = port;
        listOfAllPorts.add(port);

        MediaServer = new TCPServerMedia(port);
        Thread thread = new Thread(MediaServer);
        thread.start();
    }

    private void updateClientConfig(){
        StringBuilder prop = new StringBuilder();

        for (int i = 0; i < listOfClients.size(); i++){
            if (i > 0) prop.append(",");
            prop.append(listOfClients.get(i)).append(":");
            prop.append(listOfClientPorts.get(i));
        }

        SageApi.configuration.SetProperty(OPT_PREFIX + "client", prop.toString());
    }

    private void updateExtenderConfig(){
        StringBuilder prop = new StringBuilder();

        for (int i = 0; i < listOfExtenderPorts.size(); i++){
            if (i > 0) prop.append(",");
            prop.append(listOfExtenderMacIDs.get(i)).append(".");
            prop.append(listOfExtenderPorts.get(i));
            SageApi.configuration.SetProperty(OPT_PREFIX_NAME + listOfExtenderMacIDs.get(i) + "/type", listOfExtenderTypes.get(i).toString());
        }

        SageApi.configuration.SetProperty(OPT_PREFIX + "extender", prop.toString());
    }

    private void upgrade(String returnVal){
        int port = 9250;
        String server;
        StringTokenizer strTok = new StringTokenizer(returnVal, ",");

        while (strTok.hasMoreTokens()) {
            StringTokenizer strTok2 = new StringTokenizer(strTok.nextToken(), ".");
            server = strTok2.nextToken().toLowerCase().trim();
            port = Integer.parseInt(strTok2.nextToken().trim());

            if (server.equalsIgnoreCase("http") || server.equalsIgnoreCase("rtsp")){
                SageApi.configuration.SetProperty(OPT_PREFIX + "streamingVLCPort", String.valueOf(port));
                SageApi.configuration.SetProperty(OPT_PREFIX + "streamingVLCType", server);
            }
            else {
                if (server.equalsIgnoreCase("local")){
                    if (ServerPort == 0) startMediaServer(port);
                }
                else if (!listOfExtenderMacIDs.contains(server))
                    startExtender(port, server, false, ClientType.Hardware, null, server);
            }
        }

        if (ServerPort == 0){
            for (int i = 0; i < 20; i++){
                port = 9250 + i;
                if (!listOfAllPorts.contains(port)) break;
            }

            startMediaServer(port);
        }
        
        SageApi.configuration.SetProperty(OPT_PREFIX + "server", String.valueOf(ServerPort));
        SageApi.configuration.RemoveProperty(OPT_PREFIX + "clients");
        updateExtenderConfig();
    }
}
