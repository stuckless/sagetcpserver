/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package sagetcpserver;

import gkusnick.sagetv.api.*;
import gkusnick.sagetv.api.MediaFileAPI.MediaFile;
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import sagetcpserver.messages.MessageType;
import sagetcpserver.messages.Message;
import sagetcpserver.utils.SageLogger;
/**
 *
 * @author Patrick Roy
 */
public class TCPServerPlayer implements Runnable{
    private static enum States {Play, Pause, Stop, None};
    private static final int LOW_RATE_CYCLES = 8;
    private static final int SERVER_UPDATE_RATE_MS = 250;
    private static final String FULL_SCREEN_MODE = "0";
    private static final String WINDOWED_MODE = "1";

    private ArrayList<SagePlayer> allPlayers = new ArrayList<SagePlayer>();
    private boolean currentMuteState = false;
    private float currentVolume = 0f;
    private Long currentDuration = 0l;
    private Long currentMediaTime = 0l;
    private MediaStore currentMediaFile = null; // Need to keep in sync
    private SageLogger logger = null;
    private ServerSocket server = null;
    private States currentState = States.None;
    private String currentScreenMode = "";
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> check;

    public ClientType clientType = ClientType.None;
    public int Port = 9260;
    public String uiContext;
    public String IP;


    public TCPServerPlayer(String context, int port, String ip, boolean needReboot, ClientType newType){
        logger = new SageLogger("Player:" + port);
        logger.Message("TCPServerPlayer: " + context + ", port = " + port + ",ip = " + ip);
        this.clientType = newType;
        this.IP = ip;
        this.Port = port;
        this.uiContext = context;
        //sageApi = ip.equalsIgnoreCase("127.0.0.1") ? API.apiLocalUI : new API(this.uiContext);
        if (needReboot){
            if (newType == ClientType.HD100) SagePlayer.reboot(this.uiContext, newType, ip);
            else SagePlayer.powerOn(this.uiContext, newType, ip);
        }
    }
    
    public void run() {
        logger.Message("Starting a player on socket " + this.Port);
        try {
            Socket client;
            this.server = new ServerSocket(this.Port);
            this.server.setReuseAddress(true);

            while (true){
                client = this.server.accept();
                logger.Debug("New player " + this.allPlayers.size());
                client.setSoTimeout (SERVER_UPDATE_RATE_MS);
                this.allPlayers.add(new SagePlayer(client, this.uiContext, this.currentMediaFile, this.clientType, this.IP));

                Thread thread = new Thread(this.allPlayers.get(this.allPlayers.size() - 1));
                thread.start();

                if (this.check == null || this.check.isDone()){
                    this.check = this.scheduler.scheduleAtFixedRate(new Runnable() {
                        public void run() {
                            getOutgoingData();
                            }}, SERVER_UPDATE_RATE_MS, SERVER_UPDATE_RATE_MS, TimeUnit.MILLISECONDS);
                }
            }
        }
        catch (IOException e) {
            logger.Error(e);
            // System.exit(1);
            shutdown();
        }
    }

    public void shutdown(){
        for (SagePlayer player : allPlayers){
        player.shutdown();
        allPlayers.remove(player);
    }
}

    public void sendMessage(Message message){
        for (SagePlayer player : allPlayers){
            if (player.client != null) player.addOutgoingMessages(message);
            else allPlayers.remove(player);
        }
    }

    public void sendPlayback(MediaFile mf, String type){
        logger.Message("Playback: " + type);
        if (type.equalsIgnoreCase("Finish") || type.equalsIgnoreCase("Stop")){
            currentState = States.Stop;
            currentMediaFile = null;
            sendMessage(new Message(MessageType.CLEAR_CURRENT));
            sendMessage(new Message(MessageType.PLAY_MODE, "Stop"));
        }
        else{
            API sageApi = this.uiContext == null ? API.apiLocalUI : new API(this.uiContext);
            changeStates(States.Play, mf, sageApi.mediaPlayerAPI);
        }
    }

