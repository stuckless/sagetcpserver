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
import gkusnick.sagetv.api.AiringAPI.Airing;
import gkusnick.sagetv.api.FavoriteAPI.Favorite;
import gkusnick.sagetv.api.MediaFileAPI.MediaFile;
import gkusnick.sagetv.api.SystemMessageAPI.SystemMessage;
import java.io.*;
import java.net.*;
import java.util.Date;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import sagetcpserver.messages.MessageType;
import sagetcpserver.messages.Message;
import sagetcpserver.utils.JSONUtil;
import sagetcpserver.utils.SageLogger;
import sagetcpserver.utils.XMLUtil;

/**
 * This is where the SageTCTPServer is created and all the procesing is handled.
 * 
 * @author Rob + Fonceur
 */
public class SageMedia implements Runnable {
    public static int StreamingPort = 554;
    private static long rescale = 100000l;
    public int StreamingProfile = 0;
    public static String LocalIP = "", Password = "";
    //public static String StreamingRTSPType = "mp4a-latm";
    public static String Button1 = "Reboot,XTC:Reboot";
    public static String Button2 = "4x3,CMD:Aspect Ratio 4x3";
    public static String Button3 = "16x9,CMD:Aspect Ratio 16x9";
    public static String Button4 = "AR source,CMD:Aspect Ratio Source";
    public static String Button5 = "AR toggle,CMD:Aspect Ratio Toggle";
    public static String Button6 = "DVD menu,CMD:DVD Menu";
    public static String Button7 = "Time,CMD:Time Scroll";
    public static String StreamingTranscodeOptions = "soverlay,ab=100,samplerate=44100,channels=2," +
            "acodec=mp4a,vcodec=h264,width=352,height=240,vfilter=\"canvas{width=352,height=240," +
            "aspect=16:9}\",fps=29,vb=200,venc=x264{vbv-bufsize=500,partitions=all,level=12,no-cabac," +
            "subme=7,threads=4,ref=2,mixed-refs=1,bframes=0,min-keyint=1,keyint=50,trellis=2," +
            "direct=auto,qcomp=0.0,qpmax=51,deinterlace}";
    public static String StreamingTranscodeOptions1 = "fps=14.98,vcodec=mp4v,vb=512,scale=1,width=352,"
            + "height=240,acodec=mp4a,ab=192,channels=2,samplerate=44100,deinterlace,audio-sync";
    public static String StreamingTranscodeOptions2 = "fps=14.98,vcodec=h264,venc=x264{no-cabac,level=12,vbv-maxrate=300," +
            "vbv-bufsize=1000,keyint=75,ref=3,bframes=0},width=352,height=240,acodec=mp4a,ab=64,vb=300," +
            "samplerate=44100,audio-sync";
    public static String StreamingTranscodeOptions3 = "fps=29.97,vcodec=mp4v,vb=512,scale=1,width=352,"
            + "height=240,acodec=mp4a,ab=192,channels=2,samplerate=44100,deinterlace,audio-sync";
    public static String StreamingTranscodeOptions4 = "fps=29.97,vcodec=h264,venc=x264{no-cabac,level=12,vbv-maxrate=300," +
            "vbv-bufsize=1000,keyint=75,ref=3,bframes=0},width=352,height=240,acodec=mp4a,ab=64,vb=300," +
            "samplerate=44100,audio-sync";
    public static String StreamingTranscodeOptions5 = "fps=29.97,vcodec=mp4v,vb=512,scale=1,width=576,"
            + "height=384,acodec=mp4a,ab=192,channels=2,samplerate=44100,deinterlace,audio-sync";
    public static String StreamingTranscodeOptions6 = "fps=29.97, vcodec=h264,venc=x264{no-cabac,level=12,vbv-maxrate=300," +
            "vbv-bufsize=1000,keyint=75,ref=3,bframes=0},width=576,height=384,acodec=mp4a,ab=64,vb=300," +
            "samplerate=44100,audio-sync";
    public static String StreamingType = "rtsp";
    public static String StreamingVLCOptions = "-I dummy --one-instance --extraintf oldhttp --sout-keep"
            + " --no-sout-rtp-sap --no-sout-standard-sap --rtsp-caching=5000 -f";
    //"-I dummy --one-instance --no-sub-autodetect-file";  // qt
    public static String StreamingVLCPath = "C:/Program Files/VideoLAN/VLC/vlc.exe";

    private static final char DATA_SEPARATOR = (char)0x7c;
    private static final char STX = (char)0x02;
    private static final char ETX = (char)0x03;
    private static final int DEFAULT_MAXITEMS = 25;
    private static Integer offsetAlbum = 0, offsetOther = 0, offsetPicture = 0;
    /** Mode of this TCP server {Media|Player}. */
    /** Type of answer expected by the TCP client {TXT|XML}. */
    public String extendedMessageFormat = "TXT";
    private boolean isXML = true;
    private boolean isJSON2 = true;
    /** Maximum number of items to send per list {-1 = no limit}. */
    public Integer maxItems = 1000;
    private static String videoDiskSpaceString = "";

    private API sageApi = API.apiNullUI;
    public int serverPort;
    public Socket client = null;
    public int pingCount = 0;
    private PrintWriter outputBuffer = null;
    private BufferedReader inputBuffer = null;
    private SageLogger logger = null;
    private ArrayList<Message> incomingMessages = null;

    private boolean isInitialized = false;
    public Long lastPhoto = 0l;
    public Long lastSM = 0l;
    public Long lastVideo = 0l;

    /** Creates a new instance of SageServer */
    public SageMedia(Socket newClient) {
        logger = new SageLogger("(" + newClient.getPort() + ")");
        logger.Message("Adding a server: " + newClient.getLocalPort());
        client = newClient;
        try{
            outputBuffer = new PrintWriter(new OutputStreamWriter(client.getOutputStream(), "UTF-8"), true);
            inputBuffer = new BufferedReader(new InputStreamReader(client.getInputStream(), "UTF-8"));
        }
        catch (IOException e) {
           logger.Error(e);
        }

        serverPort = newClient.getLocalPort();
        incomingMessages = new ArrayList<Message>();
        logger.Message("Done adding the server");
    }

    /** Start listening/transmitting on the new Sage TCP Server. */
    public void run() {
        boolean clientConnected = true;
        send(new Message(MessageType.VERSION, StartServers.VERSION));

        // Restart the interval timers, so that some messages will be sent out
        // immediately upon connect.
        while (clientConnected) {
            clientConnected = getIncomingMessages();

            if (clientConnected) processIncomingMessages();
        }

        shutdown();
    }

