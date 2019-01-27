/*
 * SageServer.java
 *
 * Created on July 22, 2007, 12:02 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package sagetcpserver;

import gkusnick.sagetv.api.*;
import gkusnick.sagetv.api.MediaFileAPI.MediaFile;
import gkusnick.sagetv.api.PlaylistAPI.Playlist;
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import sagetcpserver.messages.MessageType;
import sagetcpserver.messages.Message;
import sagetcpserver.utils.JSONUtil;
import sagetcpserver.utils.SageLogger;
import java.util.regex.Pattern;

/**
 * This is where the SageTCTPServer is created and all the procesing is handled.
 * 
 * @author Rob + Fonceur
 */
public final class SagePlayer implements Runnable {
    private static final char STX = (char)0x02;
    private static final char ETX = (char)0x03;
    private static final char DATA_SEPARATOR = (char)0x7c;
    private static final int DEFAULT_MAXITEMS = 25;

    private ArrayList<Message> incomingMessages = null;
    private boolean isInitialized = false;
    private boolean isXML = true;
    private BufferedReader inputBuffer = null;
    private Integer maxItems = 1000;
    private PlaylistAPI.Playlist playlist = null;
    private PrintWriter outputBuffer = null;
    private SageLogger logger = null;
    private String context = "";

    public ClientType clientType = ClientType.None;
    public int pingCount = 0;
    public MediaStore currentMediaFile = null;
    public Socket client = null;
    public String IP;

    /** Creates a new instance of SageServer */
    public SagePlayer(Socket newClient, String uiContext, MediaStore mf, ClientType newType, String ip) {
        logger = new SageLogger("(" + newClient.getPort() + ")");
        logger.Message("Adding a player for " + uiContext);
        this.client = newClient;
        this.clientType = newType;
        this.IP = ip;
        try{
            outputBuffer = new PrintWriter(new OutputStreamWriter(newClient.getOutputStream(), "UTF-8"), true);
            inputBuffer = new BufferedReader(new InputStreamReader(newClient.getInputStream(), "UTF-8"));
        }
        catch (IOException e) {
           logger.Error(e);
        }

        incomingMessages = new ArrayList<Message>();
        currentMediaFile = mf;
        this.context = uiContext;
        logger.Message("Done adding the server");
    }

    /** Start listening/transmitting on the new Sage TCP Server. */
    public void run() {
        boolean clientConnected = true;
        send(new Message(MessageType.VERSION, StartServers.VERSION));

        while (clientConnected) {
            clientConnected = getIncomingMessages();

            if (clientConnected) processIncomingMessages();
        }

        shutdown();
    }

    public void shutdown(){
        // close the socket, kill the listning thread, etc...
        try {
            client.close();
            client = null;
        } catch (Exception ex) {logger.Message(ex.getMessage()); }
    }