     /** Get the outgoing data for the automated processing.
      * The initial connect of a new client needs to be treated differently,
      * maybe need to keep more things locally, or would currentMediaFileID
      * be enough for the init? */
    public void getOutgoingData() {
        //logger.Debug("Get outgoing messages...");
        if (allPlayers.isEmpty()){
            //logger.Debug("Nothing to see, stopping the polling...");
            this.check.cancel(true);
            return;
        }
        else{
            for (SagePlayer player : allPlayers){
                if (player.pingCount > LOW_RATE_CYCLES) player.send(new Message(MessageType.PLAY_MODE, currentState.toString()));
                else player.pingCount++;
            }
        }
        
        try {
            API sageApi = this.uiContext == null ? API.apiLocalUI : new API(this.uiContext);

            States oldSate = currentState;
            currentState = getNewState(sageApi.mediaPlayerAPI);

            if (oldSate != currentState)
                sendMessage(new Message(MessageType.PLAY_MODE, currentState.toString()));

            // All high rate data here
            switch (currentState) {
                case Play:
                    long newMediaTime = sageApi.mediaPlayerAPI.GetMediaTime() / 1000;

                    if (newMediaTime - currentMediaTime != 0l) {
                        currentMediaTime = newMediaTime;
                        sendMessage(new Message(MessageType.CURRENT_TIME, currentMediaTime.toString()));
                    }

                    // break intentionally omitted
                case Pause:
                    if (currentMediaFile.getIsLive()) {
                        // If the file playing is live, that means that the duration will
                        // continue to change as it is being watched (and recorded)
                        long currentDurLong = sageApi.mediaPlayerAPI.GetMediaDuration() / 1000;

                        if (currentDurLong - currentDuration != 0l) {
                            currentDuration = currentDurLong;
                            sendMessage(new Message(MessageType.CURRENT_DURATION, currentDuration.toString()));
                        }
                    }
                    break;
                default:
                    // Stop case, which we do nothing for
                    break;
            }

            // Check the volume
            if (sageApi.mediaPlayerAPI.GetVolume() != currentVolume) {
                currentVolume = sageApi.mediaPlayerAPI.GetVolume();
                int volume = (int)(100f * currentVolume);
                sendMessage(new Message(MessageType.VOLUME, String.valueOf(volume)));
            }

            // Check screen mode
            try{
                if (sageApi.global.IsFullScreen()) {
                    if (!currentScreenMode.equalsIgnoreCase(FULL_SCREEN_MODE)) {
                        currentScreenMode = FULL_SCREEN_MODE;
                        sendMessage(new Message(MessageType.WINDOWING_MODE, currentScreenMode));

                        if (clientType == ClientType.Hardware || clientType == ClientType.HDExtender){
                            if (sageApi.global.GetRemoteUIType().equalsIgnoreCase(sageApi.utility.LocalizeString("SD Media Extender")))
                                clientType = ClientType.MVP;
                            else if (sageApi.global.GetRemoteUIType().equalsIgnoreCase(sageApi.utility.LocalizeString("HD Media Extender")))
                                clientType = ClientType.HD100;
                            else if (sageApi.global.GetRemoteUIType().equalsIgnoreCase(sageApi.utility.LocalizeString("HD Media Player"))){
                                clientType = ClientType.HD300;
                                for (String option : sageApi.configuration.GetAudioOutputOptions()){
                                    logger.Debug("Audio option:" + option);
                                    if (option.equalsIgnoreCase("HDMIHBR")){
                                        clientType = ClientType.HD200;
                                        break;
                                    }
                                }
                            }

                            API.apiNullUI.configuration.SetProperty(StartServers.OPT_PREFIX_NAME + this.uiContext + "/type", clientType.name());
                            for (SagePlayer player : allPlayers) player.clientType = clientType;
                        }
                    }
                }
                else  {
                    if (!currentScreenMode.equalsIgnoreCase(WINDOWED_MODE)) {
                        currentScreenMode = WINDOWED_MODE;
                        sendMessage(new Message(MessageType.WINDOWING_MODE, currentScreenMode));
                    }
                }
            } catch (Exception ex){
                if (!currentScreenMode.equalsIgnoreCase(WINDOWED_MODE)) {
                    currentScreenMode = WINDOWED_MODE;
                    sendMessage(new Message(MessageType.WINDOWING_MODE, currentScreenMode));
                }
            }

            // Check mute state
            if (sageApi.mediaPlayerAPI.IsMuted() != currentMuteState) {
                currentMuteState = sageApi.mediaPlayerAPI.IsMuted();
                sendMessage(new Message(MessageType.MUTE, String.valueOf(currentMuteState)));
            }
        }
        catch (Exception ex) {
            logger.Debug(ex.getMessage());
        }
    }

