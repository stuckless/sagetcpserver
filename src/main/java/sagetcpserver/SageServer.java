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
import java.io.*;
import java.net.*;
import java.util.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.StringTokenizer;
import sagetcpserver.messages.MessageType;
import sagetcpserver.messages.Message;
import sagetcpserver.utils.IntervalTimer;
import sagetcpserver.utils.JSONUtil;
import sagetcpserver.utils.SageLogger;
import sagetcpserver.utils.XMLUtil;
import java.util.regex.Pattern;

/**
 * This is where the SageTCTPServer is created and all the procesing is handled.
 * 
 * @author Rob + Fonceur
 */
public class SageServer implements Runnable {

    public static int StreamingPort = -1;
    public static String LocalIP = "", Password = "";
    public static String StreamingRTSPType = "mp4a-latm";
    public static String StreamingTranscodeOptions = "fps=15,vcodec=mp4v,vb=512,scale=1,width=352,"
            + "height=240,acodec=mp4a,ab=192,channels=2,samplerate=44100,deinterlace,audio-sync";
//    public static String StreamingTranscodeOptions = "soverlay,ab=100,samplerate=44100,channels=2," +
//            "acodec=mp4a,vcodec=h264,width=320,height=180,vfilter=\"canvas{width=320,height=180," +
//            "aspect=16:9}\",fps=29,vb=200,venc=x264{vbv-bufsize=500,partitions=all,level=12,no-cabac," +
//            "subme=7,threads=4,ref=2,mixed-refs=1,bframes=0,min-keyint=1,keyint=50,trellis=2," +
//            "direct=auto,qcomp=0.0,qpmax=51,deinterlace}";
//    public static String StreamingTranscodeOptions = "vcodec=h264,venc=x264{no-cabac,level=12,vbv-maxrate=300," +
//            "vbv-bufsize=1000,keyint=75,ref=3,bframes=0},width=320,height=192,acodec=mp4a,ab=64,vb=300," +
//            "samplerate=22050,audio-sync";
    public static String StreamingType = "rtsp";
    public static String StreamingVLCOptions = "-I dummy --one-instance --extraintf oldhttp --sout-keep"
            + " --no-sout-rtp-sap --no-sout-standard-sap --rtsp-caching=5000 -f";
    //"-I dummy --one-instance --no-sub-autodetect-file";  // qt
    public static String StreamingVLCPath = "C:/Program Files/VideoLAN/VLC/vlc.exe";

    private static final int SERVER_UPDATE_RATE_MS = 500;
    private static final char DATA_SEPARATOR = (char)0x7c;
    private static final char STX = (char)0x02;
    private static final char ETX = (char)0x03;
    private static final int DEFAULT_MAXITEMS = 25;
    private static final int LOW_RATE_CYCLES = 4;
    private static final int VERY_LOW_RATE_MINS = 5;
    private static final int VERY_LOW_RATE_CYCLES = ((1000 / SERVER_UPDATE_RATE_MS) * 60 * VERY_LOW_RATE_MINS);
    private ArrayList<Integer> allFavorites = new ArrayList<Integer>();
    private ArrayList<Integer> allManual = new ArrayList<Integer>();
    private ArrayList<Integer> allRecordedShows = new ArrayList<Integer>();
    private ArrayList<Integer> allUpcomingRecordings = new ArrayList<Integer>();
    private static Integer numberOfRecordings = -1, offsetOther = 0, offsetPicture = 0;
    private Integer pingCount = 0;
    private static String videoDiskSpaceString = "";
    /** Mode of this TCP server {Media|Player}. */
    public String serverMode = "Media";
    /** Type of answer expected by the TCP client {TXT|XML}. */
    public String extendedMessageFormat = "TXT";
    private boolean isTXT = true;
    private boolean isXML = false;
    /** Maximum number of items to send per list {-1 = no limit}. */
    public Integer maxItems = 1000, albumSize = 0, photoSize = 0, videoSize = 0;
    
    private API sageApi;
    private AiringAPI sageApiAiring;
    private AlbumAPI sageApiAlbum;
    private ChannelAPI sageApiChannel;
    private Database sageDatabase;
    private FavoriteAPI sageApiFavorite;
    private Global sageApiGlobal;
    private MediaPlayerAPI sageApiMediaPlayer;
    private MediaFileAPI sageApiMediaFile;
    private PlaylistAPI sageApiPlaylist;
    private SageConfiguration sageConfiguration;
    private ShowAPI sageApiShow;
    private SystemMessageAPI systemMessageApi;
    private WidgetAPI sageApiWidget;
    private Utility sageUtility;
    private int serverPort;
    private IntervalTimer lowRateInterval = new IntervalTimer(LOW_RATE_CYCLES, true);
    private IntervalTimer veryLowRateInterval = new IntervalTimer(VERY_LOW_RATE_CYCLES, true);
    private ServerSocket server = null;
    private Socket client = null;    
    PrintWriter outputBuffer = null;
    BufferedReader inputBuffer = null;
    SageLogger logger = null;
    private ArrayList<Message> incomingMessages = null;
    private ArrayList<Message> outgoingMessages = null;
    private PlaylistAPI.Playlist playlist = null;

    enum States {Play, Pause, Stop, None};
    
    private States currentState = States.None;
    private MediaStore currentMediaFile = null;
    private int currentMediaFileID = -1;
    
    private final String FULL_SCREEN_MODE = "0";
    private final String WINDOWED_MODE = "1";
    
    private boolean isInitialized = false;
    private String currentScreenMode = "";
    private String context = "";
    private boolean currentMuteState = false;
    private float currentVolume = 0f;
    private Long currentDuration = 0l;
    private Long currentMediaTime = 0l;
    private Long lastAlbum = 0l;
    private Long lastPhoto = 0l;
    private Long lastSM = 0l;
    private Long lastVideo = 0l;

    // Maps for holding user-designated filters
    HashMap<String, ArrayList<String>> trueFilters = new HashMap<String, ArrayList<String>>();
    HashMap<String, ArrayList<String>> falseFilters = new HashMap<String, ArrayList<String>>();
    
    /** Creates a new instance of SageServer */
    public SageServer(String uiContext, int portNum) {
        sageApi = uiContext.equals("local") ? API.apiLocalUI : new API(uiContext);
        sageApiAiring = new AiringAPI(sageApi);
        sageApiAlbum = new AlbumAPI(sageApi);
        sageApiChannel = new ChannelAPI(sageApi);
        sageApiFavorite = new FavoriteAPI(sageApi);
        sageApiGlobal = sageApi.global;   
        sageApiMediaFile = new MediaFileAPI(sageApi);
        sageApiMediaPlayer = new MediaPlayerAPI(sageApi);
        sageApiPlaylist = new PlaylistAPI(sageApi);
        sageApiShow = new ShowAPI(sageApi);
        sageDatabase = new Database(sageApi);
        sageApiWidget = new WidgetAPI(sageApi);
        sageUtility = new Utility(sageApi);
        systemMessageApi = new SystemMessageAPI(sageApi);
        serverPort = portNum;
        incomingMessages = new ArrayList<Message>();
        outgoingMessages = new ArrayList<Message>();
        logger = new SageLogger("SageServer(" + uiContext + ":" + portNum + ")");
        sageConfiguration = new SageConfiguration();
        if (sageApiPlaylist.GetPlaylists().size() > 0)
            playlist = sageApiPlaylist.GetPlaylists().get(0);
        else playlist = sageApiPlaylist.GetNowPlayingList();
        context = uiContext;
    }

    /** Start listening/transmitting on the new Sage TCP Server. */
    public void run() {
        
        startServer();
        
        while (true)
        {
            // Reinitialize some data so it will be re-sent upon connect.
            boolean clientConnected = waitForClient ();
            outgoingMessages.add(new Message(MessageType.VERSION, StartServers.VERSION));

            // Restart the interval timers, so that some messages will be sent out
            // immediately upon connect.           
            while (clientConnected)
            {
                clientConnected = getIncomingMessages();
                
                if (clientConnected)
                {
                    processIncomingMessages();
                    if (isInitialized) getOutgoingData();
                    sendOutgoingMessages ();
                }
            }   
            
            // Client no longer connected.  Reinitialize some members
            // so data will be sent upon new connection
            lowRateInterval = new IntervalTimer(LOW_RATE_CYCLES, true);
            veryLowRateInterval = new IntervalTimer(VERY_LOW_RATE_CYCLES, true);
            isInitialized = false;
            reset();
            try
            {
                client.close();
            }
            catch (Exception ex)
            {
                logger.Debug(ex.getMessage());
            }
        }
    }
    
    /** Reinitialize some members */
    public void reset() {
        currentScreenMode = "";
        currentDuration = 0l;
        currentMediaFileID = -1;
        currentMediaTime = 0l;
        currentMuteState = false;
        currentState = States.None;
        videoDiskSpaceString = "";
        currentVolume = 0f;
        trueFilters = new HashMap<String, ArrayList<String>>();
        falseFilters = new HashMap<String, ArrayList<String>>();
   }

    /** Reinitialize some members */
    public void resetLists() {            
        allFavorites = new ArrayList<Integer>();
        allManual = new ArrayList<Integer>();
        allRecordedShows = new ArrayList<Integer>();
        allUpcomingRecordings = new ArrayList<Integer>();
        lastAlbum = 0l; // Or/and in Reset?
        lastPhoto = 0l;
        lastSM = 0l;
        lastVideo = 0l;
        offsetOther = 0;
        offsetPicture = 0;
        numberOfRecordings = -1;
    }

    /** Start a new Sage TCP Server. */
    public void startServer() {
        logger.Message("Starting server on socket " + serverPort);
        server = null;
        try
        {
            server = new ServerSocket(serverPort);
            server.setReuseAddress(true);
        }
        catch (IOException e)
        {
            logger.Error(e);
            // System.exit(1);
        }
    }

    /** Wait for a new client to connect. */
    public boolean waitForClient() {
        boolean connected = false;
        logger.Message("Now listening for client connection requests.");
        try
        {
            client = server.accept();
            outputBuffer = new PrintWriter(new OutputStreamWriter(client.getOutputStream(), "UTF-8"), true);                
            inputBuffer = new BufferedReader(new InputStreamReader(client.getInputStream(), "UTF-8"));
            client.setSoTimeout (SERVER_UPDATE_RATE_MS);

            logger.Message("Connected to client, waiting for messages...");
            connected = true;    
        }
        catch (IOException e)
        {
           logger.Error(e);
        }
            
        return connected;
    }