    /** Procees the incoming messages from the queue. */
    public void processIncomingMessages() {
        if (incomingMessages.size() > 0) {
            pingCount = 0;
            Iterator<Message> iter = incomingMessages.iterator();
            API sageApi = this.context == null ? API.apiLocalUI : new API(this.context);
            
            while (iter.hasNext()) {
                if (incomingMessages.size() < 2)
                    logger.Debug("(Processing messages) There is now " + incomingMessages.size() + " incoming message.");
                else logger.Debug("(Processing messages) There are now " + incomingMessages.size() + " incoming messages.");
                
                try {
                    Message msg = iter.next();
                    String dataStr;
                    Integer showId;
                    logger.Debug("(Processing messages) " + msg.toString());
                    switch (msg.getType()) {
                        case ANSWER:
                            this.isXML = msg.getData().equalsIgnoreCase("XML");
                            break;
                        case PLAY_MODE:
                            setPlayMode(msg.getData(), sageApi);
                            break;
                        case VOLUME:
                            setVolume(msg.getData(), sageApi.mediaPlayerAPI);
                            break;
                        case MUTE:
                            setMute(msg.getData(), sageApi.mediaPlayerAPI);
                            break;
                        case SET_CHANNEL:
                                ChannelAPI.Channel channel = sageApi.channelAPI
                                        .GetChannelForStationID(Integer.parseInt(msg.getData()));
                                if (sageApi.mediaPlayerAPI.IsPlaying())
                                    sageApi.mediaPlayerAPI.ChannelSet(channel.GetChannelNumber());
                                else {
                                    AiringAPI.List allAirings = sageApi.database.GetAiringsOnChannelAtTime(
                                            channel, (new Date()).getTime(), (new Date()).getTime() + 10, false);
                                    logger.Message("Watching: " + allAirings.get(0).GetAiringTitle());
                                    sageApi.mediaPlayerAPI.Watch(sageApi.airingAPI.Unwrap(allAirings.get(0)));
                                    sageApi.widgetAPI.FindWidget("Menu", "MediaPlayer OSD").LaunchMenuWidget();
                            }
                            break;
                        case CURRENT_CHANNEL:
                            sageApi.mediaPlayerAPI.ChannelSet(msg.getData());
                            break;
                        case WATCH_SHOW_PATH:
                            logger.Debug("Watching show with path [" + msg.getData() + "]");
                            //sageApiGlobal.SageCommand("Home");
                            //Thread.sleep(500);
                            //sageApiGlobal.SageCommand("TV");
                            //Thread.sleep(500);
                            try {
                                // Strip leading/trailing spaces and possible DVD extension...
                                String file = msg.getData().trim();
                                if (file.toUpperCase().endsWith("\\VIDEO_TS.IFO")) 
                                    file = file.substring(0, file.length() - 13);
                                MediaFile mf = sageApi.mediaFileAPI.GetMediaFileForFilePath(new File(file));
                                Object mediaFile = sageApi.mediaFileAPI.Unwrap(mf);
                                Object successful = sageApi.mediaPlayerAPI.Watch(mediaFile);
                                sageApi.widgetAPI.FindWidget("Menu", "MediaPlayer OSD").LaunchMenuWidget();
                                logger.Debug("Watch show path result: " + successful.toString());
                            }
                            catch (Exception e) {
                                logger.Error(e); 
                            }
                            break;
                        case WATCH_SELECTED_SHOW:
                            showId = null;
                            try {
                                showId = Integer.valueOf(msg.getData());
                                MediaFileAPI.MediaFile mf = sageApi.mediaFileAPI.GetMediaFileForID(showId.intValue());
                                if (mf == null) {
                                    logger.Debug("No show found with ID of " + showId.toString());
                                }
                                else {
                                    logger.Debug("Watching show: " + mf.GetMediaTitle());
                                    Object mediaFile = sageApi.mediaFileAPI.Unwrap(mf);
                                    //sageApiGlobal.SageCommand("Home");
                                    //Thread.sleep(500);
                                    //Object didItWork =
                                    sageApi.mediaPlayerAPI.Watch(mediaFile);
                                    //Thread.sleep(100);
                                    //sageApiGlobal.SageCommand("TV");
                                    sageApi.widgetAPI.FindWidget("Menu", "MediaPlayer OSD").LaunchMenuWidget();
                                }
                            }
                            catch (Throwable t) {
                                logger.Error(t);   
                                logger.Message("Show ID: " + showId.toString());
                            }                            
                            break;
                        case WATCH_SELECTED_IMAGE: // Non functionnal
                            showId = null;
                            try  {
                                showId = Integer.valueOf(msg.getData());
                                MediaFileAPI.MediaFile mf = sageApi.mediaFileAPI.GetMediaFileForID(showId.intValue());
                                if (mf == null) {
                                    logger.Debug("No image found with ID of " + showId.toString());
                                }
                                else {
                                    logger.Debug("Watching image: " + mf.GetMediaTitle());
                                    //Object mediaFile = sageApiMediaFile.Unwrap(mf);
//                                    sageApiGlobal.SageCommand("Home");
//                                    Thread.sleep(100);
//                                    sageApiGlobal.SageCommand("Picture Library");
//                                    Thread.sleep(5000);
                                    Object image = sageApi.utility.LoadImage(mf.GetFullImage());
                                    //WidgetAPI.
                                    //Object didItWork = sageApiMediaPlayer.Watch(mediaFile);
                                    WidgetAPI.Widget pictureWidget = sageApi.widgetAPI.FindWidget("Menu", "Picture Slideshow");
                                    //pictureWidget.AddWidgetChild((WidgetAPI.Widget) image);
                                    pictureWidget.LaunchMenuWidget();
                                    logger.Debug(image.toString());
                                    //Thread.sleep(5000);
                                    //Object didItWork2 = mf.Watch();
                                    //sageApiWidget.FindWidget("Menu", "Picture Slideshow").LaunchMenuWidget();
                                    //logger.Debug(didItWork2.toString());
                                }
                            }
                            catch (Throwable t) {
                                logger.Error(t);   
                                logger.Message("Show ID: " + showId.toString());
                            }                            
                            break;
                        case WINDOWING_MODE:
                            if (msg.getData().equals("0")) sageApi.global.SetFullScreen(true);
                            else sageApi.global.SetFullScreen(false);
                            break;
                        case COMMAND:
                            if (msg.getData().equalsIgnoreCase("QUT")) {
                                logger.Debug("Exit message received, closing interface");
                                sageApi.global.Exit();
                            }
                            else if (msg.getData().equalsIgnoreCase("CHU")) {
                                sageApi.mediaPlayerAPI.ChannelUp();
                                logger.Debug("ChannelUp.");
                            }
                            else if (msg.getData().equalsIgnoreCase("CHD")) {
                                logger.Debug("ChannelDown.");
                                sageApi.mediaPlayerAPI.ChannelDown();
                            }
                            else if (msg.getData().equalsIgnoreCase("VOLU")) {
                                sageApi.mediaPlayerAPI.VolumeUp();
                                logger.Debug("VolumeUp.");
                            }
                            else if (msg.getData().equalsIgnoreCase("VOLD")) {
                                logger.Debug("VolumeDown.");
                                sageApi.mediaPlayerAPI.VolumeDown();
                            }
                            else if (msg.getData().equalsIgnoreCase("Backspace")) {
                                logger.Debug("Backspace.");
                                keystroke("\b", sageApi);
                            }
                            else {
                                logger.Debug("Assumed Sage command: " + msg.getData());
                                sageApi.global.SageCommand(msg.getData());
                            }
                            break;
                        case EXECUTE:
                            Runtime.getRuntime().exec(msg.getData());
                            break;
                        case EXECUTE_ON_CLIENT:
                            String SEPARATOR_CHAR = "\\|";
                            String [] parts = msg.getData().split(SEPARATOR_CHAR);
                            ArrayList<String> parameters = new ArrayList<String>();
                            for (int i = 1; i < parts.length; i++) parameters.add(parts[i]);

                            String process = sageApi.utility.ExecuteProcessReturnOutput(parts[0], parameters, null, true, true);
                            logger.Debug("[Process] " + process);
                            break;
                        case EXTENDER_TELNET_COMMAND:
                            clientCommand(msg.getData(), sageApi);
                            //Object result = mvpCommand(context, msg.getData());
                            //logger.Debug(result.toString());
                            break;
                        case INITIALIZE:
                            if (StartServers.Password.isEmpty() || StartServers.Password.equals(msg.getData())){
                                isInitialized = true;
                                send(new Message(MessageType.INITIALIZE, "OK"));
                                send(new Message(MessageType.PLAY_MODE, (currentMediaFile == null ? "Stop" : "Play")));
                                if (currentMediaFile != null) initialState();
                            }
                            else shutdown();
                            break;
                        case KEY:
                            keystroke(msg.getData(), sageApi);
                            break;
                        case ADD_TO_PLAYLIST:
                            addToPlaylist(msg.getData(), false);
                            break;
                        case ADD_ALBUM_TO_PLAYLIST:
                            addToPlaylist(msg.getData(), true);
                            break;
                        case CHANGE_THE_PLAYLIST:
                            changePlaylist(msg.getData(), sageApi);
                            break;
                        case CLEAR_THE_PLAYLIST:
                            clearPlaylist(sageApi);
                            break;
                        case DELETE_THE_PLAYLIST:
                            DeletePlaylist(msg.getData(), sageApi);
                            break;
                        case DELETE_PLAYLIST_ITEM:
                            DeletePlaylistItem(msg.getData(), sageApi);
                            break;
                        case GET_THE_PLAYLIST:
                            dataStr = getThePlaylist(msg.getData(), sageApi);
                            send(new Message(MessageType.GET_THE_PLAYLIST, dataStr));
                            break;
                        case LIST_THE_PLAYLISTS:
                            dataStr = listThePlaylists(sageApi);
                            send(new Message(MessageType.LIST_THE_PLAYLISTS, dataStr));
                            break;
                        case START_PLAYLIST:
                            sageApi.global.SageCommand("Home");
                            Thread.sleep(500);
                            playlist.StartPlaylist();
                            //sageApi.mediaPlayerAPI.StartPlaylist(playlist);
                            Thread.sleep(100);
                            sageApi.global.SageCommand("TV");
                            break;

                        default:
                            logger.Debug("Default case reached.  Didn't know what to do with: " + msg.getData());
                            break;
                    }
                }
                catch (Throwable t) {
                    logger.Error(t);
                }
                
                iter.remove();
            }
        }
    }