    private States getNewState(MediaPlayerAPI sageApiMediaPlayer) {
        if (sageApiMediaPlayer.IsPlaying()) return States.Play;
        else if (sageApiMediaPlayer.HasMediaFile()) return States.Pause;
        else return States.Stop;
    }

    /** Change the state of the play mode */
    private void changeStates(States newState, MediaFile mf, MediaPlayerAPI sageApiMediaPlayer) {
        logger.Debug("[ChangeStates] About to process");
        try {
            currentMediaFile = new MediaStore(mf, sageApiMediaPlayer.IsCurrentMediaFileRecording());

            for (SagePlayer player : allPlayers) player.currentMediaFile = currentMediaFile;

            String episode = currentMediaFile.getEpisode();
            String title = currentMediaFile.getTitle();
            long start = currentMediaFile.getStartTime();
            long end = currentMediaFile.getEndTime();
            String duration = currentMediaFile.getDurationStr();
            String genre = currentMediaFile.getGenre();
            String year = currentMediaFile.getYear();

            if (start == end) {
                start = sageApiMediaPlayer.GetAvailableSeekingStart()/1000;
                end = sageApiMediaPlayer.GetAvailableSeekingEnd()/1000;
                duration = String.valueOf(end - start);
            }

            sendMessage(new Message(MessageType.CURRENT_START_TIME, String.valueOf(start)));
            sendMessage(new Message(MessageType.CURRENT_END_TIME, String.valueOf(end)));

            if (currentMediaFile.isMusicFile()) {
                AlbumAPI.Album album = currentMediaFile.getAlbum();
                String artist = currentMediaFile.getShow().GetPeopleInShowInRoles(new String[] {"Artist", "Artiste"});
                String albumName = album.GetAlbumName();
                String category = (genre.isEmpty() ? album.GetAlbumGenre() : genre);
                if (!albumName.isEmpty()) title = albumName + " - " + title;

                sendMessage(new Message(MessageType.CURRENT_ARTIST,
                        (artist.isEmpty() ? album.GetAlbumArtist() : artist)));
                sendMessage(new Message(MessageType.CURRENT_CATEGORY, category));
                sendMessage(new Message(MessageType.CURRENT_TYPE, "Audio"));
            }
            else {
                String actors = currentMediaFile.getShow().GetPeopleInShowInRoles(new String[]
                        {"Actor", "Acteur", "Guest", "Invité", "Special guest", "Invité spécial"});
                String channel = currentMediaFile.getChannel();
                if (!channel.isEmpty()){
                    sendMessage(new Message(MessageType.CURRENT_CHANNEL, channel));
                    sendMessage(new Message(MessageType.CURRENT_STATION_NAME,
                            channel + (currentMediaFile.getAiring().GetChannel() != null ? " " +
                            currentMediaFile.getAiring().GetChannel().GetChannelDescription() : "")));
                }
                sendMessage(new Message(MessageType.CURRENT_CATEGORY, genre));
                sendMessage(new Message(MessageType.CURRENT_ACTORS, actors));

                if (currentMediaFile.isDvd())
                    sendMessage(new Message(MessageType.CURRENT_TYPE, "DVD"));
                else if (currentMediaFile.getIsLive())
                    sendMessage(new Message(MessageType.CURRENT_TYPE, "Live TV"));
                else if (currentMediaFile.getIsTvFile())
                    sendMessage(new Message(MessageType.CURRENT_TYPE, "TV"));
                else sendMessage(new Message(MessageType.CURRENT_TYPE, "Video"));
            }

            sendMessage(new Message(MessageType.CURRENT_TITLE, title));
            sendMessage(new Message(MessageType.CURRENT_DURATION, duration));
            sendMessage(new Message(MessageType.CURRENT_DESC, currentMediaFile.getDescription()));
            sendMessage(new Message(MessageType.CURRENT_YEAR, (year.isEmpty() ? "0" : year)));
            if (!episode.equalsIgnoreCase(title) || currentMediaFile.isMusicFile())
                sendMessage(new Message(MessageType.CURRENT_EPISODE, episode));
            sendMessage(new Message(MessageType.CURRENT_ID, String.valueOf(currentMediaFile.getMediaFileId())));
        }
        catch (Exception ex) {
            logger.Debug(ex.getMessage());
        }

        currentState = newState;
        sendMessage(new Message(MessageType.PLAY_MODE, currentState.toString()));
    }
 }