    /** Procees the incoming messages from the queue. */
    public void processIncomingMessages() {
        if (incomingMessages.size() > 0)
        {
            Iterator<Message> iter = incomingMessages.iterator();
            String returnValue = "";
            
            while (iter.hasNext())
            {
                if (incomingMessages.size() < 2)
                    logger.Debug("(Processing messages) There is now " + incomingMessages.size() + " incoming message.");
                else logger.Debug("(Processing messages) There are now " + incomingMessages.size() + " incoming messages.");
                
                // TEMPORARY CODE
                String names[] = sageApiGlobal.GetUIContextNames();
                StringBuilder contextNameStr = new StringBuilder("The following UI context are available: ");
                for (int index = 0; index < names.length; index ++) 
                    contextNameStr.append(names[index]).append(", ");
                logger.Debug(contextNameStr.toString());
                ////////////////////
                
                try
                {
                    Message msg = iter.next();
                    Integer showId;
                    String dataStr;
                    logger.Debug("(Processing messages) " + msg.toString());
                    switch (msg.getType())
                    {
                        case PLAY_MODE:
                            setPlayMode(msg.getData());
                            break;
                        case VOLUME:
                            setVolume(msg.getData());
                            break;
                        case MUTE:
                            setMute(msg.getData());
                            break;
                        case CURRENT_CHANNEL:
                            sageApiMediaPlayer.ChannelSet(msg.getData());
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
                                MediaFile mf = sageApiMediaFile.GetMediaFileForFilePath(new File(file));
                                Object mediaFile = sageApiMediaFile.Unwrap(mf);
                                Object successful = sageApiMediaPlayer.Watch(mediaFile);
                                sageApiWidget.FindWidget("Menu", "MediaPlayer OSD").LaunchMenuWidget();
                                logger.Debug("Watch show path result: " + successful.toString());
                            }
                            catch (Exception e) {
                                logger.Error(e); 
                            }
                            break;
                        case WATCH_SELECTED_SHOW:
                            showId = null;
                            try
                            {
                                showId = Integer.valueOf(msg.getData());
                                MediaFileAPI.MediaFile mf = sageApiMediaFile.GetMediaFileForID(showId.intValue());
                                if (mf == null) {
                                    logger.Debug("No show found with ID of " + showId.toString());
                                }
                                else {
                                    logger.Debug("Watching show: " + mf.GetMediaTitle());
                                    Object mediaFile = sageApiMediaFile.Unwrap(mf);
                                    //sageApiGlobal.SageCommand("Home");
                                    //Thread.sleep(500);
                                    Object didItWork = sageApiMediaPlayer.Watch(mediaFile);
                                    //Thread.sleep(100);
                                    //sageApiGlobal.SageCommand("TV");
                                    sageApiWidget.FindWidget("Menu", "MediaPlayer OSD").LaunchMenuWidget();
                                }
                            }
                            catch (Throwable t)
                            {
                                logger.Error(t);   
                                logger.Message("Show ID: " + showId.toString());
                            }                            
                            break;
                        case WATCH_SELECTED_IMAGE: // Non functionnal
                            showId = null;
                            try
                            {
                                showId = Integer.valueOf(msg.getData());
                                MediaFileAPI.MediaFile mf = sageApiMediaFile.GetMediaFileForID(showId.intValue());
                                if (mf == null) {
                                    logger.Debug("No image found with ID of " + showId.toString());
                                }
                                else {
                                    logger.Debug("Watching image: " + mf.GetMediaTitle());
                                    Object mediaFile = sageApiMediaFile.Unwrap(mf);
//                                    sageApiGlobal.SageCommand("Home");
//                                    Thread.sleep(100);
//                                    sageApiGlobal.SageCommand("Picture Library");
//                                    Thread.sleep(5000);
                                    Object image = sageUtility.LoadImage(mf.GetFullImage());
                                    //WidgetAPI.
                                    //Object didItWork = sageApiMediaPlayer.Watch(mediaFile);
                                    WidgetAPI.Widget pictureWidget = sageApiWidget.FindWidget("Menu", "Picture Slideshow");
                                    //pictureWidget.AddWidgetChild((WidgetAPI.Widget) image);
                                    pictureWidget.LaunchMenuWidget();
                                    logger.Debug(image.toString());
                                    //Thread.sleep(5000);
                                    //Object didItWork2 = mf.Watch();
                                    //sageApiWidget.FindWidget("Menu", "Picture Slideshow").LaunchMenuWidget();
                                    //logger.Debug(didItWork2.toString());
                                }
                            }
                            catch (Throwable t)
                            {
                                logger.Error(t);   
                                logger.Message("Show ID: " + showId.toString());
                            }                            
                            break;
                        case WINDOWING_MODE:
                            if (msg.getData().equals("0")) sageApiGlobal.SetFullScreen(true); 
                            else sageApiGlobal.SetFullScreen(false); 
                            break;
//                        case RECORDED_SHOW_INDEX:
//                            showId = Integer.valueOf(msg.getData());
//                            dataStr = getSelectedShowInfo(showId, "REC");
//                            outgoingMessages.add(new Message(MessageType.RECORDED_SHOW_INDEX, dataStr));
//                            break;
//                        case SELECTED_SHOW_INDEX:
//                            showId = Integer.valueOf(msg.getData());
//                            dataStr = getSelectedShowInfo(showId, "SSI");
//                            outgoingMessages.add(new Message(MessageType.SELECTED_SHOW_INDEX, dataStr));
//                            break;
//                        case UPCOMING_RECORDING_SHOW_INDEX:
//                            showId = Integer.valueOf(msg.getData());
//                            dataStr = getSelectedShowInfo(showId, "UPR");
//                            outgoingMessages.add(new Message(MessageType.UPCOMING_RECORDING_SHOW_INDEX, dataStr));
//                            break;
                        case DELETE_SHOW:
                            showId = Integer.valueOf(msg.getData());
                            boolean successful = deleteShow(showId);
                            if (successful) {
                                outgoingMessages.add(new Message(MessageType.DELETE_SHOW, "OK"));    
                                
                                // A show was deleted, let's force another sending of the recorded
                                // shows list since it has now clearly changed.
                            
                            }
                            else outgoingMessages.add(new Message(MessageType.DELETE_SHOW, "BAD"));                                    
                            break;
//                        case FAVORITE_SHOW_INDEX:
//                            showId = Integer.valueOf(msg.getData());
//                            dataStr = getSelectedShowInfo(showId, "FAV");
//                            outgoingMessages.add(new Message(MessageType.FAVORITE_SHOW_INDEX, dataStr));
//                            break;
                        case COMMAND:
                            if (msg.getData().equals("QUT")) {
                                logger.Debug("Exit message received, closing interface");
                                sageApiGlobal.Exit();
                            }
                            else if (msg.getData().equals("CHU")) {
                                sageApiMediaPlayer.ChannelUp();
                                logger.Debug("ChannelUp.");
                            }
                            else if (msg.getData().equals("CHD")) {
                                logger.Debug("ChannelDown.");
                                sageApiMediaPlayer.ChannelDown();
                            }
                            else if (msg.getData().equals("VOLU")) {
                                sageApiMediaPlayer.VolumeUp();
                                logger.Debug("VolumeUp.");
                            }
                            else if (msg.getData().equals("VOLD")) {
                                logger.Debug("VolumeDown.");
                                sageApiMediaPlayer.VolumeDown();
                            }
                            else {
                                logger.Debug("Assumed Sage command: " + msg.getData());
                                sageApiGlobal.SageCommand(msg.getData());
                            }
                            break;
                        case EXTENDER_TELNET_COMMAND:
                            Object result = mvpCommand(context, msg.getData());
                            logger.Debug(result.toString());
                            break;
                        case FAVORITE_SHOW_LIST:
                            dataStr = getFavoriteList();
                            if (!dataStr.isEmpty()) 
                                outgoingMessages.add(new Message(MessageType.FAVORITE_SHOW_LIST, dataStr));
                            break;
                        case MANUAL_RECORDINGS_LIST:
                            dataStr = getManualRecordingsList();
                            if (!dataStr.isEmpty()) 
                                outgoingMessages.add(new Message(MessageType.MANUAL_RECORDINGS_LIST, dataStr));
                            break;
                        case ALL_CHANNELS:
                            dataStr = msg.getData();
                            if (dataStr.isEmpty() || dataStr.equalsIgnoreCase("All")) getAllChannels();
                            else getChannelsOnLineup(dataStr);
                            break;
                        case AIRINGS_ON_CHANNEL_AT_TIME:
                            dataStr = getAiringsOnChannelAtTime(msg.getData());
                            outgoingMessages.add(new Message(MessageType.AIRINGS_ON_CHANNEL_AT_TIME, dataStr));
                            break;
                        case ANSWER:
                            outgoingMessages.add(new Message(MessageType.ANSWER, setAnswer(msg.getData())));
                            break;
                        case MAXIMUM_ITEMS:
                            try
                            {
                                maxItems = Integer.valueOf(msg.getData());
                                outgoingMessages.add(new Message(MessageType.MAXIMUM_ITEMS, "OK"));
                            }
                            catch (Exception e)
                            {
                                logger.Debug(e.getMessage());
                                outgoingMessages.add(new Message(MessageType.MAXIMUM_ITEMS, "BAD"));
                            }
                            break;
                        case MODE:
                            serverMode = msg.getData();
                            if (serverMode.equalsIgnoreCase("Media") || serverMode.equalsIgnoreCase("Player")) {
                                outgoingMessages.add(new Message(MessageType.MODE, "OK"));
                            }
                            else {
                                outgoingMessages.add(new Message(MessageType.MODE, "BAD"));                              
                            }
                                    
                            break;
                        case ALL_THE_LINEUPS:
                            dataStr = getLineups();
                            outgoingMessages.add(new Message(MessageType.ALL_THE_LINEUPS, dataStr));
                            break;
                        case RESET:
                            reset();
                            getNumberOfCurrentlyRecordingFiles();
                            
                            if (serverMode.equalsIgnoreCase("Media"))
                            {
                                resetLists();
                                getUpcomingRecordingsList();

                                // TESTING
                                getTVFileList();
                                //getMediaFileList("IsLibraryFile", MessageType.RECORDED_SHOW_LIST);

                                returnValue = getManualRecordingsList();
                                if (!returnValue.isEmpty()) 
                                outgoingMessages.add(new Message(MessageType.MANUAL_RECORDINGS_LIST, returnValue));

                                returnValue = getFavoriteList();
                                if (!returnValue.isEmpty()) 
                                    outgoingMessages.add(new Message(MessageType.FAVORITE_SHOW_LIST, returnValue));
                            }
                            break;
                        case RESET_SYSTEM_MESSAGE:
                            systemMessageApi.DeleteAllSystemMessages();
                            this.lastSM = 0l;
                            break;
                        case INITIALIZE:
                            isInitialized = true;
                            if (serverMode.equalsIgnoreCase("Media"))
                            {
                                sendAvailableData();
                                resetLists();
                                outgoingMessages.add(new Message(MessageType.PROTOCOL_STREAMING_TYPE, StreamingType));
                                outgoingMessages.add(new Message(MessageType.STREAMING_PORT,
                                        String.valueOf(StreamingPort)));
                            }
                            else{
                                // if already playing, send the status
                            }
                            outgoingMessages.add(new Message(MessageType.INITIALIZE, "OK"));
                            break;
                        case UPCOMING_EPISODES_LIST:
                            dataStr = getUpcomingEpisodesList(msg.getData());
                            if (!dataStr.isEmpty()) 
                                outgoingMessages.add(new Message(MessageType.UPCOMING_EPISODES_LIST, dataStr));
                            break;
                        case UPCOMING_RECORDINGS_LIST:
                            getUpcomingRecordingsList();
                            break;
                        case MUSIC_FILES_LIST:
                            //dataStr = msg.getData();
                            //if (!dataStr.isEmpty()) this.lastAlbum = Long.valueOf(dataStr);
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
                        case RECORDED_SHOW_LIST:
                            getTVFileList();
                            break;
                        case RECORD_A_SHOW:
                            recordAShow(msg.getData());
                            break;
                        case VIDEO_DISK_SPACE:
                            if (serverMode.equalsIgnoreCase("Media"))
                            {
                                videoDiskSpaceString = getVideoDiskSpace();
                                if (!videoDiskSpaceString.isEmpty()) {
                                    outgoingMessages.add(new Message(MessageType.VIDEO_DISK_SPACE, videoDiskSpaceString));
                                }
                            }
                            break;
                        case ADD_TO_PLAYLIST:
                            addToPlaylist(msg.getData(), false);
                            break;
                        case ADD_ALBUM_TO_PLAYLIST:
                            addToPlaylist(msg.getData(), true);
                            break;
                        case CHANGE_THE_PLAYLIST:
                            changePlaylist(msg.getData());
                            break;
                        case CLEAR_THE_PLAYLIST:
                            clearPlaylist();
                            break;
                        case DELETE_THE_PLAYLIST:
                            DeletePlaylist(msg.getData());
                            break;
                        case DELETE_PLAYLIST_ITEM:
                            DeletePlaylistItem(msg.getData());
                            break;
                        case GET_THE_PLAYLIST:
                            dataStr = getThePlaylist(msg.getData());
                            outgoingMessages.add(new Message(MessageType.GET_THE_PLAYLIST, dataStr));
                            break;
                        case LIST_THE_PLAYLISTS:
                            dataStr = listThePlaylists();
                            outgoingMessages.add(new Message(MessageType.LIST_THE_PLAYLISTS, dataStr));
                            break;
                        case START_PLAYLIST:
                            sageApiGlobal.SageCommand("Home");
                            Thread.sleep(500);
                            sageApiMediaPlayer.StartPlaylist(playlist);
                            Thread.sleep(100);
                            sageApiGlobal.SageCommand("TV");
                            break;
                        case SEARCH_BY_TITLE:
                            dataStr = searchByTitle(msg.getData());
                            if (!dataStr.isEmpty()) 
                                outgoingMessages.add(new Message(MessageType.SEARCH_BY_TITLE, dataStr));
                            break;
                        case MATCH_EXACT_TITLE:
                            dataStr = searchByExactTitle(msg.getData());
                            if (!dataStr.isEmpty()) 
                                outgoingMessages.add(new Message(MessageType.MATCH_EXACT_TITLE, dataStr));
                            break;
                        case LAST_EPG_DOWNLOAD:
                            outgoingMessages.add(new Message(MessageType.LAST_EPG_DOWNLOAD, 
                                    String.valueOf(sageApiGlobal.GetLastEPGDownloadTime()/100000)));
                            break;
                        case NEXT_EPG_DOWNLOAD:
                            outgoingMessages.add(new Message(MessageType.NEXT_EPG_DOWNLOAD, 
                                    String.valueOf(sageApiGlobal.GetTimeUntilNextEPGDownload()/1000)));
                            break;
//                        case FILTERS:
//                            setFilters(msg.getData());
//                            getTVFileList();
//                            break;
                        case STREAM_VLC_ALBUM:
                            showId = Integer.valueOf(msg.getData());
                            setVLCAlbum(showId);
                            break;
                        case STREAM_VLC:
                            showId = Integer.valueOf(msg.getData());
                            setVLC(showId);
                            break;
                        case KEY:
                            keystroke(msg.getData());
                            break;
                        case SYSTEM_MESSAGE:
                            dataStr = msg.getData();
                            if (!dataStr.isEmpty()) this.lastSM = Long.valueOf(dataStr);
                            break;

                        default:
                            logger.Debug("Default case reached.  Didn't know what to do with: " + msg.getData());
                            break;
                    }
                }
                catch (Throwable t)
                {
                    logger.Error(t);
                }
                
                iter.remove();
            }
        }
    }

    /** Send the outgoing messages from the queue. */
    public void sendOutgoingMessages() {
        Iterator<Message> iter = outgoingMessages.iterator();
        
        if (outgoingMessages.size() == 1) logger.Debug("There is 1 message to be sent.");
        else if (outgoingMessages.size() > 1)
            logger.Debug("There are " + outgoingMessages.size() + " messages to be sent.");
        
        while (iter.hasNext())
        {
            Message msg = iter.next();
            send(msg.toString());
            iter.remove();
        }
        
        outputBuffer.flush();
    }

    /** Send a messages. */
    public void send(String msgToSend) {
        try
        {
            outputBuffer.print(STX + msgToSend + ETX);
            logger.Debug("Sending data: " + msgToSend);
        }
        catch (Throwable t)
        {
            logger.Error(t);          
        }
    }    
    
    /** Get and process the incoming messages. */
    public boolean getIncomingMessages() {
        boolean stillConnected = true;       
        String inputLine = null;
        boolean messageReceived = true;

        //while (messageReceived && stillConnected)        
        while (messageReceived && stillConnected && (incomingMessages.size() < 1))
        {
            try
            {
                inputLine = inputBuffer.readLine();
                if (inputLine == null)
                {
                    stillConnected = false;
                    messageReceived = false;
                }
                else messageReceived = true;
            }
            catch (SocketTimeoutException e)
            {
                messageReceived = false;
            }
            catch (IOException e)
            {
                messageReceived = false;
                stillConnected = false;
            }
            
            if (messageReceived)
            {
                try
                {
                    Message msg = new Message();
                    msg.fromString(inputLine);
                    incomingMessages.add(msg);
                    logger.Debug("Message received: " + inputLine);
                    logger.Debug("Now " + incomingMessages.size() + " incoming message(s) in list.");
                }
                catch (Throwable t)
                {
                    logger.Error(t);
                }
            }
        }
        
        return stillConnected;
    }

    /** Set the appropriate play mode for the current media. */
    public void setPlayMode(String playModeStr) throws Throwable {
        if (playModeStr.equals ("Play"))      sageApiMediaPlayer.Play ();
        else if (playModeStr.equals("Pause")) sageApiMediaPlayer.Pause ();
        else if (playModeStr.equals("Stop"))  sageApiGlobal.SageCommand("Stop");
        else if (playModeStr.equals("Smooth RW"))  sageApiGlobal.SageCommand("Smooth RW");
        else if (playModeStr.equals("FW1"))   sageApiMediaPlayer.SkipForward();
        else if (playModeStr.equals("FW2"))   sageApiMediaPlayer.SkipForward2();
        else if (playModeStr.equals("BK1"))   sageApiMediaPlayer.SkipBackwards();
        else if (playModeStr.equals("BK2"))   sageApiMediaPlayer.SkipBackwards2();
        else if (playModeStr.equals("PL1"))   sageApiMediaPlayer.PlayFaster();
        else if (playModeStr.equals("PL2"))   sageApiMediaPlayer.PlaySlower();
        else if (playModeStr.startsWith("Seek"))
        {
            try
            {
                long seekPercent = Long.valueOf(playModeStr.substring(5));
                long seekTime = (currentMediaFile.getEndTime() - currentMediaFile.getStartTime()) * seekPercent * 10
                        + currentMediaFile.getStartTime() * 1000;
                sageApiMediaPlayer.Seek(seekTime);
            }
            catch (Throwable t)
            {
                logger.Error(t);
            }
        }
        else throw new Throwable("Playmode not matched");
    }

    /** Get all the channels, on all the lineups. */
    public void getAllChannels() {
        for (String lineup : sageApiGlobal.GetAllLineups())
            getChannelsOnLineup(lineup);
    }
    
    /** Get all the channels, on a single lineup. */
    public void getChannelsOnLineup(String lineup) {
        Integer counter = 1;
        logger.Debug("Finding all the channels for lineup: " + lineup + ".");
        ChannelAPI.List allChannels = sageDatabase.GetChannelsOnLineup(lineup);
        allChannels = allChannels.Sort(false, "GetChannelNumber");
        Integer numberOfChannels = allChannels.size();
        logger.Debug(numberOfChannels.toString() + " channels found for lineup: " + lineup + ".");
        StringBuffer msgToSend = new StringBuffer();
        String channels = "", oneChannel = "";

        for (ChannelAPI.Channel channel : allChannels)
        {
            oneChannel = (isXML ? XMLUtil.createXMLChannel(channel, lineup)
                    : JSONUtil.createJSONChannel(channel, lineup));
            
            if (!oneChannel.isEmpty()) msgToSend.append(oneChannel);

            if (counter >= numberOfChannels) // Should be maxItems...
            {
                 if (isXML) channels = "<?xml version=\"1.0\" encoding=\"utf-8\"?><Collection><Lineup name=\"" +
                    XMLUtil.formatForXML(lineup) + "\">" + msgToSend.toString() + "</Lineup></Collection>";
                 else {
                     msgToSend.delete(msgToSend.length() - 2, msgToSend.length());

                     channels = "{\"Name\": \"" + JSONUtil.formatForJSON(lineup)
                         + "\", \"Lineup\": [" + msgToSend.toString() + "]}";
                 }

                outgoingMessages.add(new Message(MessageType.ALL_CHANNELS, channels));
                msgToSend = new StringBuffer();
            }
            
            counter++;
        }
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
        AiringAPI.Airing air = sageApiAiring.GetAiringForID(showID);
        String returnValue = "";
        
        if (type.equalsIgnoreCase("manual"))
        {
            logger.Debug("Manual case...");
            air.SetRecordingTimes(air.GetScheduleStartTime(), air.GetScheduleEndTime());
            Wait(250);
            returnValue = getManualRecordingsList();
            if (!returnValue.isEmpty())
                outgoingMessages.add(new Message(MessageType.MANUAL_RECORDINGS_LIST, returnValue));
        }
        else
        {
            logger.Debug("New favorite...");
            boolean isReruns = !type.equalsIgnoreCase("firstrun");
            boolean isFirstRuns = !type.equalsIgnoreCase("rerun");
            logger.Debug(air.GetAiringTitle());
            sageApiFavorite.AddFavorite(air.GetAiringTitle(), isFirstRuns,
                    isReruns, "", "", "", "", "", "", "", "", "", "", "");
            Wait(250);
            returnValue = getFavoriteList();
            if (!returnValue.isEmpty())
                outgoingMessages.add(new Message(MessageType.FAVORITE_SHOW_LIST, returnValue));
        }

        getUpcomingRecordingsList();
     }

    private void Wait(int delay){
        try{
            Thread.sleep(delay);
        }
        catch (Throwable t)
        {
            logger.Error(t);
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
        
        allAirings = sageDatabase.SearchByTitle(title, "T");
        logger.Debug(String.valueOf(allAirings.size()) + " unfiltered airings found for: " + title);

        for (AiringAPI.Airing air : allAirings)
        {
            if (air.GetScheduleStartTime() > startTime) msgToSend.append(XMLUtil.createXMLString
                    (air, air.GetAiringID(), "Program", sageApiShow, false));
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
        String[] allTitles = sageDatabase.SearchForTitles(title, "T");
        Date now = new Date();
        AiringAPI.List allAirings;
        int count = 0;
        long startTime = now.getTime();
        StringBuilder msgToSend = new StringBuilder();

        if (isXML) msgToSend.append("<?xml version=\"1.0\" encoding=\"utf-8\"?><Collection>")
                .append("<Title name=\"").append(title).append("\">");
        else msgToSend.append("{\"Searches\": [");

        for (String oneTitle : allTitles)
        {
            allAirings = sageDatabase.SearchByTitle(oneTitle, "T");
            logger.Debug(String.valueOf(allAirings.size()) + " unfiltered airings found for: " + oneTitle);

            for (AiringAPI.Airing air : allAirings)
            {
                if (air.GetScheduleStartTime() > startTime){
                    if (isXML) msgToSend.append(XMLUtil.createXMLString
                        (air, air.GetAiringID(), "Program", sageApiShow, false));
                    else{
                        msgToSend.append(JSONUtil.createJSONString(air,
                        air.GetAiringID(), "Program", sageApiShow, "", false, "IsTVFile", ""));
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

    private String getAiringsOnChannelAtTime(String msg) {
        String SEPARATOR_CHAR = "|";
        long endTime = -1;
        Integer highIndex = msg.indexOf(SEPARATOR_CHAR);
        Integer stationID = Integer.valueOf(msg.substring(0, highIndex));
        Integer lowIndex = highIndex + 1;
        highIndex = msg.indexOf(SEPARATOR_CHAR, lowIndex);
        long startTime = Long.valueOf(msg.substring(lowIndex, highIndex));
        lowIndex = highIndex + 1;
        highIndex = msg.indexOf(SEPARATOR_CHAR, lowIndex);
        
        if (highIndex == -1)
        {
            logger.Debug("Using days...");
            Date now = new Date();
            endTime = now.getTime() + startTime * 24 * 3600000;
            startTime = now.getTime();
        }
        else endTime = Long.valueOf(msg.substring(lowIndex, highIndex));
        
        return getAiringsOnChannelAtTime(stationID, startTime, endTime, false);
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
    public String getAiringsOnChannelAtTime(Integer stationID, long startTime, 
            long endTime, boolean mustStartDuringTime) {
        ChannelAPI.Channel channel = sageApiChannel.GetChannelForStationID(stationID);
        logger.Debug("Finding all the airings on channel:" + channel.GetChannelNumber());
        AiringAPI.List allAirings = sageDatabase.GetAiringsOnChannelAtTime(channel, startTime, endTime, mustStartDuringTime);
        logger.Debug(String.valueOf(allAirings.size()) + " airings found.");
        StringBuilder msgToSend = new StringBuilder();

        if (isXML) msgToSend.append("<?xml version=\"1.0\" encoding=\"utf-8\"?><Collection><Station ID=\"")
                .append(stationID).append("\">");
        else msgToSend.append("{\"Programs\": [");
        //else msgToSend.append("{\"ChannelID\": ").append(stationID).append(", \"Airings\": [");

        for (AiringAPI.Airing air : allAirings)
        {
            if (isXML) msgToSend.append(XMLUtil.createXMLString(air,
                    air.GetAiringID(), "Program", sageApiShow, true));
            else msgToSend.append(JSONUtil.createJSONString(air,
                    air.GetAiringID(), "Program", sageApiShow, "", true, "IsTVFile", ""));
        }

        if (isXML) msgToSend.append("</Station></Collection>");
        else{
            msgToSend.delete(msgToSend.length() - 2, msgToSend.length());
            msgToSend.append("]}");
        }
        return msgToSend.toString();   
    }

   /**
    * Returns the list of Manual recording jobs, the format of the 
    * output is dependant on the answer type (TXT|XML}.  Nothing is sent if
    * there was no changes since the last transmission.
    * 
    * @return msgToSend String - The list of Manual recordings.
    */
    public String getManualRecordingsList() {
        ArrayList<Integer> toAddList = new ArrayList<Integer>();
        ArrayList<Integer> toDeleteList = new ArrayList<Integer>();
        Integer manualID = -1;
        Date now = new Date();
        long startTime = now.getTime() - 3600000;
        long endTime = now.getTime() + 14 * 24 * 3600000;

        logger.Debug("Finding all the manual recordings...");
        AiringAPI.List allAirings = sageDatabase.GetAiringsOnViewableChannelsAtTime(startTime, endTime, false);
        allAirings = allAirings.FilterByBoolMethod("IsManualRecord", true);
        logger.Debug(String.valueOf(allAirings.size()) + " manual recording(s) found.");

        if (allAirings.size() == 0 && allManual.isEmpty()) return ""; // Nothing to do

        for (Integer recID : allManual) toDeleteList.add(recID);
        StringBuilder msgToSend = new StringBuilder();
        
        if (isTXT) msgToSend.append(allAirings.size()).append(DATA_SEPARATOR);
        else if (isXML) msgToSend.append(
                "<?xml version=\"1.0\" encoding=\"utf-8\"?><Collection><List Name=\"Manual\">");
        else msgToSend.append("{\"Favorites\": [");

        for (AiringAPI.Airing air : allAirings)
        {
            manualID = air.GetAiringID();
            
            if (!isTXT)
            {
                if (!allManual.contains(manualID))
                {
                    toAddList.add(manualID);

                    if (isXML) msgToSend.append(XMLUtil.createXMLString(air, manualID, "Program",
                            sageApiShow, "Add", "", true, "IsManual", ""));
                    else msgToSend.append(JSONUtil.createJSONString(air, manualID, "Program",
                            sageApiShow, "Add", true, "IsManual", ""));
                }
                else toDeleteList.remove(toDeleteList.indexOf(manualID));
            }
            else
            {
                toAddList.add(manualID);
                msgToSend.append(air.GetAiringTitle()).append(DATA_SEPARATOR);
                msgToSend.append(manualID.toString()).append(DATA_SEPARATOR);
            }
        }

        if (!isTXT)
        {
            for (Integer toAdd : toAddList) allManual.add(toAdd);
            for (Integer toDelete : toDeleteList)
            {
                allManual.remove(toDelete);
                if (isXML) msgToSend.append(XMLUtil.createXMLString(toDelete, "Program", "Del"));
                else msgToSend.append(JSONUtil.createJSONString(toDelete, "Del"));
            }
            
            if (toAddList.size() + toDeleteList.size() == 0) return "";
            else if (isXML) msgToSend.append("</List></Collection>");
            else if (toAddList.isEmpty() && toDeleteList.isEmpty()) msgToSend.append("]}");
            else{
                msgToSend.delete(msgToSend.length() - 2, msgToSend.length());
                msgToSend.append("]}");
            }
        }
        else if (toAddList.equals(allManual)) return "";
        else allManual = toAddList;
        
        return msgToSend.toString();
    }

   /**
    * Returns the list of Favorite shows (Recording jobs), the format of the 
    * output is dependant on the answer type (TXT|XML}.  Nothing is sent if
    * there was no changes since the last transmission.
    * 
    * @return msgToSend String - The list of Favorite shows.
    */
    public String getFavoriteList() {
        ArrayList<Integer> toAddList = new ArrayList<Integer>();
        ArrayList<Integer> toDeleteList = new ArrayList<Integer>();
        Integer favID = -1;

        logger.Debug("Finding all the favorites...");
        FavoriteAPI.List favorites = sageApiFavorite.GetFavorites();
        logger.Debug(String.valueOf(favorites.size()) + " favorites found.");

        if (favorites.size() == 0 && allFavorites.isEmpty()) return ""; // Nothing to do

        for (Integer recID : allFavorites) toDeleteList.add(recID);
        StringBuilder msgToSend = new StringBuilder();

        if (isTXT) msgToSend.append(favorites.size()).append(DATA_SEPARATOR);
        else if (isXML) msgToSend.append(
                "<?xml version=\"1.0\" encoding=\"utf-8\"?><Collection><List Name=\"Favorites\">");
        else msgToSend.append("{\"Favorites\": [");

        for (FavoriteAPI.Favorite favorite : favorites)
        {
            favID = favorite.GetFavoriteID();
            
            if (!isTXT)
            {
                if (!allFavorites.contains(favID))
                {
                    toAddList.add(favID);
                    if (isXML) msgToSend.append(XMLUtil.createXMLString(favorite, favID, "Program", "Add"));
                    else msgToSend.append(JSONUtil.createJSONString(favorite, favID, "Program", "Add"));
                }
                else toDeleteList.remove(toDeleteList.indexOf(favID));
            }
            else
            {
                toAddList.add(favID);
                msgToSend.append(favorite.GetFavoriteTitle()).append(DATA_SEPARATOR);
                msgToSend.append(favID.toString()).append(DATA_SEPARATOR);
            }
        }

        if (!isTXT)
        {
            for (Integer toAdd : toAddList) allFavorites.add(toAdd);
            for (Integer toDelete : toDeleteList)
            {
                allFavorites.remove(toDelete);
                if (isXML) msgToSend.append(XMLUtil.createXMLString(toDelete, "Program", "Del"));
                else msgToSend.append(JSONUtil.createJSONString(toDelete, "Del"));
            }
            
            if (toAddList.size() + toDeleteList.size() == 0) return "";
            else if (isXML) msgToSend.append("</List></Collection>");
            else if (toAddList.isEmpty() && toDeleteList.isEmpty()) msgToSend.append("]}");
            else{
                msgToSend.delete(msgToSend.length() - 2, msgToSend.length());
                msgToSend.append("]}");
            }
        }
        else if (toAddList.equals(allFavorites)) return "";
        else allFavorites = toAddList;
        
        return msgToSend.toString();
    }

    public String getSystemMessages(){
        SystemMessageAPI.List messages = systemMessageApi.GetSystemMessages();
        if (messages == null || messages.size() < 1) return "";
        int size = messages.size();

        logger.Debug("Gathering "  + size + " system message(s).");

        StringBuilder msgToSend = new StringBuilder();
        SystemMessageAPI.SystemMessage message;
        int counter = 0;
        int rescale = 100000;
        if (isXML) msgToSend.append("<?xml version=\"1.0\" encoding=\"utf-8\"?><Collection><List Name=\"SystemMessages\">");
        else msgToSend.append("{\"SystemMessage\": [");

        for (int i = messages.size() - 1; i >= 0; i--){
            message = messages.get(i);
            if (message.GetSystemMessageTime()/rescale > this.lastSM){
                counter++;

                if (isXML) msgToSend.append(XMLUtil.createXMLString(message, "Message"));
                else msgToSend.append(JSONUtil.createJSONString(message));
            }
            else break;
        }

        if (counter == 0) return "";
        else logger.Debug("Found "  + counter + " system message(s).");
        this.lastSM = (Long) messages.get(size - 1).GetSystemMessageTime()/rescale;
        logger.Debug("Last SM time = " + this.lastSM);

        if (isXML) msgToSend.append("</List></Collection>");
        else{
            msgToSend.delete(msgToSend.length() - 2, msgToSend.length());
            msgToSend.append("]}");
        }

        return msgToSend.toString();
    }

    /** Get the list of all the lineups. */
    public String getLineups() {
        logger.Debug("Finding all the lineups...");
        String [] lineups = sageApiGlobal.GetAllLineups();
        logger.Debug(String.valueOf(lineups.length) + " lineups found.");
        StringBuilder msgToSend = new StringBuilder(String.valueOf(lineups.length));

        for (String lineup : lineups) 
            msgToSend.append(this.isXML ? DATA_SEPARATOR : "~").append(lineup);

        return msgToSend.toString();
    }

    /** Get the list of items in the playlist. */
    public String getThePlaylist(String plName) {
        logger.Debug("Finding all the items in the playlist...");
        AiringAPI.Airing airing = null;
        AlbumAPI.Album album = null;
        boolean isJSON = !(isXML || isTXT);
        Integer id = -1;
        MediaFileAPI.MediaFile mf = null;
        Object obj = null;
        PlaylistAPI.Playlist pl = null;
        PlaylistAPI.Playlist thisPlaylist = (plName.isEmpty() ? 
            playlist : sageApiPlaylist.FindPlaylist(plName));
        String artist = "", title = "";
        
        if (plName.equalsIgnoreCase(sageApiPlaylist.GetNowPlayingList().GetName()))
            thisPlaylist = sageApiPlaylist.GetNowPlayingList();
        Integer numberOfItems = thisPlaylist.GetNumberOfPlaylistItems();
        logger.Debug(numberOfItems.toString() + " item(s) found.");
        StringBuilder msgToSend = new StringBuilder(isJSON ? "{\"PlayList\": [" :
            (numberOfItems + DATA_SEPARATOR + thisPlaylist.GetName() + DATA_SEPARATOR));
        int max = (maxItems != -1 ? maxItems : DEFAULT_MAXITEMS);
 
        for (int i = 0; i < numberOfItems && i < max; i++)
        {
            obj = thisPlaylist.GetPlaylistItemAt(i);
            if (isJSON && i > 0) msgToSend.append(", ");
            
            if (thisPlaylist.GetPlaylistItemTypeAt(i).equalsIgnoreCase("Airing"))
            {
                airing = sageApiAiring.Wrap(obj);
                mf = airing.GetMediaFileForAiring();
                id = mf.GetMediaFileID();
                title = mf.GetMediaTitle();

                if (isJSON) msgToSend.append("{").append(JSONUtil.addJSONElement("ID", id))
                        .append(JSONUtil.addJSONElement("Title", title));
                else msgToSend.append(id).append(DATA_SEPARATOR).append(title);

                if (mf.IsMusicFile())
                {
                    artist = airing.GetShow().GetPeopleInShowInRoles(new String[]{"Artist", "Artiste"});

                    if (isJSON) msgToSend.append(JSONUtil.addJSONElement("Artist", artist))
                            .append("\"Type\": \"Song\"");
                    else msgToSend.append(" by ").append(artist).append(DATA_SEPARATOR)
                            .append("Song").append(DATA_SEPARATOR);
                }
                else if (mf.IsPictureFile()){
                    if (isJSON) msgToSend.append("\"Type\": \"Photo\"");
                    else msgToSend.append(DATA_SEPARATOR).append("Photo").append(DATA_SEPARATOR);
                }
                else if (mf.IsTVFile())
                {
                    String episode = airing.GetShow().GetShowEpisode();

                    if (isJSON) msgToSend.append(JSONUtil.addJSONElement("Episode", episode))
                            .append("\"Type\": \"Recording\"");
                    else{
                        msgToSend.append((episode.isEmpty() ? "" : ": " + episode)).append(DATA_SEPARATOR);
                        msgToSend.append("TV").append(DATA_SEPARATOR);
                    }
                }
                else if (mf.IsVideoFile()){
                    if (isJSON) msgToSend.append("\"Type\": \"Video\"");
                    else msgToSend.append(DATA_SEPARATOR).append("Video").append(DATA_SEPARATOR);
                }
                else if (mf.IsDVD()){
                    if (isJSON) msgToSend.append("\"Type\": \"DVD\"");
                    else msgToSend.append(DATA_SEPARATOR).append("DVD").append(DATA_SEPARATOR);
                }
                else{
                    if (isJSON) msgToSend.append("\"Type\": \"Other\"");
                    else {
                        msgToSend.append(DATA_SEPARATOR).append("Other").append(DATA_SEPARATOR);
                    msgToSend.append(mf.GetSegmentFiles()[0].getPath()).append(DATA_SEPARATOR);
                    }
                }

                if (isJSON) msgToSend.append("}");
            }
            else if (thisPlaylist.GetPlaylistItemTypeAt(i).equalsIgnoreCase("Album"))
            {
                try
                {
                    album = sageApiAlbum.Wrap(obj);
                    mf = album.GetAlbumTracks().get(0).GetMediaFileForAiring();
                    artist = album.GetAlbumArtist();
                    id = mf.GetMediaFileID();
                    title = album.GetAlbumName();

                    if (isJSON) msgToSend.append("{").append(JSONUtil.addJSONElement("ID", id))
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
                        if (isJSON){
//                            if (j > 0) msgToSend.append(", ");
//                            msgToSend.append("{")
//                                    .append(JSONUtil.addJSONElement("ID", mf.GetMediaFileID()))
//                                    .append(JSONUtil.addJSONElement("Title", mf.GetMediaTitle(), true)).append("}");
                        }
                        else msgToSend.append((j == 0 ? " " : ", ")).append(mf.GetSegmentFiles()[0].getPath());
                    }

                    if (isJSON) msgToSend.append("}");//("]}");
                    else msgToSend.append(DATA_SEPARATOR);
                }
                catch (Exception e)
                {
                    logger.Debug(e.getMessage());
                }
            }
            else if (thisPlaylist.GetPlaylistItemTypeAt(i).equalsIgnoreCase("Playlist"))
            {
                try
                {
                    pl = sageApiPlaylist.Wrap(obj);
                    mf = album.GetAlbumTracks().get(0).GetMediaFileForAiring();
                    msgToSend.append(String.valueOf(pl.GetNumberOfPlaylistItems())).append(DATA_SEPARATOR);
                    msgToSend.append(pl.GetName()).append(DATA_SEPARATOR);
                    msgToSend.append("Playlist").append(DATA_SEPARATOR);
                    msgToSend.append("").append(DATA_SEPARATOR);
                }
                catch (Exception e)
                {
                    logger.Debug(e.getMessage());
                }
            }
            else
            {
                // What else can it be?
                msgToSend.append("999").append(DATA_SEPARATOR);
                msgToSend.append(obj.toString()).append(DATA_SEPARATOR);
                msgToSend.append("Unknown").append(DATA_SEPARATOR);
                msgToSend.append("").append(DATA_SEPARATOR);
            }
        }

        if (isJSON) msgToSend.append("]}");

        return msgToSend.toString();
    }

    /** Delete a show (media file) based on a show ID */
    public boolean deleteShow(Integer showId) {
        try {
            MediaFileAPI.MediaFile mf = sageApiMediaFile.GetMediaFileForID(showId);
        
            return mf.DeleteFileWithoutPrejudice();
        }
        catch (Exception e) {
         
            return false;
        }
    }

            /** Setup VLC to stream a show (media file) based on a show ID */
    public void setVLCAlbum(Integer showId) {
        try {
            MediaFileAPI.MediaFile mf = sageApiMediaFile.GetMediaFileForID(showId);

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
            MediaFileAPI.MediaFile mf = sageApiMediaFile.GetMediaFileForID(showId);
            
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
            sb.append("--sout \"#duplicate{dst='transcode{").append(StreamingTranscodeOptions).append("}:");

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

    /** Get detailed information about a selected show. */
    public String getSelectedShowInfo(Integer showId, String showType) {
        AiringAPI.Airing air = null;
        FavoriteAPI.Favorite favorite = null;
        StringBuilder dataMsg = new StringBuilder();
        
        if (showType.equals("REC"))
        {
            MediaFileAPI.MediaFile mf = sageApiMediaFile.GetMediaFileForID(showId);
            air = mf.GetMediaFileAiring();
        }
        else if (showType.equals("FAV")) favorite = sageApiFavorite.GetFavoriteForID(showId);
        else air = sageApiAiring.GetAiringForID(showId);
        
        if (showType.equals("FAV"))
        {
            // format the string...
            if (isTXT)
            {
                boolean favFirstRun = favorite.IsFirstRuns();
                boolean favReRun = favorite.IsReRuns();
                String favChannel = favorite.GetFavoriteChannel();
                String favDesc = favorite.GetFavoriteDescription();
                String favTitle = favorite.GetFavoriteTitle();
                if (favChannel.isEmpty()) favChannel = "Any";

                dataMsg.append(favTitle).append(DATA_SEPARATOR);
                dataMsg.append(String.valueOf(showId)).append(DATA_SEPARATOR);
                dataMsg.append(favChannel).append(DATA_SEPARATOR);
                dataMsg.append(favDesc).append(DATA_SEPARATOR);
                dataMsg.append(String.valueOf(favFirstRun)).append(DATA_SEPARATOR);
                dataMsg.append(String.valueOf(favReRun)).append(DATA_SEPARATOR);
            }
        }
        else if (isTXT)
        {
            ShowAPI.Show show = air.GetShow();
            String category = show.GetShowCategory();
            String chanNum = air.GetAiringChannelNumber();
            String chanName = air.GetAiringChannelName();
            String desc = show.GetShowDescription();

            if (category.length() == 0) category = "Not Available";
            if (chanName.length() == 0) chanName = "None";
            if (chanNum.length() == 0) chanNum = "None";
            if (desc.length() == 0) desc = "No description available.";

            dataMsg.append(air.GetAiringTitle()).append(DATA_SEPARATOR);
            dataMsg.append(chanNum).append(DATA_SEPARATOR);
            dataMsg.append(chanName).append(DATA_SEPARATOR);
            dataMsg.append(category).append(DATA_SEPARATOR);
            dataMsg.append(String.valueOf(air.GetAiringStartTime())).append(DATA_SEPARATOR);
            dataMsg.append(air.IsAiringHDTV() ? "1" : "0").append(DATA_SEPARATOR);
            dataMsg.append(sageApiShow.IsShowFirstRun(air) ? "1" : "0").append(DATA_SEPARATOR);
            dataMsg.append(showType).append(DATA_SEPARATOR);
            dataMsg.append(desc).append(DATA_SEPARATOR);
        }
        else
        {
            dataMsg.append("<?xml version=\"1.0\" encoding=\"utf-8\"?><Collection>").append(
                XMLUtil.createXMLString(air, showId, "Program", sageApiShow, false)).append("</Collection>");
        }

        return dataMsg.toString();
    }
    
    /**
     * Set the filters that are to be applied to the lists.
     * @param data   Can be any of the following:
     *               clear  - clears all filters for all lists
     *               list,clear  - clears filtesr for the specific list
     *               list, filters  - filters to be applied to list
     */
    private void setFilters(String data) {
        // Remove all filters.
        if (data.equalsIgnoreCase("clear")) {
            trueFilters = new HashMap<String, ArrayList<String>>();
            falseFilters = new HashMap<String, ArrayList<String>>(); 
            logger.Debug("Removing ALL filters");
            return;
        }
        
        StringTokenizer dataList = new StringTokenizer(data, ",");
        
        String listToFilter = dataList.nextToken();
        
        ArrayList<String> trueFilts = new ArrayList<String>();
        if (trueFilters.containsKey(listToFilter)) {
            trueFilts = trueFilters.get(listToFilter);
        }
        
        ArrayList<String> falseFilts = new ArrayList<String>();      
         if (falseFilters.containsKey(listToFilter)) {
            falseFilts = falseFilters.get(listToFilter);
        }       
        
        logger.Debug("Modifying filters for list: " + listToFilter);
        while (dataList.hasMoreTokens()) {
            String dataLine = dataList.nextToken();
            if (dataLine.equalsIgnoreCase("clear")) {
                logger.Debug("Clearing filters for list: " + listToFilter);
                trueFilters.remove(listToFilter);
                falseFilters.remove(listToFilter);
                return;
            }
            StringTokenizer filterPair = new StringTokenizer(dataLine, "=");
            String filterExp = filterPair.nextToken();
            boolean boolExp = filterPair.nextToken().equalsIgnoreCase("true");

            if (boolExp == true) {
                logger.Debug("Adding to TRUE filter: " + filterExp);
                trueFilts.add(filterExp);
            }
            else {
                logger.Debug("Adding to FALSE filter: " + filterExp);                
                falseFilts.add(filterExp);
            }
        }
        
        trueFilters.put(listToFilter, trueFilts);
        falseFilters.put(listToFilter, falseFilts);
        
        if (logger.ShowDebug) {
            logger.Debug("List of TRUE filters: ");
            Set<String> keys = trueFilters.keySet();
            Iterator iter = keys.iterator();
            while (iter.hasNext()) {
                String listType = (String) iter.next();
                ArrayList<String> filts = trueFilters.get(listType);
                for (String str : filts) {
                    logger.Debug(listType + ": " + str);
                }
            }
            
            logger.Debug("List of FALSE filters: ");
            keys = falseFilters.keySet();
            iter = keys.iterator();
            while (iter.hasNext()) {
                String listType = (String) iter.next();
                ArrayList<String> filts = falseFilters.get(listType);
                for (String str : filts) {
                    logger.Debug(listType + ": " + str);
                }
            }
            
        }
        
    }    
    
   /**
    * Returns the list of Recorded shows.  The format of the output is 
    * dependant on the answer parameter (TXT|XML}.  Nothing is sent if
    * there was no changes since the last transmission.
    * 
    * @return msgToSend String - The list of Recorded shows.
    */
   public void getTVFileList() {
       String type = "IsTVFile";
        ArrayList<Integer> toAddList = new ArrayList<Integer>();
        ArrayList<Integer> toDeleteList = new ArrayList<Integer>();
        ArrayList<Integer> tempMediaList = new ArrayList<Integer>();
        boolean skipIt = false;
        Integer mediaFileID;
        String filename = "", result = "Done";
        // Running a one-time list of all current recorded TV shows
        logger.Debug("Finding all the listings for: " + type);
        MediaFileAPI.List allMediaFiles = sageApiMediaFile.GetMediaFiles();
        allMediaFiles = allMediaFiles.FilterByBoolMethod(type, true);
        int max = (maxItems != -1 ? maxItems : DEFAULT_MAXITEMS);

        // Apply any user-indicated filters
        if (trueFilters.containsKey("REC")) {

            ArrayList<String> filters = trueFilters.get("REC");
            if (!filters.isEmpty()) {
            String filterStr = filters.remove(0);
            for (String filt : filters) {
                filterStr.concat("|" + filt);
            }

            logger.Debug("Applying user filter string for TRUE: " + filterStr);
            allMediaFiles = allMediaFiles.FilterByBoolMethod(filterStr, true);
            }

            filters = falseFilters.get("REC");
            if (!filters.isEmpty()) {
                String filterStr = filters.remove(0);
                for (String filt : filters) {
                    filterStr.concat("|" + filt);
                }

                logger.Debug("Applying user filter string for FALSE: " + filterStr);
                allMediaFiles = allMediaFiles.FilterByBoolMethod(filterStr, false);
            }

        }
        for (Integer recID : allRecordedShows) {
            tempMediaList.add(recID);
        }

        allMediaFiles = allMediaFiles.SortLexical(true, "GetFileStartTime");
        int showListLength = allMediaFiles.size();
        logger.Debug(showListLength + " files found.");
        checkAvailableData(type, allMediaFiles.size());
        for (Integer recID : tempMediaList) toDeleteList.add(recID);
        logger.Debug("Done filling toDelete...");
        StringBuilder msgToSend = new StringBuilder();

        if (isTXT) msgToSend.append(showListLength).append(DATA_SEPARATOR);
        else if (isXML) msgToSend.append("<?xml version=\"1.0\" encoding=\"utf-8\"?><Collection><List Name=\"")
                .append(type).append("\">");
        else {
            msgToSend.append("{\"");
            msgToSend.append("Recordings");
            msgToSend.append("\": [");
        }

        for (MediaFileAPI.MediaFile mf : allMediaFiles)
        {
            if (toAddList.size() >= max)
            {
                result = "Partial";
                break;
            }
                
            mediaFileID = mf.GetMediaFileID();
            
            if (!isTXT)
            {
                if (!tempMediaList.contains(mediaFileID))// && !(maxItems != -1 && toAddList.size() >= maxItems))
                {
                    toAddList.add(mediaFileID);
                    
                    try
                    {
                        filename = mf.GetSegmentFiles()[0].getPath();
                        if (isXML) msgToSend.append(XMLUtil.createXMLString(mf.GetMediaFileAiring(),
                            mediaFileID, "Program", sageApiShow, "Add", "", false, type, filename));
                        else msgToSend.append(JSONUtil.createJSONString(mf.GetMediaFileAiring(),
                            mediaFileID, "Program", sageApiShow, "Add", false, type, filename));
                    }
                    catch (Exception e) { }
                }
                else toDeleteList.remove(toDeleteList.indexOf(mediaFileID));
            }
            else
            {
                toAddList.add(mediaFileID);
                msgToSend.append(mf.GetMediaTitle()).append(DATA_SEPARATOR);
                msgToSend.append(mediaFileID.toString()).append(DATA_SEPARATOR);
            }
        }
   
        if (!isTXT)
        {
            for (Integer toAdd : toAddList) allRecordedShows.add(toAdd);
            
            for (Integer toDelete : toDeleteList)
            {
                allRecordedShows.remove(toDelete);
                
                if (isXML) msgToSend.append(XMLUtil.createXMLString(toDelete, "Program", "Del"));
                else msgToSend.append(JSONUtil.createJSONString(toDelete, "Del"));
            }
            
            if (toAddList.size() + toDeleteList.size() == 0) skipIt = true;
            else if (isXML) msgToSend.append("</List></Collection>");
            else if (toAddList.isEmpty() && toDeleteList.isEmpty()) msgToSend.append("]}");
            else{
                msgToSend.delete(msgToSend.length() - 2, msgToSend.length());
                msgToSend.append("]}");
            }

        }
        else if (!toAddList.equals(allRecordedShows)) allRecordedShows = toAddList;
        else skipIt = true;

        if (!skipIt)
        {
            outgoingMessages.add(new Message(MessageType.RECORDED_SHOW_LIST, msgToSend.toString()));
            if (!isTXT) outgoingMessages.add(new Message(MessageType.RECORDED_SHOW_LIST, result));
        }
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
        int rescale = 100000;
        long lastTime = (isLibraryFile ? lastVideo : lastPhoto);
        String filename = "", result = "Done";
        StringBuilder msgToSend = new StringBuilder();
        logger.Debug("Finding all the listings for: " + type);
        logger.Debug("Offset: " + offset + ", last time: " + lastTime);

        MediaFileAPI.List allMediaFiles =
                sageApiMediaFile.GetMediaFiles().FilterByBoolMethod(type, true);

        if (isLibraryFile)
            allMediaFiles = allMediaFiles.FilterByBoolMethod("IsPictureFile|IsMusicFile", false);

        allMediaFiles = allMediaFiles.SortLexical(true, "GetFileStartTime");
        int size = allMediaFiles.size();
        logger.Debug(size + " files found.");
        checkAvailableData(type, allMediaFiles.size());

        if (allMediaFiles.isEmpty()){
            if (!isTXT) outgoingMessages.add(new Message(mt, result));
            return;
        }

        if (isTXT) msgToSend.append(allMediaFiles.size()).append(DATA_SEPARATOR);
        else if (isXML) msgToSend.append("<?xml version=\"1.0\" encoding=\"utf-8\"?><Collection><List Name=\"")
                .append(type).append("\">");
        else msgToSend.append("{\"").append(isLibraryFile ? "Videos" : "Photos").append("\": [");

        MediaFileAPI.MediaFile mf;
        AiringAPI.Airing airing = null;
        for (int i = 0; i + offset < size; i++)
        {
            if (count >= max)
            {
                if (!isTXT)
                {
                    if (isLibraryFile) offsetOther += count;
                    else offsetPicture += count;
                    if (isXML) msgToSend.append("</List></Collection>");
                    else{
                        msgToSend.delete(msgToSend.length() - 2, msgToSend.length());
                        msgToSend.append("]}");
                    }
                }
                outgoingMessages.add(new Message(mt, msgToSend.toString()));
                count = 0;

                if (maxItems != -1){
                    result = "Partial";
                    break;
                }
                else {
                    msgToSend = new StringBuilder();
                    if (isTXT) msgToSend.append(allMediaFiles.size()).append(DATA_SEPARATOR); // This one makes no sense...
                    else if (isXML) msgToSend.append("<?xml version=\"1.0\" encoding=\"utf-8\"?><Collection><List Name=\"")
                            .append(type).append("\">");
                    else msgToSend.append("{\"").append(isLibraryFile ? "Videos" : "Photos").append("\": [");
                }
            }

            mf = allMediaFiles.get(size - 1 - (offset + i) );
            airing = mf.GetMediaFileAiring();

            if (airing.GetAiringStartTime()/rescale <= lastTime) continue;
            else count++;
            
            mediaFileID = mf.GetMediaFileID();

            if (!isTXT)
            {
                try
                {
                    filename = mf.GetSegmentFiles()[0].getPath();
                    if (isXML) msgToSend.append(XMLUtil.createXMLString(airing,
                        mediaFileID, "Program", sageApiShow, "Add", "", false, type, filename));
                    else msgToSend.append(JSONUtil.createJSONString(airing,
                        mediaFileID, "Program", sageApiShow, "Add", false, type, filename));
                }
                catch (Exception e) { }
            }
            else
            {
                msgToSend.append(mf.GetMediaTitle()).append(DATA_SEPARATOR);
                msgToSend.append(mediaFileID).append(DATA_SEPARATOR);
            }
        }
        
        if (count > 0){
            if (!isTXT)
            {
                if (isLibraryFile) offsetOther += count;
                else offsetPicture += count;
                if (isXML) msgToSend.append("</List></Collection>");
                else{
                    msgToSend.delete(msgToSend.length() - 2, msgToSend.length());
                    msgToSend.append("]}");
                }
            }
            outgoingMessages.add(new Message(mt, msgToSend.toString()));
       }

        if (!isTXT)
        {
            if (isLibraryFile) lastVideo = airing.GetAiringStartTime()/rescale;
            else lastPhoto = airing.GetAiringStartTime()/rescale;
            outgoingMessages.add(new Message(mt, result));
       }
    }

   /** */
   public void getAlbumList() {
        boolean sentAny = false;
        Integer mediaFileID, index, albumCount = 0;
        String result = "Done";
        StringBuffer msgToSend = new StringBuffer();
        logger.Debug("Finding all the albums");
        AlbumAPI.List allAlbums =  sageApiAlbum.GetAlbums();
        logger.Debug(String.valueOf(allAlbums.size()) + " albums found.");
        checkAvailableData("IsAlbums", allAlbums.size());
        allAlbums = allAlbums.SortLexical(false, "GetAlbumName");
        
        for (AlbumAPI.Album album : allAlbums)
        {
            albumCount++;
            if (maxItems != -1 && albumCount > maxItems)
            {
                result = "Partial";
                break;
            }
            else if (album.GetAlbumTracks().size() == 0) continue;

            index = 1;
            if (isXML) msgToSend = new StringBuffer(XMLUtil.createXMLAlbum(album));
            else msgToSend = new StringBuffer(JSONUtil.createJSONAlbum(album));

            for (AiringAPI.Airing air : album.GetAlbumTracks())
            {
                mediaFileID = air.GetMediaFileForAiring().GetMediaFileID();
                
                if (index < 4 * DEFAULT_MAXITEMS)
                {
                    try
                    {
                        if (isXML) msgToSend.append(XMLUtil.createXMLSong(air, mediaFileID, sageApiShow,
                                album.GetAlbumArtist(), album.GetAlbumGenre(), index));
                        else msgToSend.append(JSONUtil.createJSONSong(air, mediaFileID, sageApiShow,
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
            outgoingMessages.add(new Message(MessageType.MUSIC_FILES_LIST, msgToSend.toString()));
            sentAny = true;
        }
        
        if (sentAny) outgoingMessages.add(new Message(MessageType.MUSIC_FILES_LIST, result));
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
        ShowAPI.Show show = sageApiShow.GetShowForExternalID(EPGID);
        logger.Debug("Finding all the upcoming episodes for:" + show.GetShowTitle());
        Date now = new Date();
        AiringAPI.List allAirings = show.GetAiringsForShow(now.getTime());
        logger.Debug(String.valueOf(allAirings.size()) + " upcoming episodes found.");
        StringBuilder msgToSend = new StringBuilder();

        if (isXML) msgToSend.append("<?xml version=\"1.0\" encoding=\"utf-8\"?><Collection><EPGID ID=\"")
                .append(EPGID).append("\">");
        else msgToSend.append("{\"UpcomingEpisodes\": [");
        
        for (AiringAPI.Airing air : allAirings)
        {
            if (isXML) msgToSend.append(XMLUtil.createXMLString(air, air.GetAiringID(),
                    "Program", sageApiShow, true));
            else msgToSend.append(JSONUtil.createJSONString(air, air.GetAiringID(),
                    sageApiShow, true));
        }

        if (isXML) msgToSend.append("</EPGID></Collection>");
        else if (allAirings.size() == 0) msgToSend.append("]}");
        else{
            msgToSend.delete(msgToSend.length() - 2, msgToSend.length());
            msgToSend.append("]}");
        }

        return msgToSend.toString();   
    }

    /**
    * Returns the list of Upcoming recordings, the format of the output is
    * dependant on the answer type (TXT|XML}.  Nothing is sent if there was
    * no changes since the last transmission.
    * 
    * @return msgToSend String - The list of Upcoming recordings.
    */
    public void getUpcomingRecordingsList() {
        ArrayList<Integer> toAddList = new ArrayList<Integer>();
        ArrayList<Integer> toDeleteList = new ArrayList<Integer>();
        boolean skipIt = false;
        Integer airingID;
        String result = "Done";
        int max = (maxItems != -1 ? maxItems : DEFAULT_MAXITEMS);
        logger.Debug("Finding all upcoming recordings.....");
        AiringAPI.List upcomingRecordings = sageApiGlobal.GetScheduledRecordings();
        upcomingRecordings = upcomingRecordings.SortLexical(false, "GetAiringStartTime");
//        upcomingRecordings = upcomingRecordings.SortLexical(false, "GetAiringStartTime", new Object());
        String upcomingRecordingsListLength = String.valueOf(upcomingRecordings.size());
        logger.Debug(upcomingRecordingsListLength + " scheduled recordings found.");
        for (Integer recID : allUpcomingRecordings) toDeleteList.add(recID);

        StringBuilder msgToSend = new StringBuilder();

        if (isTXT) msgToSend.append(upcomingRecordingsListLength).append(DATA_SEPARATOR);
        else if (isXML) msgToSend.append(
                "<?xml version=\"1.0\" encoding=\"utf-8\"?><Collection><List Name=\"Upcoming\">");
        else msgToSend.append("{\"Upcoming\": [");

        for (AiringAPI.Airing airing : upcomingRecordings)
        {
            if (toAddList.size() >= max)
            {
                result = "Partial";
                break;
            }

            airingID = airing.GetAiringID();
           
            if (!isTXT)
            {
                if (!allUpcomingRecordings.contains(airingID))
                {
                    toAddList.add(airingID);
                    if (isXML) msgToSend.append(XMLUtil.createXMLString(airing,
                            airingID, "Program", sageApiShow, "Add", "", false, "IsTVFile", ""));
                    else msgToSend.append(JSONUtil.createJSONString(airing,
                            airingID, "Program", sageApiShow, "Add", false, "IsTVFile", ""));
                }
                else toDeleteList.remove(toDeleteList.indexOf(airingID));
            }
            else
            {
                toAddList.add(airingID);
                msgToSend.append(airing.GetShow().GetShowTitle()).append(DATA_SEPARATOR);
                msgToSend.append(String.valueOf(airing.GetAiringStartTime())).append(DATA_SEPARATOR);
                msgToSend.append(airing.GetAiringChannelNumber()).append(DATA_SEPARATOR);
                msgToSend.append(airingID.toString()).append(DATA_SEPARATOR);
            }
        }
        
        if (!isTXT)
        {
            for (Integer toAdd : toAddList) allUpcomingRecordings.add(toAdd);
            for (Integer toDelete : toDeleteList)
            {
                allUpcomingRecordings.remove(toDelete);
                if (isXML) msgToSend.append(XMLUtil.createXMLString(toDelete, "Program", "Del"));
                else msgToSend.append(JSONUtil.createJSONString(toDelete, "Del"));
            }
            
            if (toAddList.size() + toDeleteList.size() == 0) skipIt = true;
            else if (isXML) msgToSend.append("</List></Collection>");
            else if (toAddList.isEmpty() && toDeleteList.isEmpty()) msgToSend.append("]}");
            else{
                msgToSend.delete(msgToSend.length() - 2, msgToSend.length());
                msgToSend.append("]}");
            }
        }
        else if (toAddList.equals(allUpcomingRecordings)) skipIt = true;
        else allUpcomingRecordings = toAddList;
        
        if (!skipIt)
        {
            outgoingMessages.add(new Message(MessageType.UPCOMING_RECORDINGS_LIST, msgToSend.toString()));
            if (!isTXT) outgoingMessages.add(new Message(MessageType.UPCOMING_RECORDINGS_LIST, result));
        }
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

        PlaylistAPI.Playlist thisPlaylist = (plName.isEmpty() ? 
            playlist : sageApiPlaylist.FindPlaylist(plName));

        try
        {
            showId = Integer.valueOf(ID);
            MediaFileAPI.MediaFile mf = sageApiMediaFile.GetMediaFileForID(showId.intValue());
            if (mf == null) logger.Debug("No show found with ID of " + showId.toString());
            else 
            {
                Object mediaFile = null;
                
                if (isAlbum)
                {
                    AlbumAPI.Album album = mf.GetAlbumForFile();
                    logger.Debug("Adding to the playlist the songs from: " + album.GetAlbumName());
                    Object albumUW = sageApiAlbum.Unwrap(album);
                    thisPlaylist.AddToPlaylist(albumUW);
                }
                else
                {
                    logger.Debug("Adding to the playlist: " + mf.GetMediaTitle());
                    mediaFile = sageApiMediaFile.Unwrap(mf);
                    thisPlaylist.AddToPlaylist(mediaFile);
                }
            }
        }
        catch (Throwable t)
        {
            logger.Error(t);   
            logger.Message("Show ID: " + showId.toString());
        }                            
    }

    /** Change the playlist */
    public void changePlaylist(String newPlaylist) {
        for (PlaylistAPI.Playlist pl : sageApiPlaylist.GetPlaylists())
        {
            if (pl.GetName().equalsIgnoreCase(newPlaylist))
            {
                playlist = pl;
                return;
            }
        }
        
        if (newPlaylist.equalsIgnoreCase("Now Playing")) playlist = sageApiPlaylist.GetNowPlayingList();
        else playlist = sageApiPlaylist.AddPlaylist(newPlaylist);
    }

        /** Delete a playlist */
    public void clearPlaylist() {
        Integer numberOfItems = playlist.GetNumberOfPlaylistItems();

        try
        {
            for (int i = 1; i <= numberOfItems; i++)
                playlist.RemovePlaylistItemAt(numberOfItems - i);
        }
        catch (Exception e)
        {
            logger.Debug(e.getMessage());
        }

        String dataStr = getThePlaylist(playlist.GetName());
        outgoingMessages.add(new Message(MessageType.GET_THE_PLAYLIST, dataStr));
    }

    /** Delete a playlist */
    public void DeletePlaylist(String plName) {
        PlaylistAPI.Playlist thisPlaylist = (plName.isEmpty() ? 
            playlist : sageApiPlaylist.FindPlaylist(plName));

            Integer numberOfItems = thisPlaylist.GetNumberOfPlaylistItems();

            try
            {
                if (thisPlaylist.GetName().equalsIgnoreCase(sageApiPlaylist.GetNowPlayingList().GetName()))
                    for (int i = 1; i <= numberOfItems; i++) 
                        thisPlaylist.RemovePlaylistItemAt(numberOfItems - i);
                else
                {
                    thisPlaylist.RemovePlaylist();
                    thisPlaylist = sageApiPlaylist.GetNowPlayingList();
                }
            } 
            catch (Exception e)
            {
                logger.Debug(e.getMessage());
            }

        String dataStr = listThePlaylists();
        outgoingMessages.add(new Message(MessageType.LIST_THE_PLAYLISTS, dataStr));
        dataStr = getThePlaylist(thisPlaylist.GetName());
        outgoingMessages.add(new Message(MessageType.GET_THE_PLAYLIST, dataStr));
    }

    /** Delete an item from a playlist */
    public void DeletePlaylistItem(String msg) {
        String SEPARATOR_CHAR = "\\|";
        String [] parameters = msg.split(SEPARATOR_CHAR);
        String plName = (parameters.length > 1 ? parameters[0] : "");
        String ID = parameters[parameters.length - 1];

        PlaylistAPI.Playlist thisPlaylist = (plName.isEmpty() ? 
            playlist : sageApiPlaylist.FindPlaylist(plName));

        try
        {
            Integer item = Integer.valueOf(ID);

            if (item < thisPlaylist.GetNumberOfPlaylistItems())
                thisPlaylist.RemovePlaylistItemAt(item);
        }
        catch (Exception e)
        {
            logger.Debug(e.getMessage());
        }
        String dataStr = getThePlaylist(thisPlaylist.GetName());
        outgoingMessages.add(new Message(MessageType.GET_THE_PLAYLIST, dataStr));
    }

    /** Returns the used and available disk space */
    public String getVideoDiskSpace() {
        long usedVideoDiskSpace = sageApiGlobal.GetUsedVideoDiskspace() / 1048576;
        long totalDiskSpace = sageApiGlobal.GetTotalDiskspaceAvailable() / 1048576;
        
        return String.valueOf(usedVideoDiskSpace) + "|" + String.valueOf(totalDiskSpace);
    }

    /** Get the number of currently recording files */
    public void getNumberOfCurrentlyRecordingFiles() {
        MediaFileAPI.List allCurrentlyRecordingFiles = sageApiGlobal.GetCurrentlyRecordingMediaFiles();
        
        if (numberOfRecordings != allCurrentlyRecordingFiles.size())
        {
            numberOfRecordings = allCurrentlyRecordingFiles.size();
            outgoingMessages.add(new Message(MessageType.NUM_CURRENTLY_RECORDING_FILES, numberOfRecordings.toString()));
        }
    }
    
    /** Get the outgoing data for the automated processing. */
    public void getOutgoingData() {
        String returnValue = "";
        
        try
        {
             // If the media player is loading, then we're going to wait to update anything new
            if (serverMode.equalsIgnoreCase("Player") && !sageApiMediaPlayer.IsMediaPlayerLoading ()) {
                States newState = getNewState();
                boolean isNewTitle = (newState == States.Play) && currentMediaFileID !=
                        sageApiMediaPlayer.GetCurrentMediaFile().GetMediaFileID();

                if (isNewTitle) changeStates(newState);
                else if(newState != currentState) {
                    if (newState == States.Stop)
                        outgoingMessages.add(new Message(MessageType.CLEAR_CURRENT));
                    outgoingMessages.add(new Message(MessageType.PLAY_MODE, newState.toString()));
                    pingCount = 0;
                    currentState = newState;
                    }
                else{
                    if  (pingCount > 4){
                        pingCount = 0;
                        outgoingMessages.add(new Message(MessageType.PLAY_MODE, newState.toString()));
                    }
                    else pingCount++;
                }

                if (sageApiMediaPlayer.GetVolume() != currentVolume) {
                    currentVolume = sageApiMediaPlayer.GetVolume();
                    int volume = (int)(100f * currentVolume);
                    outgoingMessages.add(new Message(MessageType.VOLUME, String.valueOf(volume)));
                    pingCount = 0;
                }

                // All high rate data here
                switch (currentState) {
                    case Play:
                        long newMediaTime = sageApiMediaPlayer.GetMediaTime() / 1000;
                        
                        // Convert to time elapsed
                        //currentMediaTime -= currentMediaFile.getStartTime();
                        
                        if (newMediaTime - currentMediaTime != 0l) {
                            currentMediaTime = newMediaTime;
                            outgoingMessages.add(new Message(MessageType.CURRENT_TIME, currentMediaTime.toString()));
                            pingCount = 0;
                        }
                        
                        // break intentionally omitted
                    case Pause:
                        if (currentMediaFile.getIsLive()) {
                            // If the file playing is live, that means that the duration will 
                            // continue to change as it is being watched (and recorded)
                            long currentDurLong = sageApiMediaPlayer.GetMediaDuration() / 1000;
                            
                            if (currentDurLong - currentDuration != 0l) {
                                currentDuration = currentDurLong;
                                outgoingMessages.add(new Message(MessageType.CURRENT_DURATION, currentDuration.toString()));
                                pingCount = 0;
                            }
                        }
                        break;
                    default:
                        // Stop case, which we do nothing for
                        break;
                }
            }

            // All low rate data here
            if (lowRateInterval.execute())
            {
                if (serverMode.equalsIgnoreCase("Media")){
                    getNumberOfCurrentlyRecordingFiles();

                    if  (pingCount > 2){
                        pingCount = 0;
                        returnValue = getSystemMessages();
                        if (!returnValue.isEmpty())
                            outgoingMessages.add(new Message(MessageType.SYSTEM_MESSAGE, returnValue));
                        else
                        {
                            String diskStr = getVideoDiskSpace();
                            if (!diskStr.isEmpty()){
                                videoDiskSpaceString = diskStr;
                                outgoingMessages.add(new Message(MessageType.VIDEO_DISK_SPACE, videoDiskSpaceString));
                            }
                        }
                    }
                    else pingCount++;
                }
                else
                {
                    // Check screen mode
                    if (sageApiGlobal != null && sageApiGlobal.IsFullScreen())
                    {
                        if (!currentScreenMode.equalsIgnoreCase(FULL_SCREEN_MODE))
                        {
                            currentScreenMode = FULL_SCREEN_MODE;
                            outgoingMessages.add(new Message(MessageType.WINDOWING_MODE, currentScreenMode));
                            pingCount = 0;
                        }
                    }
                    else
                    {
                        if (!currentScreenMode.equalsIgnoreCase(WINDOWED_MODE))
                        {
                            currentScreenMode = WINDOWED_MODE;
                            outgoingMessages.add(new Message(MessageType.WINDOWING_MODE, currentScreenMode));
                            pingCount = 0;
                        }
                    }

                    // Check mute state
                    if (sageApiMediaPlayer.IsMuted() && (!currentMuteState))
                    {
                        outgoingMessages.add(new Message(MessageType.MUTE, "True"));
                        currentMuteState = true;
                        pingCount = 0;
                    }
                    else if (!sageApiMediaPlayer.IsMuted() && (currentMuteState))
                    {
                        outgoingMessages.add(new Message(MessageType.MUTE, "False"));
                        currentMuteState = false;
                        pingCount = 0;
                    }
                    
                    if (currentState == States.Play && currentMediaFile.isDvd())
                    {
                        Long currentDurLong = sageApiMediaPlayer.GetMediaDuration() / 1000;
                        outgoingMessages.add(new Message(MessageType.CURRENT_DURATION, currentDurLong.toString()));
                        pingCount = 0;
                    }
                }
            }

            // All very low rate data here.
            if (veryLowRateInterval.execute() && serverMode.equalsIgnoreCase("Media"))
            {
                getUpcomingRecordingsList();
                
                // FOR TESTING
                getTVFileList();
                //getMediaFileList("IsLibraryFile", MessageType.RECORDED_SHOW_LIST);

                returnValue = getManualRecordingsList();
                if (!returnValue.isEmpty()) 
                outgoingMessages.add(new Message(MessageType.MANUAL_RECORDINGS_LIST, returnValue));

                returnValue = getFavoriteList();
                if (!returnValue.isEmpty()) 
                    outgoingMessages.add(new Message(MessageType.FAVORITE_SHOW_LIST, returnValue));
            }
        }
        catch (Exception ex)
        {
            logger.Debug(ex.getMessage());
        }
    }

    private void keystroke(String key){
        sageUtility.Keystroke(key, false);
    }

    private States getNewState() {
        if (sageApiMediaPlayer.IsPlaying() == false) {
            if (sageApiMediaPlayer.HasMediaFile()) return States.Pause;
            else return States.Stop;
        }
        else return States.Play;
    }

    /** Get the list of playlists. */
    public String listThePlaylists() {
        logger.Debug("Finding all the playlists...");
        ArrayList<String> playlistNames = new ArrayList<String>();

        // Can crash if extender is off line...
        try{
            PlaylistAPI.List allThePlaylists = sageApiPlaylist.GetPlaylists();
            String nowPlaying = sageApiPlaylist.GetNowPlayingList().GetName();

            for (PlaylistAPI.Playlist pl : allThePlaylists)
                playlistNames.add(pl.GetName());

            if (!playlistNames.contains(nowPlaying)) playlistNames.add(0, nowPlaying);
        }
        catch (Exception ex) {ex.printStackTrace();}

        Integer numberOfItems = playlistNames.size();
        logger.Debug(numberOfItems + " playlist(s) found.");
        StringBuilder msgToSend = new StringBuilder(String.valueOf(numberOfItems));

        for (String name : playlistNames)
            msgToSend.append(DATA_SEPARATOR).append(name);
        
        return msgToSend.toString();
    }

    /** Change the state of the play mode */
    private void changeStates(States newState) {
        logger.Debug("[ChangeStates] About to process");
        try
        {
            currentMediaFile = new MediaStore(sageApiMediaPlayer.GetCurrentMediaFile(), sageApiMediaPlayer.IsCurrentMediaFileRecording());
            currentMediaFileID = currentMediaFile.getMediaFileId();
            String episode = currentMediaFile.getEpisode();
            String title = currentMediaFile.getTitle();
            long start = currentMediaFile.getStartTime();
            long end = currentMediaFile.getEndTime();
            String duration = currentMediaFile.getDurationStr();
            boolean isMusicFile = currentMediaFile.isMusicFile();
            AiringAPI.Airing airing = sageApiMediaPlayer.GetCurrentMediaFile().GetMediaFileAiring();
            ShowAPI.Show show = airing.GetShow();
            String genre = show.GetShowCategory();
            String year = show.GetShowYear();

            if (start == end)
            {
                start = sageApiMediaPlayer.GetAvailableSeekingStart()/1000;
                end = sageApiMediaPlayer.GetAvailableSeekingEnd()/1000;
                duration = String.valueOf(end - start);
            }

            outgoingMessages.add(new Message(MessageType.CURRENT_START_TIME, String.valueOf(start)));
            outgoingMessages.add(new Message(MessageType.CURRENT_END_TIME, String.valueOf(end)));

            if (isMusicFile)
            {
                AlbumAPI.Album album = currentMediaFile.getAlbum();
                String artist = show.GetPeopleInShowInRoles(new String[] {"Artist", "Artiste"});
                String albumName = album.GetAlbumName();
                String category = (genre.isEmpty() ? album.GetAlbumGenre() : genre);
                year = album.GetAlbumYear();
                if (!albumName.isEmpty()) title = albumName + " - " + title;

                outgoingMessages.add(new Message(MessageType.CURRENT_ARTIST,
                        (artist.isEmpty() ? album.GetAlbumArtist() : artist)));
                outgoingMessages.add(new Message(MessageType.CURRENT_CATEGORY, category));
                outgoingMessages.add(new Message(MessageType.CURRENT_TYPE, "Audio"));
            }
            else
            {
                String actors = show.GetPeopleInShowInRoles(new String[]
                        {"Actor", "Acteur", "Guest", "Invité", "Special guest", "Invité spécial"});
                String channel = currentMediaFile.getChannel();
                if (!channel.isEmpty()){
                    outgoingMessages.add(new Message(MessageType.CURRENT_CHANNEL, channel));
                    outgoingMessages.add(new Message(MessageType.CURRENT_STATION_NAME,
                            channel + (airing.GetChannel() != null ? " " +
                            airing.GetChannel().GetChannelDescription() : "")));
                }
                outgoingMessages.add(new Message(MessageType.CURRENT_CATEGORY, genre));
                outgoingMessages.add(new Message(MessageType.CURRENT_ACTORS, actors));

                if (currentMediaFile.isDvd())
                    outgoingMessages.add(new Message(MessageType.CURRENT_TYPE, "DVD"));
                else if (currentMediaFile.getIsLive())
                    outgoingMessages.add(new Message(MessageType.CURRENT_TYPE, "Live TV"));
                else if (currentMediaFile.getIsTvFile())
                    outgoingMessages.add(new Message(MessageType.CURRENT_TYPE, "TV"));
                else outgoingMessages.add(new Message(MessageType.CURRENT_TYPE, "Video"));

            }

            outgoingMessages.add(new Message(MessageType.CURRENT_TITLE, title));
            outgoingMessages.add(new Message(MessageType.CURRENT_DURATION, duration));
            outgoingMessages.add(new Message(MessageType.CURRENT_DESC, currentMediaFile.getDescription()));
            outgoingMessages.add(new Message(MessageType.CURRENT_YEAR, (year.isEmpty() ? "0" : year)));
            if (!episode.equalsIgnoreCase(title) || isMusicFile)
                outgoingMessages.add(new Message(MessageType.CURRENT_EPISODE, episode));
            outgoingMessages.add(new Message(MessageType.CURRENT_ID, String.valueOf(currentMediaFileID)));
        }
        catch (Exception ex)
        {
            logger.Debug(ex.getMessage());
        }

        outgoingMessages.add(new Message(MessageType.PLAY_MODE, newState.toString()));
        currentState = newState;
    }

    /** Set the volume. */
    public void setVolume(String volumeStr) {
        try
        {
            float vol = Float.valueOf(volumeStr) / 100f;
            sageApiMediaPlayer.SetVolume(vol);
        }
        catch (Exception ex)
        {
            logger.Debug(ex.getMessage());
        }
    }
    
    /** Set the volume. */
    public void setMute(String muteOn) {
        try
        {
            boolean mute = Boolean.getBoolean(muteOn);
            sageApiMediaPlayer.SetMute(mute);
        }
        catch (Exception ex)
        {
            logger.Debug(ex.getMessage());
        }
    }

    // From Nielm's web server code
    
    public static Object mvpReboot(String uiContext){
        return mvpCommand(uiContext, "killall miniclient;sleep 1;reboot");
    }

    public static Object mvpPowerOff(String uiContext){
        return mvpCommand(uiContext, "killall miniclient");
    }

    public static Object stxPowerOff(String uiContext){
        return mvpCommand(uiContext, "poweroff");
    }
    
    public static Object stxPowerOn(String uiContext){
        return mvpCommand(uiContext, "poweron");
    }

    public static Object stxReboot(String uiContext){
        return mvpCommand(uiContext, "reboot");
    }
 
    private static Object mvpCommand(String uiContext,String command) {
        // check Mac or IP
        if ( uiContext.matches("(\\d{1,3}\\.){3}\\d{1,3}")) {
            try 
            {
                InetAddress ipaddr=InetAddress.getByName(uiContext);
                return mvpCommand(ipaddr,command);
            } 
            catch (java.net.UnknownHostException e){}
        }
        // assume Mac
        uiContext=uiContext.toLowerCase().replaceAll("[^a-f0-9]", "");
        if ( uiContext.length()!=12)
        {
            return "Invalid Mac address: cleaned should be 12 chars: "+uiContext;
        }
        final Process p;
        
        try 
        {
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

            if ( true) 
            { // || SageApi.booleanApi("IsWindowsOs", null)) {
                p=Runtime.getRuntime().exec("arp -a");
            } 
            else p=Runtime.getRuntime().exec("arp -an");

            try
            {
                Thread errGobbler = new Thread() 
                {
                    public void run() 
                    {
                        BufferedReader in = new BufferedReader(new InputStreamReader(p.getErrorStream()));
                        String line;
                        try 
                        {
                            while((line=in.readLine())!=null)
                            {
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
                try 
                {
                    Pattern pat=Pattern.compile("^.*?(([0-9]{1,3}.){3}[0-9]{1,3}).+?(([0-9a-f]{2}[:\\-.]){5}[0-9a-f]{2}).*$",
                        Pattern.CASE_INSENSITIVE);
                    while((line=in.readLine())!=null)
                    {
                        System.out.println("Arp: stdout: "+line);
                        java.util.regex.Matcher m=pat.matcher(line);
                        if ( m.matches())
                        {
                            if ( m.group(3).toLowerCase().replaceAll("[^a-f0-9]", "").equals(uiContext))
                                {
                                    try 
                                    {
                                        addr = InetAddress.getByName(m.group(1));
                                    }
                                    catch (java.net.UnknownHostException e)
                                    {
                                        System.out.println("arp " + e);
                                    }
                                }
                            }
                        }
                        in.close();
                        if ( addr!=null) return mvpCommand(addr,command);
                    } 
                catch (IOException e){}
                }
                finally 
                {
                    try 
                    {
                        System.out.println("arp exited with " + p.exitValue());
                    }
                    catch (IllegalThreadStateException e)
                    {
                        System.out.println("Terminating ARP");
                        p.destroy();
                        p.getOutputStream().close(); // close stdin
                    }
            }
            return "Could not determine IP address for UI Context";

        }
        catch (Exception e) 
        {
            System.out.println("Getting IP for MAC " + e);
        }
        return "Could not determine IP address for UI Context - Arp failed";

    }
    
    static Object mvpCommand(InetAddress ipaddr, String command) {
        System.out.println("Issuing the command: " + command + " on the extender at " + ipaddr);

        PrintWriter out =null;
        BufferedReader in =null;
        java.net.Socket sock =null;
        String line = "";
        
        try 
        {
            sock = new java.net.Socket(ipaddr,23);
            out = new PrintWriter(sock.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
            while(in.read() != 32); // Must read login prompt before sending login name
            Thread.sleep(1000);
            out.println("root");
            Thread.sleep(1000);
            out.println(command);
            Thread.sleep(1500); // Give it a sec to do its thing
            out.println("exit");
        } 
        catch (Exception e)
        {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
            return "Failed to connect to extender at " + ipaddr.getHostAddress() + " -- " + e;
        } 
        finally 
        {
            try 
            {
                if ( out!=null) out.close();
                if ( in != null) in.close();
                if ( sock != null) sock.close();
            } 
            catch (Exception e){}
        }
        System.out.println("Returning...");
        return Boolean.TRUE;
    }

    private String setAnswer(String answer){
        if (answer.equalsIgnoreCase("TXT")){
            this.isTXT = true;
            this.isXML = false;
        }
        else if (answer.equalsIgnoreCase("XML")){
            this.isTXT = false;
            this.isXML = true;
        }
        else if (answer.equalsIgnoreCase("JSON")){
            this.isTXT = false;
            this.isXML = false;
        }
        else return "BAD";

        extendedMessageFormat = answer;
        return "OK";
    }

    private void checkAvailableData(String type, int size){
        int count = albumSize + photoSize + videoSize;

        if (type.equalsIgnoreCase("IsAlbums")) albumSize = size;
        else if (type.equalsIgnoreCase("IsPictureFile")) photoSize = size;
        else if (type.equalsIgnoreCase("IsLibraryFile")) videoSize = size;

        if (count != albumSize + photoSize + videoSize) sendAvailableData();
    }

    private void sendAvailableData(){
        if (albumSize + photoSize + videoSize == 0) return;

        String data = albumSize + "," + photoSize + "," + videoSize;
        outgoingMessages.add(new Message(MessageType.TOTAL_AVAILABLE_DATA, data));
    }
}