    // Add another outgoing message if ready
    public void addOutgoingMessages(Message message){ if (isInitialized) send(message); }

    /** Send a messages. */
    public void send(Message msg) {
        pingCount = 0;
        logger.Debug("Sending data: " + msg.toString());

        try {
            outputBuffer.print(STX + msg.toString() + ETX);
            outputBuffer.flush();
        }
        catch (Throwable t) {
            logger.Error(t);          
        }
    }    
    
    /** Get and process the incoming messages. */
    public boolean getIncomingMessages() {
        boolean stillConnected = true;       
        String inputLine = null;
        boolean messageReceived = true;

        //while (messageReceived && stillConnected)        
        while (messageReceived && stillConnected && (incomingMessages.size() < 1)) {
            try {
                inputLine = inputBuffer.readLine();
                if (inputLine == null) {
                    stillConnected = false;
                    messageReceived = false;
                }
                else messageReceived = true;
            }
            catch (SocketTimeoutException e) {
                messageReceived = false;
            }
            catch (IOException e) {
                messageReceived = false;
                stillConnected = false;
            }
            
            if (messageReceived) {
                try {
                    Message msg = new Message();
                    msg.fromString(inputLine);
                    incomingMessages.add(msg);
                    logger.Debug("Message received: " + inputLine);
                    logger.Debug("Now " + incomingMessages.size() + " incoming message(s) in list.");
                }
                catch (Throwable t) {
                    logger.Error(t);
                }
            }
        }
        
        return stillConnected;
    }