    public void shutdown(){
        // close the socket, kill the listning thread, etc...
        try {
            if (client != null){
                client.close();
                client = null;
            }
            this.finalize(); // Probably not what we really want here...
        } catch (Throwable ex) {
            Logger.getLogger(SageMedia.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /** Reinitialize some members */
    public void resetLists() {            
        lastPhoto = 0l;
        lastVideo = 0l;
        offsetAlbum = 0;
        offsetOther = 0;
        offsetPicture = 0;
    }

    /** Procees the incoming messages from the queue. */
    public void processIncomingMessages() {
        if (incomingMessages.size() > 0) {
            pingCount = 0;
            Iterator<Message> iter = incomingMessages.iterator();
            
            while (iter.hasNext()) {
                if (incomingMessages.size() < 2)
                    logger.Debug("(Processing messages) There is now " + incomingMessages.size() + " incoming message.");
                else logger.Debug("(Processing messages) There are now " + incomingMessages.size() + " incoming messages.");
                
                /* TEMPORARY CODE
                String names[] = sageApi.global.GetUIContextNames();
                StringBuilder contextNameStr = new StringBuilder("The following UI context are available: ");
                for (int index = 0; index < names.length; index ++) 
                    contextNameStr.append(names[index]).append(", ");
                logger.Debug(contextNameStr.toString());
                *////////////////////
                
                try {
                    Message msg = iter.next();
                    Integer showId;
                    String dataStr;
                    logger.Debug("(Processing messages) " + msg.toString());
                    switch (msg.getType()) {
                        case DELETE_SHOW:
                            showId = Integer.valueOf(msg.getData());
                            boolean successful = deleteShow(showId);
                            if (successful) {
                                send(new Message(MessageType.DELETE_SHOW, "OK"));
                                
                                // A show was deleted, let's force another sending of the recorded
                                // shows list since it has now clearly changed.
                            
                            }
                            else send(new Message(MessageType.DELETE_SHOW, "BAD"));
                            break;
                        case ALL_CHANNELS:
                            dataStr = msg.getData();
                            if (dataStr.isEmpty() || dataStr.equalsIgnoreCase("All")) getAllChannels();
                            else getChannelsOnLineup(dataStr);
                            break;
                        case AIRINGS_ON_CHANNEL_AT_TIME:
                            getAiringsOnChannelAtTime(msg.getData());
                            break;
                        case ANSWER:
                            this.isXML = msg.getData().equalsIgnoreCase("XML");
                            this.isJSON2 = msg.getData().equalsIgnoreCase("JSON2");
                            break;
                        case BUTTON:
                            getButtons();
                            break;
                        case CHANGE_FAVORITE_FREQUENCY:
                            changeFavoriteFrequency(msg.getData());
                            break;
                        case DELETE_FAVORITE_SHOW:
                            deleteFavoriteShow(msg.getData());
                            break;
                        case EXECUTE:
                            Runtime.getRuntime().exec(msg.getData());
                            break;
                        case MAXIMUM_ITEMS:
                            try {
                                maxItems = Integer.valueOf(msg.getData());
                                send(new Message(MessageType.MAXIMUM_ITEMS, "OK"));
                            }
                            catch (Exception e) {
                                logger.Debug(e.getMessage());
                                send(new Message(MessageType.MAXIMUM_ITEMS, "BAD"));
                            }
                            break;
                        case ALL_THE_LINEUPS:
                            dataStr = getLineups();
                            send(new Message(MessageType.ALL_THE_LINEUPS, dataStr));
                            break;
                        case RESET_SYSTEM_MESSAGE:
                            sageApi.systemMessageAPI.DeleteAllSystemMessages();
                            this.lastSM = 0l;
                            break;
                        case RESET:
                            resetLists();
                            this.lastSM = 0l;
                        case INITIALIZE:
                            if (StartServers.Password.isEmpty() || StartServers.Password.equals(msg.getData())){
                                isInitialized = true;
                                sendAvailableData();
                                send(new Message(MessageType.PROTOCOL_STREAMING_TYPE, StreamingType));
                                send(new Message(MessageType.STREAMING_PORT, String.valueOf(StreamingPort)));
                                send(new Message(MessageType.INITIALIZE, "OK"));
                                dataStr = sageApi.configuration.GetProperty("jetty/jetty.port", null);
                                if (dataStr != null && !dataStr.isEmpty())
                                    send (new Message(MessageType.JETTY_HTTP_PORT, dataStr));
                                initialFavoriteList();
                                initialManualRecordingsList();
                                initialSystemMessages();
                                initialTVFileList();
                                initialUpcomingRecordingsList();
                            }
                            else shutdown();
                            break;
                        case UPCOMING_EPISODES_LIST:
                            dataStr = getUpcomingEpisodesList(msg.getData());
                            if (!dataStr.isEmpty()) 
                                send(new Message(MessageType.UPCOMING_EPISODES_LIST, dataStr));
                            break;
                        case LIST_OF_CLIENTS:
                            getClients();
                            break;
                        case MUSIC_FILES_LIST:
                            dataStr = msg.getData();
                            if (!dataStr.isEmpty()){
                               if (dataStr.equalsIgnoreCase("Reset"))
                                    offsetAlbum = 0;
                                else offsetAlbum = Integer.valueOf(dataStr);
                            }
                            getAlbumList();
                            break;
                        case OTHER_FILES_LIST:
                            dataStr = msg.getData();
                            if (!dataStr.isEmpty()){
                                if (dataStr.equalsIgnoreCase("Reset")){
                                    offsetOther = 0;
                                    this.lastVideo = 0l;
                                }
                                else{
                                    String SEPARATOR_CHAR = "\\|";
                                    String [] parameters = dataStr.split(SEPARATOR_CHAR);
                                    this.lastVideo = Long.valueOf(parameters[0]);
                                    offsetOther = Integer.valueOf(parameters[1]);
                                }
                            }
                            getMediaFileList("IsLibraryFile", MessageType.OTHER_FILES_LIST);
                            break;
                        case PICTURE_FILES_LIST:
                            dataStr = msg.getData();
                            if (!dataStr.isEmpty()){
                                if (dataStr.equalsIgnoreCase("Reset")){
                                    offsetPicture = 0;
                                    this.lastPhoto = 0l;
                                }
                                else{
                                    String SEPARATOR_CHAR = "\\|";
                                    String [] parameters = dataStr.split(SEPARATOR_CHAR);
                                    this.lastPhoto = Long.valueOf(parameters[0]);
                                    offsetPicture = Integer.valueOf(parameters[1]);
                                }
                            }
                            getMediaFileList("IsPictureFile", MessageType.PICTURE_FILES_LIST);
                            break;
                        case RECORD_A_SHOW:
                            recordAShow(msg.getData());
                            break;
                        case SEARCH_BY_TITLE:
                            dataStr = searchByTitle(msg.getData());
                            if (!dataStr.isEmpty()) 
                                send(new Message(MessageType.SEARCH_BY_TITLE, dataStr));
                            break;
                        case MATCH_EXACT_TITLE:
                            dataStr = searchByExactTitle(msg.getData());
                            if (!dataStr.isEmpty()) 
                                send(new Message(MessageType.MATCH_EXACT_TITLE, dataStr));
                            break;
                        case LAST_EPG_DOWNLOAD:
                            send(new Message(MessageType.LAST_EPG_DOWNLOAD,
                                    String.valueOf(sageApi.global.GetLastEPGDownloadTime()/100000)));
                            break;
                        case NEXT_EPG_DOWNLOAD:
                            send(new Message(MessageType.NEXT_EPG_DOWNLOAD,
                                    String.valueOf(sageApi.global.GetTimeUntilNextEPGDownload()/1000)));
                            break;
                        case STREAM_VLC_ALBUM:
                            showId = Integer.valueOf(msg.getData());
                            setVLCAlbum(showId);
                            break;
                        case STREAM_VLC:
                            showId = Integer.valueOf(msg.getData());
                            setVLC(showId);
                            break;
                        case STREAM_VLC_PROFILE:
                            StreamingProfile = Integer.valueOf(msg.getData());
                            break;
                        case SYSTEM_MESSAGE:
                            dataStr = msg.getData();
                            if (!dataStr.isEmpty()){
                                this.lastSM = Long.valueOf(dataStr);
                                if (isInitialized) initialSystemMessages();
                            }
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
            try
            {
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

    public void changeFavoriteFrequency(String message){
        String[] parts = message.split("\\|");
        if (parts.length < 3) return;
        for (String part : parts) logger.Message("Part = " + part);
        Favorite favorite = sageApi.favoriteAPI.GetFavoriteForID(Integer.parseInt(parts[0]));
        favorite.SetRunStatus(parts[1].equalsIgnoreCase("True"), parts[2].equalsIgnoreCase("True"));
    }

    public void deleteFavoriteShow(String show){
        int id = Integer.parseInt(show);

        try{
            Favorite favorite = sageApi.favoriteAPI.GetFavoriteForID(id);
            favorite.RemoveFavorite();
        }
        catch (Exception e) {
            try{
                Airing airing = sageApi.airingAPI.GetAiringForID(id);
                airing.CancelRecord();
            }
            catch (Exception ex){}
        }
    }

    /** Get all the channels, on all the lineups. */
    public void getAllChannels() {
        for (String lineup : sageApi.global.GetAllLineups())
            getChannelsOnLineup(lineup);
    }

    /** Get all the buttons. */
    public void getButtons(){
        StringBuilder buttons = new StringBuilder("{\"Buttons\": [");

        buttons.append(JSONUtil.createJSONButton(Button1)).append(",");
        buttons.append(JSONUtil.createJSONButton(Button2)).append(",");
        buttons.append(JSONUtil.createJSONButton(Button3)).append(",");
        buttons.append(JSONUtil.createJSONButton(Button4)).append(",");
        buttons.append(JSONUtil.createJSONButton(Button5)).append(",");
        buttons.append(JSONUtil.createJSONButton(Button6)).append(",");
        buttons.append(JSONUtil.createJSONButton(Button7));
        buttons.append("]}");
        
        send(new Message(MessageType.BUTTON, buttons.toString()));
    }

    /** Get all the channels, on a single lineup. */
    public void getChannelsOnLineup(String lineup) {
        Integer counter = 1;
        logger.Debug("Finding all the channels for lineup: " + lineup + ".");
        ChannelAPI.List allChannels = sageApi.database.GetChannelsOnLineup(lineup);
        allChannels = allChannels.Sort(false, "GetChannelNumber");
        Integer numberOfChannels = allChannels.size();
        logger.Debug(numberOfChannels.toString() + " channels found for lineup: " + lineup + ".");
        StringBuffer msgToSend = new StringBuffer();
        String channels = "", oneChannel = "";

        for (ChannelAPI.Channel channel : allChannels) {
            oneChannel = (isXML ? XMLUtil.createXMLChannel(channel, lineup)
                    : JSONUtil.createJSONChannel(channel, lineup));
            
            if (!oneChannel.isEmpty()) msgToSend.append(oneChannel);

            if (counter >= numberOfChannels){ // Should be maxItems...
                 if (isXML) channels = "<?xml version=\"1.0\" encoding=\"utf-8\"?><Collection><Lineup name=\"" +
                    XMLUtil.formatForXML(lineup) + "\">" + msgToSend.toString() + "</Lineup></Collection>";
                 else {
                     msgToSend.delete(msgToSend.length() - 2, msgToSend.length());

                     channels = "{\"Name\": \"" + JSONUtil.formatForJSON(lineup, false)
                         + "\", \"Lineup\": [" + msgToSend.toString() + "]}";
                 }

                send(new Message(MessageType.ALL_CHANNELS, channels));
                msgToSend = new StringBuffer();
            }
            
            counter++;
        }
    }

    public void getConflicts(){
        AiringAPI.List allAirings = sageApi.global.GetAiringsThatWontBeRecorded(true);

        if (allAirings.isEmpty()) logger.Message("There are no recording conflict currently");
        else logger.Message("Found some recording conflict: " + allAirings.size());

        ArrayList<Airing> addList = new ArrayList<Airing>();
        for (AiringAPI.Airing airing : allAirings) addList.add(airing);

        sendAiringsList(addList, new ArrayList<Integer>(), MessageType.SCHEDULING_CONFLICT_LIST);
    }

    private void recordAShow(String msg) {
        String SEPARATOR_CHAR = "\\|";
        String [] parameters = msg.split(SEPARATOR_CHAR);
        String type = parameters[0];
        Integer showID = Integer.valueOf(parameters[1]);
        
        recordAShow(type, showID);
    }
    
    /**
     * Record a show, given a type and ID.
     * 
     * @param type The type of recording (Manual, FirstRun, ReRun, Any).
     * @param showID The ID of the selected show.
     */
    public void recordAShow(String type, Integer showID) {
        logger.Debug("Type: " + type);
        logger.Debug("ShowID as int = " + showID.toString());
        Airing air = sageApi.airingAPI.GetAiringForID(showID);
        
        if (type.equalsIgnoreCase("manual")) {
            logger.Debug("Manual case...");
            air.SetRecordingTimes(air.GetScheduleStartTime(), air.GetScheduleEndTime());
        }
        else {
            logger.Debug("New favorite...");
            boolean isReruns = !type.equalsIgnoreCase("firstrun");
            boolean isFirstRuns = !type.equalsIgnoreCase("rerun");
            logger.Debug(air.GetAiringTitle());
            sageApi.favoriteAPI.AddFavorite(air.GetAiringTitle(), isFirstRuns,
                    isReruns, "", "", "", "", "", "", "", "", "", "", "");
        }
     }

    /**
     * Search for all TV airings matching the exact title.
     * 
     * @param title The title to search for.
     * 
     * @return A list of airings. (XML String)
     */
    public String searchByExactTitle(String title) {
        logger.Debug("Finding the future airings of: " + title);
        Date now = new Date();
        AiringAPI.List allAirings;
        long startTime = now.getTime();
        StringBuilder msgToSend = new StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?><Collection>" +
            "<Title name=\"" + title + "\">");
        
        allAirings = sageApi.database.SearchByTitle(title, "T");
        logger.Debug(String.valueOf(allAirings.size()) + " unfiltered airings found for: " + title);

        for (AiringAPI.Airing air : allAirings) {
            if (air.GetScheduleStartTime() > startTime) msgToSend.append(XMLUtil.createXMLString
                    (air, air.GetAiringID(), "Program", sageApi.showAPI, false));
        }
        
        msgToSend.append("</Title></Collection>");

        return msgToSend.toString();   
    }
    
    /**
     * Search for all TV airings matching the title.
     * 
     * @param title The title to search for.
     * 
     * @return A list of airings. (XML String)
     */
    public String searchByTitle(String title) {
        logger.Debug("Finding the future airings of: " + title);
        String[] allTitles = sageApi.database.SearchForTitles(title, "T");
        Date now = new Date();
        AiringAPI.List allAirings;
        int count = 0;
        long startTime = now.getTime();
        StringBuilder msgToSend = new StringBuilder();

        if (isXML) msgToSend.append("<?xml version=\"1.0\" encoding=\"utf-8\"?><Collection>")
                .append("<Title name=\"").append(title).append("\">");
        else if (isJSON2) msgToSend.append("{\"Search guide\": [");
        else msgToSend.append("{\"Searches\": [");

        for (String oneTitle : allTitles) {
            allAirings = sageApi.database.SearchByTitle(oneTitle, "T");
            logger.Debug(String.valueOf(allAirings.size()) + " unfiltered airings found for: " + oneTitle);

            for (AiringAPI.Airing air : allAirings) {
                if (air.GetScheduleStartTime() > startTime){
                    if (isXML) msgToSend.append(XMLUtil.createXMLString
                        (air, air.GetAiringID(), "Program", sageApi.showAPI, false));
                    else{
                        msgToSend.append(JSONUtil.createJSONString(air,
                        air.GetAiringID(), "Program", sageApi.showAPI, "", false, "IsTVFile", ""));
                        count++;
                    }
                }
            }
        }
        
        if (isXML) msgToSend.append("</Title></Collection>");
        else {
            if (count > 0) msgToSend.delete(msgToSend.length() - 2, msgToSend.length());
            msgToSend.append("]}");
        }

        return msgToSend.toString();   
    }

    private void getAiringsOnChannelAtTime(String msg) {
        String SEPARATOR_CHAR = "|";
        long endTime = -1;
        Integer highIndex = msg.indexOf(SEPARATOR_CHAR);
        Integer stationID = Integer.valueOf(msg.substring(0, highIndex));
        Integer lowIndex = highIndex + 1;
        highIndex = msg.indexOf(SEPARATOR_CHAR, lowIndex);
        long startTime = Long.valueOf(msg.substring(lowIndex, highIndex));
        lowIndex = highIndex + 1;
        highIndex = msg.indexOf(SEPARATOR_CHAR, lowIndex);
        
        if (highIndex == -1) {
            logger.Debug("Using days...");
            Date now = new Date();
            endTime = now.getTime() + startTime * 24 * 3600000;
            startTime = now.getTime();
        }
        else endTime = Long.valueOf(msg.substring(lowIndex, highIndex));
        
        getAiringsOnChannelAtTime(stationID, startTime, endTime, false);
    }
    
   /**
    * Returns the list of Airings on a specific Channel between a start time
    * and an end time.
    * 
    * @param stationID The station ID of the selected channel.
    * @param startTime The start of the time window (Java time)
    * @param endTime The end of the time window (Java time)
    * @param mustStartDuringTime If true, then only Airings 
    * that start during the time window will be returned, if false then 
    * any Airing that overlaps with the time window will be returned 
    * 
    * @return msgToSend XMLString - The list of Airings.
    */
    public void getAiringsOnChannelAtTime(Integer stationID, long startTime,
            long endTime, boolean mustStartDuringTime) {
        ChannelAPI.Channel channel = sageApi.channelAPI.GetChannelForStationID(stationID);
        logger.Debug("Finding all the airings on channel:" + channel.GetChannelNumber());
        AiringAPI.List allAirings = sageApi.database.GetAiringsOnChannelAtTime(channel, startTime, endTime, mustStartDuringTime);
        logger.Debug(String.valueOf(allAirings.size()) + " airings found.");
        if (allAirings.isEmpty()) return;
        StringBuilder msgToSend = new StringBuilder();
        int count = 0, id;
        int max = (maxItems != -1 ? maxItems : 2 * DEFAULT_MAXITEMS);

        if (isXML) msgToSend.append("<?xml version=\"1.0\" encoding=\"utf-8\"?><Collection><Station ID=\"")
                .append(stationID).append("\">");
        else if (isJSON2) msgToSend.append("{\"Program guide\": [");
        else msgToSend.append("{\"Programs\": [");

        for (AiringAPI.Airing air : allAirings) {
            if (count >= max){
                if (isXML) msgToSend.append("</Station></Collection>");
                else{
                    msgToSend.delete(msgToSend.length() - 2, msgToSend.length());
                    msgToSend.append("]}");
                }
                send(new Message(MessageType.AIRINGS_ON_CHANNEL_AT_TIME, msgToSend.toString()));
                count = 0;

                if (maxItems != -1) break;
                else {
                    msgToSend = new StringBuilder();
                    if (isXML) msgToSend.append("<?xml version=\"1.0\" encoding=\"utf-8\"?><Collection><Station ID=\"")
                            .append(stationID).append("\">");
                    else if (isJSON2) msgToSend.append("{\"Program guide\": [");
                    else msgToSend.append("{\"Programs\": [");
                }
            }

            if (isXML) msgToSend.append(XMLUtil.createXMLString(air,
                    air.GetAiringID(), "Program", sageApi.showAPI, true));
            else msgToSend.append(JSONUtil.createJSONString(air,
                    air.GetAiringID(), "Program", sageApi.showAPI, "", true, "IsTVFile", ""));
            count++;
        }

        if (count == 0) return;
        else if(isXML) msgToSend.append("</Station></Collection>");
        else{
            msgToSend.delete(msgToSend.length() - 2, msgToSend.length());
            msgToSend.append("]}");
        }
        send(new Message(MessageType.AIRINGS_ON_CHANNEL_AT_TIME, msgToSend.toString()));
    }

    public void initialManualRecordingsList() {
        ArrayList<Airing> addList = new ArrayList<Airing>();
        Date now = new Date();
        long startTime = now.getTime() - 3600000;
        long endTime = now.getTime() + 14 * 24 * 3600000;

        logger.Debug("Finding all the manual recordings...");
        AiringAPI.List allAirings = sageApi.database.GetAiringsOnViewableChannelsAtTime(startTime, endTime, false);
        allAirings = allAirings.FilterByBoolMethod("IsManualRecord", true);
        logger.Debug(String.valueOf(allAirings.size()) + " manual recording(s) found.");

        if (allAirings.isEmpty()) return;

        for (AiringAPI.Airing airing : allAirings) addList.add(airing);

        sendAiringsList(addList, new ArrayList<Integer>(), MessageType.MANUAL_RECORDINGS_LIST);
    }

    public void initialFavoriteList() {
        ArrayList<Favorite> toAddList = new ArrayList<Favorite>();
        int count = 0;
        logger.Debug("Finding all the favorites...");
        FavoriteAPI.List favorites = sageApi.favoriteAPI.GetFavorites();
        logger.Debug(String.valueOf(favorites.size()) + " favorites found.");

        if (favorites.isEmpty()) return;

        for (Favorite favorite : favorites){
            count++;
            toAddList.add(favorite);
            if (count >= 25){
                sendFavorites(toAddList, new ArrayList<Favorite>(), new ArrayList<Integer>());
                toAddList = new ArrayList<Favorite>();
                count = 0;
            }
        }
        
        if (toAddList.size() > 0) sendFavorites(toAddList, new ArrayList<Favorite>(), new ArrayList<Integer>());
    }

   /**
    * Returns the list of Favorite shows (Recording jobs), the format of the 
    * output is dependant on the answer type (TXT|XML}.  Nothing is sent if
    * there was no changes since the last transmission.
    * 
    * @return msgToSend String - The list of Favorite shows.
    */
    public void sendFavorites(ArrayList<Favorite> favorites, ArrayList<Favorite> modFavorites, ArrayList<Integer> toDeleteList) {
        StringBuilder msgToSend = new StringBuilder();

        if (isXML) msgToSend.append("<?xml version=\"1.0\" encoding=\"utf-8\"?><Collection><List Name=\"Favorites\">");
        else msgToSend.append("{\"Favorites\": [");

        for (Integer toDelete : toDeleteList) {
            if (isXML) msgToSend.append(XMLUtil.createXMLString(toDelete, "Program", "Del"));
            else msgToSend.append(JSONUtil.createJSONString(toDelete, "Del"));
        }

        for (Favorite favorite : favorites){
            if (isXML) msgToSend.append(XMLUtil.createXMLString(favorite, favorite.GetFavoriteID(), "Program", "Add"));
            else msgToSend.append(JSONUtil.createJSONString(favorite, favorite.GetFavoriteID(), "Program", "Add"));
        }

        for (Favorite favorite : modFavorites){
            if (isXML) msgToSend.append(XMLUtil.createXMLString(favorite, favorite.GetFavoriteID(), "Program", "Mod"));
            else msgToSend.append(JSONUtil.createJSONString(favorite, favorite.GetFavoriteID(), "Program", "Mod"));
        }

        if (isXML) msgToSend.append("</List></Collection>");
        else{
            msgToSend.delete(msgToSend.length() - 2, msgToSend.length());
            msgToSend.append("]}");
        }
        
        send(new Message(MessageType.FAVORITE_SHOW_LIST, msgToSend.toString()));
    }

    /** Get the list of all the lineups. */
    public String getLineups() {
        logger.Debug("Finding all the lineups...");
        String [] lineups = sageApi.global.GetAllLineups();
        logger.Debug(String.valueOf(lineups.length) + " lineups found.");
        StringBuilder msgToSend = new StringBuilder(String.valueOf(lineups.length));

        for (String lineup : lineups) 
            msgToSend.append(this.isXML ? DATA_SEPARATOR : "~").append(lineup);

        return msgToSend.toString();
    }

    /** Delete a show (media file) based on a show ID */
    public boolean deleteShow(Integer showId) {
        try {
            MediaFileAPI.MediaFile mf = sageApi.mediaFileAPI.GetMediaFileForID(showId);
        
            return mf.DeleteFileWithoutPrejudice();
        }
        catch (Exception e) {
            return false;
        }
    }

            /** Setup VLC to stream a show (media file) based on a show ID */
    public void setVLCAlbum(Integer showId) {
        try {
            MediaFileAPI.MediaFile mf = sageApi.mediaFileAPI.GetMediaFileForID(showId);

            if (mf == null){
                logger.Message("[setVLCAlbum] No such file, aborting the streaming...");
                return;
            }
            else{
                AlbumAPI.Album album = mf.GetAlbumForFile();
                StringBuilder sb = new StringBuilder(" ");

                for (AiringAPI.Airing air : album.GetAlbumTracks())
                    sb.append("\"").append(air.GetMediaFileForAiring().GetFileForSegment(0)).append("\" ");

                setVLC(sb.toString());
            }
        }
        catch (Exception e) {
            logger.Debug("[setVLCAlbum]: " + e.getMessage());
        }
    }

    /** Setup VLC to stream a show (media file) based on a show ID */
    public void setVLC(Integer showId) {
        try {
            MediaFileAPI.MediaFile mf = sageApi.mediaFileAPI.GetMediaFileForID(showId);
            
            if (mf == null){
                logger.Message("[setVLC] No such file, aborting the streaming...");
                return;
            }
            else setVLC(" \""  + mf.GetFileForSegment(0) + "\" ");
        }
        catch (Exception e) {
            logger.Debug("[setVLC]: " + e.getMessage());
        }
    }

    /** Setup VLC to stream the content */
    public void setVLC(String content) {
        if (StreamingPort == -1) {
            logger.Debug("[setVLC]: Streaming port not set!");
            return;
        }

        try {
            if (LocalIP.isEmpty()) LocalIP = InetAddress.getLocalHost().getHostAddress();

            StringBuilder sb = new StringBuilder("\"");
            sb.append(StreamingVLCPath).append("\" ");
            sb.append(StreamingVLCOptions);
            sb.append(content);
            sb.append("--sout \"#duplicate{dst='transcode{");

            switch(StreamingProfile){
                case 0: sb.append(StreamingTranscodeOptions); break;
                case 1: sb.append(StreamingTranscodeOptions1); break;
                case 2: sb.append(StreamingTranscodeOptions2); break;
                case 3: sb.append(StreamingTranscodeOptions3); break;
                case 4: sb.append(StreamingTranscodeOptions4); break;
                case 5: sb.append(StreamingTranscodeOptions5); break;
                case 6: sb.append(StreamingTranscodeOptions6); break;
            }

            sb.append("}:");

            if (StreamingType.equalsIgnoreCase("http")){
                //sb.append("std{access=http,mux=ts,dst=");
                //sb.append(LocalIP).append(":");
                //sb.append(StreamingPort).append("}}");
            }
            else if (StreamingType.equalsIgnoreCase("rtsp")){
//                sb.append("rtp{dst=");
//                sb.append(LocalIP).append(",").append(StreamingPort);
//                sb.append(",sdp=rtsp://"); //.append(LocalIP).append(":");
//                sb.append("0.0.0.0").append(":");
                sb.append("gather:rtp{sdp=rtsp://:");
                sb.append(StreamingPort).append("/stream.sdp");
                //sb.append(",").append(StreamingRTSPType);
                sb.append("}'}\"");
            }

            sb.append(" vlc://quit");

            logger.Debug(sb.toString());
            Runtime.getRuntime().exec(sb.toString());
        }
        catch (Exception e) {
            logger.Debug("[setVLC]: " + e.getMessage());
        }
    }

    public void initialSystemMessages(){
        SystemMessageAPI.List messages = sageApi.systemMessageAPI.GetSystemMessages();
        ArrayList<SystemMessage> newMessages = new ArrayList<SystemMessage>();
        if (messages == null || messages.size() < 1) return;

        logger.Debug("Gathering "  + messages.size() + " system message(s).");

        for (int i = messages.size() - 1; i >= 0; i--){
            if (messages.get(i).GetSystemMessageTime()/rescale > this.lastSM)
                newMessages.add(messages.get(i));
            else break;
        }

        if (newMessages.isEmpty()) return;
        else logger.Debug("Found "  + newMessages.size() + " system message(s).");

        sendSystemMessages(newMessages);
    }

    public void sendSystemMessages(ArrayList<SystemMessage> newMessages){
        int count = 0;
        int max = (maxItems != -1 ? maxItems : 2 * DEFAULT_MAXITEMS);
        String result = "Done";
        StringBuilder msgToSend = new StringBuilder();
        if (isXML) msgToSend.append("<?xml version=\"1.0\" encoding=\"utf-8\"?><Collection><List Name=\"SystemMessages\">");
        else if (isJSON2) msgToSend.append("{\"Alerts\": [");
        else msgToSend.append("{\"SystemMessage\": [");

        for (SystemMessage message : newMessages){
            if (count >= max){
                if (isXML) msgToSend.append("</List></Collection>");
                else{
                    msgToSend.delete(msgToSend.length() - 2, msgToSend.length());
                    msgToSend.append("]}");
                }
                send(new Message(MessageType.SYSTEM_MESSAGE, msgToSend.toString()));
                count = 0;

                if (maxItems != -1){
                    result = "Partial";
                    break;
                }
                else {
                    msgToSend = new StringBuilder();
                    if (isXML) msgToSend.append("<?xml version=\"1.0\" encoding=\"utf-8\"?><Collection><List Name=\"SystemMessages\">");
                    else if (isJSON2) msgToSend.append("{\"Alerts\": [");
                    else msgToSend.append("{\"SystemMessage\": [");
                }
            }

            if (isXML) msgToSend.append(XMLUtil.createXMLString(message, "Message"));
            else msgToSend.append(JSONUtil.createJSONString(message));
            count++;
        }

        if (count > 0){
            if (isXML) msgToSend.append("</List></Collection>");
            else{
                msgToSend.delete(msgToSend.length() - 2, msgToSend.length());
                msgToSend.append("]}");
            }

            send(new Message(MessageType.SYSTEM_MESSAGE, msgToSend.toString()));
        }

        send(new Message(MessageType.SYSTEM_MESSAGE, result));
        this.lastSM = newMessages.get(0).GetSystemMessageTime()/rescale;
    }
    
   public void initialTVFileList() {
        ArrayList<Airing> addList = new ArrayList<Airing>();
        logger.Debug("Finding all the Recordings");
        MediaFileAPI.List allMediaFiles = sageApi.mediaFileAPI.GetMediaFiles();
        allMediaFiles = allMediaFiles.FilterByBoolMethod("IsTVFile", true);

        if (allMediaFiles.isEmpty()) return;

        allMediaFiles = allMediaFiles.SortLexical(true, "GetFileStartTime");
        logger.Debug(allMediaFiles.size() + " files found.");
        checkAvailableData("IsTVFile", allMediaFiles.size());

        for (MediaFile mf : allMediaFiles) addList.add(mf.GetMediaFileAiring());

        sendAiringsList(addList, new ArrayList<Integer>(), MessageType.RECORDED_SHOW_LIST);
    }

      /**
    * Returns the list of Other video files, Picture file.  The format of
    * the output is dependant on the answer parameter (TXT|XML}.  Nothing
    * is sent if there was no changes since the last transmission.
    *
    * @param Type Can be any other video files
    * (IsLibraryFile) or picture (IsPicture).
    *
    * @return msgToSend String - The list of Recorded shows.
    */
   public void getMediaFileList(String type, MessageType mt) {
        boolean isLibraryFile = type.equalsIgnoreCase("IsLibraryFile");
        int mediaFileID = 0, count = 0;
        int max = (maxItems != -1 ? maxItems : (isLibraryFile ? 1 : 4) * DEFAULT_MAXITEMS);
        int offset = (isLibraryFile ? offsetOther : offsetPicture);
        long lastTime = (isLibraryFile ? lastVideo : lastPhoto);
        String filename = "", result = "Done";
        StringBuilder msgToSend = new StringBuilder();
        logger.Debug("Finding all the listings for: " + type);
        logger.Debug("Offset: " + offset + ", last time: " + lastTime);

        MediaFileAPI.List allMediaFiles =
                sageApi.mediaFileAPI.GetMediaFiles().FilterByBoolMethod(type, true);

        if (isLibraryFile)
            allMediaFiles = allMediaFiles.FilterByBoolMethod("IsPictureFile|IsMusicFile", false);

        allMediaFiles = allMediaFiles.SortLexical(true, "GetFileStartTime");
        int size = allMediaFiles.size();
        logger.Debug(size + " files found.");
        checkAvailableData(type, allMediaFiles.size());

        if (allMediaFiles.isEmpty()){
            send(new Message(mt, result));
            return;
        }

        if (isXML) msgToSend.append("<?xml version=\"1.0\" encoding=\"utf-8\"?><Collection><List Name=\"")
                .append(type).append("\">");
        else msgToSend.append("{\"").append(isLibraryFile ? "Videos" : "Photos").append("\": [");

        MediaFileAPI.MediaFile mf;
        AiringAPI.Airing airing = null;
        for (int i = 0; i + offset < size; i++) {
            if (count >= max) {
                if (isLibraryFile) offsetOther += count;
                else offsetPicture += count;
                if (isXML) msgToSend.append("</List></Collection>");
                else{
                    msgToSend.delete(msgToSend.length() - 2, msgToSend.length());
                    msgToSend.append("]}");
                }
                send(new Message(mt, msgToSend.toString()));
                count = 0;

                if (maxItems != -1){
                    result = "Partial";
                    break;
                }
                else {
                    msgToSend = new StringBuilder();
                    if (isXML) msgToSend.append("<?xml version=\"1.0\" encoding=\"utf-8\"?><Collection><List Name=\"")
                            .append(type).append("\">");
                    else msgToSend.append("{\"").append(isLibraryFile ? "Videos" : "Photos").append("\": [");
                }
            }

            mf = allMediaFiles.get(size - 1 - (offset + i) );
            airing = mf.GetMediaFileAiring();

            if (airing.GetAiringStartTime()/rescale <= lastTime) continue;
            else count++;
            
            mediaFileID = mf.GetMediaFileID();

            try
            {
                filename = mf.GetSegmentFiles()[0].getPath();
                if (isXML) msgToSend.append(XMLUtil.createXMLString(airing,
                    mediaFileID, "Program", sageApi.showAPI, "Add", "", false, type, filename));
                else msgToSend.append(JSONUtil.createJSONString(airing,
                    mediaFileID, "Program", sageApi.showAPI, "Add", false, type, filename));
            }
            catch (Exception e) { }
        }
        
        if (count > 0){
            if (isLibraryFile) offsetOther += count;
            else offsetPicture += count;
            if (isXML) msgToSend.append("</List></Collection>");
            else{
                msgToSend.delete(msgToSend.length() - 2, msgToSend.length());
                msgToSend.append("]}");
            }
            send(new Message(mt, msgToSend.toString()));
       }

        if (isLibraryFile) lastVideo = airing.GetAiringStartTime()/rescale;
        else lastPhoto = airing.GetAiringStartTime()/rescale;
        send(new Message(mt, result));
    }

   /** */
   public void getAlbumList() {
        boolean sentAny = false;
        Integer mediaFileID, index, albumCount = 0;
        String result = "Done";
        StringBuilder msgToSend = new StringBuilder();
        logger.Debug("Finding all the albums");
        AlbumAPI.List allAlbums =  sageApi.albumAPI.GetAlbums().SortLexical(false, "GetAlbumName");
        int size = allAlbums.size();
        logger.Debug(String.valueOf(size) + " albums found.");
        checkAvailableData("IsAlbums", size);
        
        AlbumAPI.Album album;
        for (int i = 0; i + offsetAlbum < size; i++){
            if (maxItems != -1 && albumCount >= maxItems) {
                result = "Partial";
                break;
            }
            else album = allAlbums.get(i + offsetAlbum);
            
            if (album.GetAlbumTracks().size() > 0){
                albumCount++;

                index = 1;
                if (isXML) msgToSend = new StringBuilder(XMLUtil.createXMLAlbum(album));
                else msgToSend = new StringBuilder(JSONUtil.createJSONAlbum(album));

                for (AiringAPI.Airing air : album.GetAlbumTracks()) {
                    mediaFileID = air.GetMediaFileForAiring().GetMediaFileID();

                    if (index < 4 * DEFAULT_MAXITEMS) {
                        try {
                            if (isXML) msgToSend.append(XMLUtil.createXMLSong(air, mediaFileID, sageApi.showAPI,
                                    album.GetAlbumArtist(), album.GetAlbumGenre(), index));
                            else msgToSend.append(JSONUtil.createJSONSong(air, mediaFileID, sageApi.showAPI,
                                    album.GetAlbumArtist(), album.GetAlbumGenre(), index));
                            index++;
                        }
                        catch (Exception e) { }
                    }
                    //else toDeleteList.remove(toDeleteList.indexOf(mediaFileID));
                }

                if (isXML) msgToSend.append("</Album></List></Collection>");
                else{
                    msgToSend.delete(msgToSend.length() - 2, msgToSend.length());
                    msgToSend.append("]}");
                }
                send(new Message(MessageType.MUSIC_FILES_LIST, msgToSend.toString()));
                sentAny = true;
            }
        }
        
        if (sentAny){
            offsetAlbum += albumCount;
            send(new Message(MessageType.MUSIC_FILES_LIST, result));
        }
        else logger.Debug("No message to send...");
    }
   
   public String imageFromMeta(String metaImage) {
       String filename = "";
       int endPosition = metaImage.indexOf(".jpg#0");
       if (endPosition > 10) filename = metaImage.substring(10, endPosition + 4);
       return filename;
   }
    
   /**
    * Returns the list of Upcoming episodes.
    * 
    * @param EPGID The global unique ID which represents this show.
    * 
    * @return msgToSend XMLString - The list of Upcoming episodes.
    */
    public String getUpcomingEpisodesList(String EPGID) {
        ShowAPI.Show show = sageApi.showAPI.GetShowForExternalID(EPGID);
        logger.Debug("Finding all the upcoming episodes for:" + show.GetShowTitle());
        Date now = new Date();
        AiringAPI.List allAirings = show.GetAiringsForShow(now.getTime());
        logger.Debug(String.valueOf(allAirings.size()) + " upcoming episodes found.");
        StringBuilder msgToSend = new StringBuilder();

        if (isXML) msgToSend.append("<?xml version=\"1.0\" encoding=\"utf-8\"?><Collection><EPGID ID=\"")
                .append(EPGID).append("\">");
        else msgToSend.append("{\"UpcomingEpisodes\": [");
        
        for (AiringAPI.Airing air : allAirings) {
            if (isXML) msgToSend.append(XMLUtil.createXMLString(air, air.GetAiringID(),
                    "Program", sageApi.showAPI, true));
            else msgToSend.append(JSONUtil.createJSONString(air, air.GetAiringID(),
                    sageApi.showAPI, true));
        }

        if (isXML) msgToSend.append("</EPGID></Collection>");
        else if (allAirings.size() == 0) msgToSend.append("]}");
        else{
            msgToSend.delete(msgToSend.length() - 2, msgToSend.length());
            msgToSend.append("]}");
        }

        return msgToSend.toString();   
    }

    /** Returns the used and available disk space */
    public void getVideoDiskSpace() {
        long usedVideoDiskSpace = sageApi.global.GetUsedVideoDiskspace() / 1048576;
        long totalDiskSpace = sageApi.global.GetTotalDiskspaceAvailable() / 1048576;

        videoDiskSpaceString = String.valueOf(usedVideoDiskSpace) + "|" + String.valueOf(totalDiskSpace);
        send(new Message(MessageType.VIDEO_DISK_SPACE, videoDiskSpaceString));
    }

    public void initialUpcomingRecordingsList() {
        logger.Debug("Finding all upcoming recordings.....");
        AiringAPI.List upcomingRecordings = sageApi.global.GetScheduledRecordings();
        logger.Debug(upcomingRecordings.size() + " scheduled recording(s) found.");

        if (upcomingRecordings.isEmpty()) return;

        upcomingRecordings = upcomingRecordings.SortLexical(false, "GetAiringStartTime");
        ArrayList<Airing> addList = new ArrayList<Airing>();
        ArrayList<Integer> airingList = new ArrayList<Integer>();

        for (Airing airing : upcomingRecordings){
            addList.add(airing);
            airingList.add(airing.GetAiringID());
        }

        if (TCPServerMedia.allUpcomingRecordings.isEmpty())
            TCPServerMedia.allUpcomingRecordings = airingList;

        sendAiringsList(addList, new ArrayList<Integer>(), MessageType.UPCOMING_RECORDINGS_LIST);
        getConflicts();
    }

    /**
    * Returns the list of Upcoming recordings, the format of the output is
    * dependant on the answer type (XML|JSON}.  Nothing is sent if there was
    * no changes since the last transmission.
    */
    public void sendAiringsList(ArrayList<Airing> addList, ArrayList<Integer> toDeleteList, MessageType mt) {
        boolean hasFilenames = false;
        int count = 0, id;
        int max = (maxItems != -1 ? maxItems : DEFAULT_MAXITEMS);
        String result = "Done", filename = "", filetype = "IsTVFile", type = "Upcoming";
        StringBuilder msgToSend = new StringBuilder();

        switch (mt){
            case MANUAL_RECORDINGS_LIST:
                filetype = "IsManual";
                type = (this.isJSON2 ? "Manual" : "Favorites");
                break;
            case OTHER_FILES_LIST:
                filetype = "IsTVFile";
                type = "Videos";
                hasFilenames = true;
                break;
            case PICTURE_FILES_LIST:
                filetype = "IsPictureFile";
                type = "Photos";
                hasFilenames = true;
                break;
            case RECORDED_SHOW_LIST:
                filetype = "IsTVFile";
                type = "Recordings";
                hasFilenames = true;
                break;
            case SCHEDULING_CONFLICT_LIST:
                filetype = "IsTVFile";
                type = "Conflicts";
                break;
            case UPCOMING_RECORDINGS_LIST:
                filetype = "IsTVFile";
                type = (this.isJSON2 ? "Schedule" : "Upcoming");
                break;
        }

        if (isXML) msgToSend.append("<?xml version=\"1.0\" encoding=\"utf-8\"?><Collection><List Name=\"")
                .append(type).append("\">");
        else msgToSend.append("{\"").append(type).append("\": [");

        for (AiringAPI.Airing airing : addList) {
            if (count >= max){
                if (isXML) msgToSend.append("</List></Collection>");
                else{
                    msgToSend.delete(msgToSend.length() - 2, msgToSend.length());
                    msgToSend.append("]}");
                }
                send(new Message(mt, msgToSend.toString()));
                count = 0;

                if (maxItems != -1){
                    result = "Partial";
                    break;
                }
                else {
                    msgToSend = new StringBuilder();
                    if (isXML) msgToSend.append("<?xml version=\"1.0\" encoding=\"utf-8\"?><Collection><List Name=\"")
                            .append(type).append("\">");
                    else msgToSend.append("{\"").append(type).append("\": [");
                }
            }

            filename = (hasFilenames ? airing.GetMediaFileForAiring().GetSegmentFiles()[0].getPath() : "");
            id = (hasFilenames ? airing.GetMediaFileForAiring().GetMediaFileID() : airing.GetAiringID());

            if(isXML) msgToSend.append(XMLUtil.createXMLString(airing,
                    id, "Program", sageApi.showAPI, "Add", "", false, filetype, filename));
            else{
                msgToSend.append(JSONUtil.createJSONString(airing,
                    id, "Program", sageApi.showAPI, "Add", false, filetype, filename));
            }
            count++;
        }
        
        for (Integer toDelete : toDeleteList) {
            if (isXML) msgToSend.append(XMLUtil.createXMLString(toDelete, "Program", "Del"));
            else msgToSend.append(JSONUtil.createJSONString(toDelete, "Del"));
        }

        if (isXML) msgToSend.append("</List></Collection>");
        else if (count == 0 && toDeleteList.isEmpty()){
            send(new Message(mt, result));
            return;
        }
        else{
            msgToSend.delete(msgToSend.length() - 2, msgToSend.length());
            msgToSend.append("]}");
        }
        
        send(new Message(mt, msgToSend.toString()));
        send(new Message(mt, result));
    }
    
    private void checkAvailableData(String type, int size){
        int count = TCPServerMedia.albumSize + TCPServerMedia.photoSize + TCPServerMedia.videoSize;

        if (type.equalsIgnoreCase("IsAlbums")) TCPServerMedia.albumSize = size;
        else if (type.equalsIgnoreCase("IsPictureFile")) TCPServerMedia.photoSize = size;
        else if (type.equalsIgnoreCase("IsLibraryFile")) TCPServerMedia.videoSize = size;

        if (count != TCPServerMedia.albumSize + TCPServerMedia.photoSize + TCPServerMedia.videoSize)
            sendAvailableData();
    }

    private void sendAvailableData(){
        if (TCPServerMedia.albumSize + TCPServerMedia.photoSize + TCPServerMedia.videoSize == 0) return;

        String data = TCPServerMedia.albumSize + "," + TCPServerMedia.photoSize + "," + TCPServerMedia.videoSize;
        send(new Message(MessageType.TOTAL_AVAILABLE_DATA, data));
    }

    private void getClients(){
        int numberOfClients = StartServers.listOfClientNames.size();
        int numberOfExtenders = StartServers.listOfExtenderNames.size();

        if (numberOfClients + numberOfExtenders == 0) return;

        StringBuilder msgToSend = new StringBuilder();
        if (isXML) msgToSend.append("<?xml version=\"1.0\" encoding=\"utf-8\"?><Collection><List Name=\"Clients\">");
        else msgToSend.append("{\"Clients\": [");


        for (int i = 0; i < numberOfClients; i++){
            if (isXML) msgToSend.append(XMLUtil.createXMLClient(StartServers.listOfClientNames.get(i),
                        StartServers.listOfClientPorts.get(i), StartServers.listOfClients.get(i)));
            else msgToSend.append(JSONUtil.createJSONClient(StartServers.listOfClientNames.get(i),
                        StartServers.listOfClientPorts.get(i), StartServers.listOfClients.get(i)));
        }

        for (int i = 0; i < numberOfExtenders; i++){
            if (isXML) msgToSend.append(XMLUtil.createXMLClient(StartServers.listOfExtenderNames.get(i),
                    StartServers.listOfExtenderPorts.get(i), StartServers.listOfExtenderMacIDs.get(i)));
            else msgToSend.append(JSONUtil.createJSONClient(StartServers.listOfExtenderNames.get(i),
                    StartServers.listOfExtenderPorts.get(i), StartServers.listOfExtenderMacIDs.get(i)));
        }

        if (isXML) msgToSend.append("</List></Collection>");
        else {
            msgToSend.delete(msgToSend.length() - 2, msgToSend.length());
            msgToSend.append("]}");
        }

        send(new Message(MessageType.LIST_OF_CLIENTS, msgToSend.toString()));
    }
}