    private void initialState() {
        logger.Debug("[Initial state] About to process");
        try {
            String episode = currentMediaFile.getEpisode();
            String title = currentMediaFile.getTitle();
            long start = currentMediaFile.getStartTime();
            long end = currentMediaFile.getEndTime();
            String duration = currentMediaFile.getDurationStr();
            String genre = currentMediaFile.getGenre();
            String year = currentMediaFile.getYear();

            if (start == end) {
                API sageApi = this.context == null ? API.apiLocalUI : new API(this.context);
                start = sageApi.mediaPlayerAPI.GetAvailableSeekingStart()/1000;
                end = sageApi.mediaPlayerAPI.GetAvailableSeekingEnd()/1000;
                duration = String.valueOf(end - start);
            }

            send(new Message(MessageType.CURRENT_START_TIME, String.valueOf(start)));
            send(new Message(MessageType.CURRENT_END_TIME, String.valueOf(end)));

            if (currentMediaFile.isMusicFile()) {
                AlbumAPI.Album album = currentMediaFile.getAlbum();
                String artist = currentMediaFile.getShow().GetPeopleInShowInRoles(new String[] {"Artist", "Artiste"});
                String albumName = album.GetAlbumName();
                String category = (genre.isEmpty() ? album.GetAlbumGenre() : genre);
                if (!albumName.isEmpty()) title = albumName + " - " + title;

                send(new Message(MessageType.CURRENT_ARTIST,
                        (artist.isEmpty() ? album.GetAlbumArtist() : artist)));
                send(new Message(MessageType.CURRENT_CATEGORY, category));
                send(new Message(MessageType.CURRENT_TYPE, "Audio"));
            }
            else {
                String actors = currentMediaFile.getShow().GetPeopleInShowInRoles(new String[]
                        {"Actor", "Acteur", "Guest", "Invité", "Special guest", "Invité spécial"});
                String channel = currentMediaFile.getChannel();
                if (!channel.isEmpty()){
                    send(new Message(MessageType.CURRENT_CHANNEL, channel));
                    send(new Message(MessageType.CURRENT_STATION_NAME,
                            channel + (currentMediaFile.getAiring().GetChannel() != null ? " " +
                            currentMediaFile.getAiring().GetChannel().GetChannelDescription() : "")));
                }
                send(new Message(MessageType.CURRENT_CATEGORY, genre));
                send(new Message(MessageType.CURRENT_ACTORS, actors));

                if (currentMediaFile.isDvd())
                    send(new Message(MessageType.CURRENT_TYPE, "DVD"));
                else if (currentMediaFile.getIsLive())
                    send(new Message(MessageType.CURRENT_TYPE, "Live TV"));
                else if (currentMediaFile.getIsTvFile())
                    send(new Message(MessageType.CURRENT_TYPE, "TV"));
                else send(new Message(MessageType.CURRENT_TYPE, "Video"));
            }

            send(new Message(MessageType.CURRENT_TITLE, title));
            send(new Message(MessageType.CURRENT_DURATION, duration));
            send(new Message(MessageType.CURRENT_DESC, currentMediaFile.getDescription()));
            send(new Message(MessageType.CURRENT_YEAR, (year.isEmpty() ? "0" : year)));
            if (!episode.equalsIgnoreCase(title) || currentMediaFile.isMusicFile())
                send(new Message(MessageType.CURRENT_EPISODE, episode));
            send(new Message(MessageType.CURRENT_ID, String.valueOf(currentMediaFile.getMediaFileId())));
        }
        catch (Exception ex) {
            logger.Debug(ex.getMessage());
        }
    }

    /** Set the appropriate play mode for the current media. */
    public void setPlayMode(String playModeStr, API sageApi) throws Throwable {
        //if (playModeStr.equals ("Play"))            sageApiMediaPlayer.Play ();
        //else if (playModeStr.equals("Pause"))       sageApiMediaPlayer.Pause ();
        //else if (playModeStr.equals("Stop"))        sageApi.global.SageCommand("Stop");
        //else
        if (playModeStr.equals("FW1"))      sageApi.mediaPlayerAPI.SkipForward();
        else if (playModeStr.equals("FW2")) sageApi.mediaPlayerAPI.SkipForward2();
        else if (playModeStr.equals("BK1")) sageApi.mediaPlayerAPI.SkipBackwards();
        else if (playModeStr.equals("BK2")) sageApi.mediaPlayerAPI.SkipBackwards2();
        else if (playModeStr.equals("PL1")) sageApi.mediaPlayerAPI.PlayFaster();
        else if (playModeStr.equals("PL2") || playModeStr.equals("Smooth RW"))
            sageApi.mediaPlayerAPI.PlaySlower();
        else if (playModeStr.startsWith("Seek")) {
            try {
                long seekPercent = Long.valueOf(playModeStr.substring(5));
                long seekTime = (currentMediaFile.getEndTime() - currentMediaFile.getStartTime()) * seekPercent * 10
                        + currentMediaFile.getStartTime() * 1000;
                sageApi.mediaPlayerAPI.Seek(seekTime);
            }
            catch (Throwable t) {
                logger.Error(t);
            }
        }
        else sageApi.global.SageCommand(playModeStr);
    }

    private void keystroke(String key, API sageApi){
        if (!key.isEmpty()){
            for (int i = 0; i < key.length(); i++)
                sageApi.utility.Keystroke(String.valueOf(key.charAt(i)), false);
        }
    }

    /** Set the volume. */
    public void setVolume(String volumeStr, MediaPlayerAPI sageApiMediaPlayer) {
        try {
            float vol = Float.valueOf(volumeStr) / 100f;
            sageApiMediaPlayer.SetVolume(vol);
        }
        catch (Exception ex) {
            logger.Debug(ex.getMessage());
        }
    }
    
    /** Set the volume. */
    public void setMute(String muteOn, MediaPlayerAPI sageApiMediaPlayer) {
        try {
            boolean mute = Boolean.getBoolean(muteOn);
            sageApiMediaPlayer.SetMute(mute);
        }
        catch (Exception ex) {
            logger.Debug(ex.getMessage());
        }
    }

    private void clientCommand(String command, API sageApi){
        if (command.equalsIgnoreCase("Power")) power(sageApi);
        else if (command.equalsIgnoreCase("PowerOn")) powerOn(sageApi);
        else if (command.equalsIgnoreCase("PowerOff")) powerOff(sageApi);
        else if (command.equalsIgnoreCase("Reboot")) reboot(this.context, this.clientType, this.IP);
        else mvpCommand(context, command, this.IP);
    }

    private void power(API sageApi){
        boolean isFullScreen = sageApi.global.IsFullScreen();

        switch (this.clientType){
            case None:
            case Software:
                logger.Debug("[Power] Software client, so toggling from: " + isFullScreen);
                if (isFullScreen) sageApi.global.SetFullScreen(false);
                else sageApi.global.SetFullScreen(true);
                break;
            case MVP:
                logger.Debug("[Power] Running on an Hauppauge MVP");
                if (isFullScreen) mvpCommand(context, "killall miniclient", this.IP);
                else mvpCommand(context, "killall sagewait", this.IP);
                break;

            default:
                logger.Debug("[Power] Running on a HD extender");
                if (isFullScreen) mvpCommand(context, "killall miniclient", this.IP);
                else mvpCommand(context, "killall waitpower", this.IP);
        }
    }

    public void powerOn(API sageApi){
        switch (this.clientType){
            case None:
            case Software:
                logger.Debug("[PowerOn] Software client, going full screen");
                sageApi.global.SetFullScreen(true);
                break;

            default:
                logger.Debug("[PowerOn] Running on an extender");
                powerOn(context, this.clientType, this.IP);
        }
    }

    public static void powerOn(String context, ClientType extenderType, String ip){
        switch (extenderType){
            case MVP:
                mvpCommand(context, "killall sagewait", ip);
                break;

            default:
                mvpCommand(context, "killall waitpower", ip);
        }
    }

    public void powerOff(API sageApi){
        switch (this.clientType){
            case None:
            case Software:
                logger.Debug("[PowerOff] Software client, so leaving fullscreen");
                sageApi.global.SetFullScreen(false);
                break;

            default:
                logger.Debug("[PowerOff] Running on an extender");
                mvpCommand(context, "killall miniclient", this.IP);
        }
    }

    public static void reboot(String uiContext, ClientType clientType, String ip){
        switch (clientType){
            case None:
            case Software:
                break;
            case MVP:
                 mvpCommand(uiContext, "killall miniclient;sleep 1;reboot", ip);
                break;

            default:
                System.out.println("Reboot: " + mvpCommand(uiContext, "reboot", ip));
        }
    }

    // From Nielm's web server code
    
    private static String mvpCommand(String uiContext, String command, String ip) {
        // check Mac or IP
        if ( uiContext.matches("(\\d{1,3}\\.){3}\\d{1,3}")) {
            try {
                InetAddress ipaddr = InetAddress.getByName(uiContext);
                return mvpCommand(ipaddr,command);
            } 
            catch (java.net.UnknownHostException e){}
        }
        // assume Mac
        uiContext=uiContext.toLowerCase().replaceAll("[^a-f0-9]", "");
        if ( uiContext.length()!=12) {
            return "Invalid Mac address: cleaned should be 12 chars: "+uiContext;
        }
        final Process p;
        
        try {
            // Windows arp output
            // c:\ arp -a
            //
            // Interface: 192.168.0.3 --- 0x9
            //  Internet Address      Physical Address      Type
            //  192.168.0.1           00-0f-b5-af-0d-48     dynamic
            //  192.168.0.50          00-11-2f-ea-21-02     dynamic

            // Linux arp output:
            // $arp -an
            // ? (192.168.0.1)           00:0f:b5:af:0d:48
            // ? (192.168.0.50)          00:11:2f:ea:21:02

            if ( true) { // || SageApi.booleanApi("IsWindowsOs", null)) {
                p=Runtime.getRuntime().exec("arp -a");
            } 
            else p=Runtime.getRuntime().exec("arp -an");

            try {
                Thread errGobbler = new Thread() {
                    @Override
                    public void run() {
                        BufferedReader in = new BufferedReader(new InputStreamReader(p.getErrorStream()));
                        String line;
                        try {
                            while((line=in.readLine())!=null) {
                                    System.out.println("Arp: stderr: " + line);
                            }
                            in.close();
                        } 
                        catch (IOException e){}
                    }
                };
                errGobbler.start();
                String line;
                InetAddress addr = null;
                BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
                try {
                    Pattern pat=Pattern.compile("^.*?(([0-9]{1,3}.){3}[0-9]{1,3}).+?(([0-9a-f]{2}[:\\-.]){5}[0-9a-f]{2}).*$",
                        Pattern.CASE_INSENSITIVE);
                    while((line=in.readLine())!=null) {
                        System.out.println("Arp: stdout: "+line);
                        java.util.regex.Matcher m=pat.matcher(line);
                        if ( m.matches()) {
                            if ( m.group(3).toLowerCase().replaceAll("[^a-f0-9]", "").equals(uiContext)) {
                                    try {
                                        addr = InetAddress.getByName(m.group(1));
                                    }
                                    catch (java.net.UnknownHostException e) {
                                        System.out.println("arp " + e);
                                    }
                                }
                            }
                        }
                        in.close();
                        if ( addr != null){
                            API.apiNullUI.configuration.SetProperty(StartServers.OPT_PREFIX_NAME + uiContext + "/ip", addr.getHostAddress());
                            return mvpCommand(addr,command);
                        }
                        else if (ip != null) return mvpCommand(InetAddress.getByName(ip), command);
                    } 
                catch (IOException e){}
                }
                finally {
                    try {
                        System.out.println("arp exited with " + p.exitValue());
                    }
                    catch (IllegalThreadStateException e) {
                        System.out.println("Terminating ARP");
                        p.destroy();
                        p.getOutputStream().close(); // close stdin
                    }
            }
            return "Could not determine IP address for UI Context";

        }
        catch (Exception e) {
            System.out.println("Getting IP for MAC " + e);
        }
        return "Could not determine IP address for UI Context - Arp failed";

    }
    
    static String mvpCommand(InetAddress ipaddr, String command) {
        System.out.println("Issuing the command: " + command + " on the extender at " + ipaddr);

        PrintWriter out = null;
        //BufferedReader in = null;
        java.net.Socket sock = null;
        
        try {
            sock = new java.net.Socket(ipaddr,23);
            //out = new PrintWriter(new OutputStreamWriter(sock.getOutputStream(), "UTF-8"), true);
            //in = new BufferedReader(new InputStreamReader(sock.getInputStream(), "UTF-8"));
            out = new PrintWriter(sock.getOutputStream(), true);
            //in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
            Thread.sleep(100);
            out.println("root");
            //out.print("root");
            //out.flush();
            Thread.sleep(100);
            out.println(command);
            //out.print(command);
            //out.flush();
            Thread.sleep(150); // Give it a sec to do its thing
            out.println("exit");
            //out.print("exit");
            //out.flush();
        } 
        catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            return "Failed to connect to extender at " + ipaddr.getHostAddress() + " -- " + e;
        } 
        finally {
            try {
                if ( out!= null) out.close();
                //if ( in != null) in.close();
                if ( sock != null) sock.close();
            } 
            catch (Exception e){}
        }
        System.out.println("Returning...");
        return "OK";
    }

            /** Add an item to the playlist.
     *
     * @param ID The MediaFileID of the media.
     * @param  isAlbum True if this represents an album.
     */
    public void addToPlaylist(String msg, boolean isAlbum) {
        Integer showId = -1;
        String SEPARATOR_CHAR = "\\|";
        String [] parameters = msg.split(SEPARATOR_CHAR);
        String plName = (parameters.length > 1 ? parameters[0] : "");
        String ID = parameters[parameters.length - 1];
        API sageApi = this.context == null ? API.apiLocalUI : new API(this.context);

        PlaylistAPI.Playlist thisPlaylist = (plName.isEmpty() ?
            playlist : sageApi.playlistAPI.FindPlaylist(plName));

        try {
            showId = Integer.valueOf(ID);
            MediaFileAPI.MediaFile mf = sageApi.mediaFileAPI.GetMediaFileForID(showId.intValue());
            if (mf == null) logger.Debug("No show found with ID of " + showId.toString());
            else {
                Object mediaFile = null;

                if (isAlbum) {
                    AlbumAPI.Album album = mf.GetAlbumForFile();
                    logger.Debug("Adding to the playlist the songs from: " + album.GetAlbumName());
                    Object albumUW = sageApi.albumAPI.Unwrap(album);
                    thisPlaylist.AddToPlaylist(albumUW);
                }
                else {
                    logger.Debug("Adding to the playlist: " + mf.GetMediaTitle());
                    mediaFile = sageApi.mediaFileAPI.Unwrap(mf);
                    thisPlaylist.AddToPlaylist(mediaFile);
                }
            }
        }
        catch (Throwable t) {
            logger.Error(t);
            logger.Message("Show ID: " + showId.toString());
        }
    }

    /** Change the playlist */
    public void changePlaylist(String newPlaylist, API sageApi) {
        for (PlaylistAPI.Playlist pl : sageApi.playlistAPI.GetPlaylists()) {
            if (pl.GetName().equalsIgnoreCase(newPlaylist)) {
                playlist = pl;
                return;
            }
        }

        if (newPlaylist.equalsIgnoreCase("Now Playing")) playlist = sageApi.playlistAPI.GetNowPlayingList();
        else playlist = sageApi.playlistAPI.AddPlaylist(newPlaylist);
    }

        /** Delete a playlist */
    public void clearPlaylist(API sageApi) {
        Integer numberOfItems = playlist.GetNumberOfPlaylistItems();

        try {
            for (int i = 1; i <= numberOfItems; i++)
                playlist.RemovePlaylistItemAt(numberOfItems - i);
        }
        catch (Exception e) {
            logger.Debug(e.getMessage());
        }

        String dataStr = getThePlaylist(playlist.GetName(), sageApi);
        send(new Message(MessageType.GET_THE_PLAYLIST, dataStr));
    }

    /** Delete a playlist */
    public void DeletePlaylist(String plName, API sageApi) {
        PlaylistAPI.Playlist thisPlaylist = (plName.isEmpty() ? playlist : sageApi.playlistAPI.FindPlaylist(plName));

        Integer numberOfItems = thisPlaylist.GetNumberOfPlaylistItems();

        try {
            if (thisPlaylist.GetName().equalsIgnoreCase(sageApi.playlistAPI.GetNowPlayingList().GetName()))
                for (int i = 1; i <= numberOfItems; i++)
                    thisPlaylist.RemovePlaylistItemAt(numberOfItems - i);
            else {
                thisPlaylist.RemovePlaylist();
                thisPlaylist = sageApi.playlistAPI.GetNowPlayingList();
            }
        }
        catch (Exception e) {
            logger.Debug(e.getMessage());
        }

        String dataStr = listThePlaylists(sageApi);
        send(new Message(MessageType.LIST_THE_PLAYLISTS, dataStr));
        dataStr = getThePlaylist(thisPlaylist.GetName(), sageApi);
        send(new Message(MessageType.GET_THE_PLAYLIST, dataStr));
    }

    /** Delete an item from a playlist */
    public void DeletePlaylistItem(String msg, API sageApi) {
        String SEPARATOR_CHAR = "\\|";
        String [] parameters = msg.split(SEPARATOR_CHAR);
        String plName = (parameters.length > 1 ? parameters[0] : "");
        String ID = parameters[parameters.length - 1];

        PlaylistAPI.Playlist thisPlaylist = (plName.isEmpty() ?
            playlist : sageApi.playlistAPI.FindPlaylist(plName));

        try {
            Integer item = Integer.valueOf(ID);

            if (item < thisPlaylist.GetNumberOfPlaylistItems())
                thisPlaylist.RemovePlaylistItemAt(item);
        }
        catch (Exception e) {
            logger.Debug(e.getMessage());
        }
        String dataStr = getThePlaylist(thisPlaylist.GetName(), sageApi);
        send(new Message(MessageType.GET_THE_PLAYLIST, dataStr));
    }

    /** Get the list of items in the playlist. */
    public String getThePlaylist(String plName, API sageApi) {
        logger.Debug("Finding all the items in the playlist...");
        AiringAPI.Airing airing = null;
        AlbumAPI.Album album = null;
        Integer id = -1;
        MediaFileAPI.MediaFile mf = null;
        Object obj = null;

        PlaylistAPI.Playlist pl = null;
        PlaylistAPI.Playlist thisPlaylist = (plName.isEmpty() ?
            playlist : sageApi.playlistAPI.FindPlaylist(plName));
        String artist = "", title = "";
        logger.Debug("Using the playlist: " + playlist.GetName());

        if (thisPlaylist == null && sageApi.playlistAPI.GetNowPlayingList() != null
                && plName.equalsIgnoreCase(sageApi.playlistAPI.GetNowPlayingList().GetName()))
            thisPlaylist = sageApi.playlistAPI.GetNowPlayingList();
        Integer numberOfItems = thisPlaylist.GetNumberOfPlaylistItems();
        logger.Debug(numberOfItems.toString() + " item(s) found.");
        StringBuilder msgToSend = new StringBuilder(!isXML ? "{\"PlayList\": [" :
            (numberOfItems + DATA_SEPARATOR + thisPlaylist.GetName() + DATA_SEPARATOR));
        int max = (maxItems != -1 ? maxItems : DEFAULT_MAXITEMS);

        for (int i = 0; i < numberOfItems && i < max; i++) {
            obj = thisPlaylist.GetPlaylistItemAt(i);
            if (!isXML && i > 0) msgToSend.append(", ");

            if (thisPlaylist.GetPlaylistItemTypeAt(i).equalsIgnoreCase("Airing")) {
                airing = sageApi.airingAPI.Wrap(obj);
                mf = airing.GetMediaFileForAiring();
                id = mf.GetMediaFileID();
                title = mf.GetMediaTitle();

                if (!isXML) msgToSend.append("{").append(JSONUtil.addJSONElement("ID", id))
                        .append(JSONUtil.addJSONElement("Title", title));
                else msgToSend.append(id).append(DATA_SEPARATOR).append(title);

                if (mf.IsMusicFile()) {
                    artist = airing.GetShow().GetPeopleInShowInRoles(new String[]{"Artist", "Artiste"});

                    if (!isXML) msgToSend.append(JSONUtil.addJSONElement("Artist", artist))
                            .append("\"Type\": \"Song\"");
                    else msgToSend.append(" by ").append(artist).append(DATA_SEPARATOR)
                            .append("Song").append(DATA_SEPARATOR);
                }
                else if (mf.IsPictureFile()){
                    if (!isXML) msgToSend.append("\"Type\": \"Photo\"");
                    else msgToSend.append(DATA_SEPARATOR).append("Photo").append(DATA_SEPARATOR);
                }
                else if (mf.IsTVFile()) {
                    String episode = airing.GetShow().GetShowEpisode();

                    if (!isXML) msgToSend.append(JSONUtil.addJSONElement("Episode", episode))
                            .append("\"Type\": \"Recording\"");
                    else{
                        msgToSend.append((episode.isEmpty() ? "" : ": " + episode)).append(DATA_SEPARATOR);
                        msgToSend.append("TV").append(DATA_SEPARATOR);
                    }
                }
                else if (mf.IsVideoFile()){
                    if (!isXML) msgToSend.append("\"Type\": \"Video\"");
                    else msgToSend.append(DATA_SEPARATOR).append("Video").append(DATA_SEPARATOR);
                }
                else if (mf.IsDVD()){
                    if (!isXML) msgToSend.append("\"Type\": \"DVD\"");
                    else msgToSend.append(DATA_SEPARATOR).append("DVD").append(DATA_SEPARATOR);
                }
                else{
                    if (!isXML) msgToSend.append("\"Type\": \"Other\"");
                    else {
                        msgToSend.append(DATA_SEPARATOR).append("Other").append(DATA_SEPARATOR);
                    msgToSend.append(mf.GetSegmentFiles()[0].getPath()).append(DATA_SEPARATOR);
                    }
                }

                if (!isXML) msgToSend.append("}");
            }
            else if (thisPlaylist.GetPlaylistItemTypeAt(i).equalsIgnoreCase("Album"))
            {
                try {
                    album = sageApi.albumAPI.Wrap(obj);
                    mf = album.GetAlbumTracks().get(0).GetMediaFileForAiring();
                    artist = album.GetAlbumArtist();
                    id = mf.GetMediaFileID();
                    title = album.GetAlbumName();

                    if (!isXML) msgToSend.append("{").append(JSONUtil.addJSONElement("ID", id))
                            .append(JSONUtil.addJSONElement("Type", "Album"))
                            .append(JSONUtil.addJSONElement("Title", title))
                            .append(JSONUtil.addJSONElement("Artist", artist, true));
                            //.append("\"Songs\": [");

                    else{
                        msgToSend.append(id).append(DATA_SEPARATOR);
                        msgToSend.append(title).append(" {")
                                .append(String.valueOf(album.GetNumberOfTracks()));
                        msgToSend.append(" song(s)} by ").append(artist).append(DATA_SEPARATOR);
                        msgToSend.append("Album").append(DATA_SEPARATOR);
                    }

                    for (int j = 0; j < album.GetNumberOfTracks(); j++) {
                        mf = album.GetAlbumTracks().get(j).GetMediaFileForAiring();
                        if (!isXML){
//                            if (j > 0) msgToSend.append(", ");
//                            msgToSend.append("{")
//                                    .append(JSONUtil.addJSONElement("ID", mf.GetMediaFileID()))
//                                    .append(JSONUtil.addJSONElement("Title", mf.GetMediaTitle(), true)).append("}");
                        }
                        else msgToSend.append((j == 0 ? " " : ", ")).append(mf.GetSegmentFiles()[0].getPath());
                    }

                    if (!isXML) msgToSend.append("}");//("]}");
                    else msgToSend.append(DATA_SEPARATOR);
                }
                catch (Exception e) {
                    logger.Debug(e.getMessage());
                }
            }
            else if (thisPlaylist.GetPlaylistItemTypeAt(i).equalsIgnoreCase("Playlist")) {
                try {
                    pl = sageApi.playlistAPI.Wrap(obj);
                    mf = album.GetAlbumTracks().get(0).GetMediaFileForAiring();
                    msgToSend.append(String.valueOf(pl.GetNumberOfPlaylistItems())).append(DATA_SEPARATOR);
                    msgToSend.append(pl.GetName()).append(DATA_SEPARATOR);
                    msgToSend.append("Playlist").append(DATA_SEPARATOR);
                    msgToSend.append("").append(DATA_SEPARATOR);
                }
                catch (Exception e) {
                    logger.Debug(e.getMessage());
                }
            }
            else {
                // What else can it be?
                msgToSend.append("999").append(DATA_SEPARATOR);
                msgToSend.append(obj.toString()).append(DATA_SEPARATOR);
                msgToSend.append("Unknown").append(DATA_SEPARATOR);
                msgToSend.append("").append(DATA_SEPARATOR);
            }
        }

        if (!isXML) msgToSend.append("]}");

        return msgToSend.toString();
    }

    /** Get the list of playlists. */
    public String listThePlaylists(API sageApi) {
        logger.Debug("Finding all the playlists...");
        ArrayList<String> playlistNames = new ArrayList<String>();

        // Can crash if extender is off line...
        try{
            PlaylistAPI.List allThePlaylists = sageApi.playlistAPI.GetPlaylists();
            logger.Debug("Total playlist(s): " + allThePlaylists.size());

            for (PlaylistAPI.Playlist pl : allThePlaylists){
                logger.Debug("Adding the playlist: " + pl.GetName());
                playlistNames.add(pl.GetName());
            }

            Playlist nowPlaying = sageApi.playlistAPI.GetNowPlayingList();
            logger.Debug("Now playing: " + nowPlaying);

            if (nowPlaying != null && !playlistNames.contains(nowPlaying.GetName()))
                playlistNames.add(0, nowPlaying.GetName());
        }
        catch (Exception ex) {logger.Debug(ex.getMessage());}

        Integer numberOfItems = playlistNames.size();
        logger.Debug(numberOfItems + " playlist(s) found.");
        StringBuilder msgToSend = new StringBuilder(String.valueOf(numberOfItems));

        for (String name : playlistNames)
            msgToSend.append(DATA_SEPARATOR).append(name);

        return msgToSend.toString();
    }
 
    public boolean isExtender(API sageApi) {
        try {
            return (sageApi.global.IsRemoteUI() && !sageApi.global.IsDesktopUI());
        } catch (Exception e){
            System.out.println(e);
        }
        return false;
    }

    public boolean isPlaceshifter(API sageApi) {
        try {
            return (sageApi.global.IsRemoteUI() && sageApi.global.IsDesktopUI());
        } catch (Exception e){
            System.out.println(e);
        }
        return false;
    }

    public boolean isMvp(API sageApi){
	    try {
	        return isExtender(sageApi) && sageApi.configuration.GetAudioOutputOptions() == null;
        } catch (Exception e){
            System.out.println(e);
        }
        return false;
    }

    public boolean isHDExtender(API sageApi){
        try {
            return isExtender(sageApi) && sageApi.configuration.GetAudioOutputOptions() != null;
        } catch (Exception e){
            System.out.println(e);
        }
        return false;
    }
}
