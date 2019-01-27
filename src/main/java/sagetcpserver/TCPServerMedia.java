/*
 * To change this template, choose Tools | Templates
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
public class TCPServerMedia implements Runnable{
    private static final int SERVER_UPDATE_RATE_MS = 250;
    private static final int LOW_RATE_CYCLES = 4;
    private static Integer numberOfRecordings = -1;

    public static ArrayList<Integer> allUpcomingRecordings = new ArrayList<Integer>();
    public static int albumSize = 0, photoSize = 0, videoSize = 0;

    private API sageApi = API.apiNullUI;
    private ArrayList<SageMedia> allMedia = new ArrayList<SageMedia>();
    private SageLogger logger = null;
    private ServerSocket server = null;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> check = null;

    public int Port = 9250;

    public TCPServerMedia(int port){
        logger = new SageLogger("Media:" + port);
        this.Port = port;
    }
    
    public void run() {
        logger.Message("Starting a media server on socket " + this.Port);
        try {
            Socket client;
            this.server = new ServerSocket(this.Port);
            this.server.setReuseAddress(true);

            while (true){
                client = this.server.accept();
                client.setSoTimeout (SERVER_UPDATE_RATE_MS);
                this.allMedia.add(new SageMedia(client));

                Thread thread = new Thread(this.allMedia.get(this.allMedia.size() - 1));
                thread.start();

                if (this.check == null || this.check.isDone()){
                    logger.Debug("[Run] Starting the loop");
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
        for (SageMedia media : allMedia){
            media.shutdown();
            allMedia.remove(media);
        }
    }

    public void sendMF(MediaFile mf, String type){
        logger.Message("Media file " + type + ": " + mf.GetMediaTitle());
        MessageType mt = null;

        if (mf.IsMusicFile() || mf.IsPictureFile()) return; // Don't handle it for now
        else if(mf.IsLibraryFile()) mt = MessageType.OTHER_FILES_LIST;
        else if(mf.IsTVFile()) mt = MessageType.RECORDED_SHOW_LIST;
        else return;

        ArrayList<Airing> addList = new ArrayList<Airing>();
        ArrayList<Integer> deleteList = new ArrayList<Integer>();

        if (type.equalsIgnoreCase("Add")) addList.add(mf.GetMediaFileAiring());
        else deleteList.add(mf.GetMediaFileID());

        for (SageMedia media : allMedia) media.sendAiringsList(addList, deleteList, mt);
    }

    public void sendMessage(Message message){
        for (SageMedia media : allMedia){
            if (media.client != null) media.addOutgoingMessages(message);
            else allMedia.remove(media);
        }
    }

    public void getOutgoingData() {
        if (allMedia.isEmpty()){
            logger.Debug("[OutGoingData] Stop the loop!");
            this.check.cancel(true);
            return;
        }
        else{
            for (SageMedia media : allMedia){
                if (media.pingCount > LOW_RATE_CYCLES) media.getVideoDiskSpace();
                else media.pingCount++;
            }
        }
    }

    /** Get the number of currently recording files */
    public void getNumberOfCurrentlyRecordingFiles() {
        MediaFileAPI.List allCurrentlyRecordingFiles = sageApi.global.GetCurrentlyRecordingMediaFiles();

        if (numberOfRecordings != allCurrentlyRecordingFiles.size()) {
            numberOfRecordings = allCurrentlyRecordingFiles.size();
            sendMessage(new Message(MessageType.NUM_CURRENTLY_RECORDING_FILES, numberOfRecordings.toString()));
        }
    }

    public void sendFavorite(Favorite favorite, String modifier){
        ArrayList<Favorite> newFavorites = new ArrayList<Favorite>();
        ArrayList<Favorite> modFavorites = new ArrayList<Favorite>();
        ArrayList<Integer> toDelete = new ArrayList<Integer>();

        if (modifier.equalsIgnoreCase("Add")) newFavorites.add(favorite);
        else if(modifier.equalsIgnoreCase("Del")) toDelete.add(favorite.GetFavoriteID());
        else modFavorites.add(favorite);

        for (SageMedia media : allMedia) media.sendFavorites(newFavorites, modFavorites, toDelete);
    }

    public void sendSystemMessage(SystemMessage message){
        ArrayList<SystemMessage> newMessages = new ArrayList<SystemMessage>();
        newMessages.add(message);

        for (SageMedia media : allMedia) media.sendSystemMessages(newMessages);
    }

    public void getConflicts() {
        if (allMedia.isEmpty()) return;

        AiringAPI.List allAirings = sageApi.global.GetAiringsThatWontBeRecorded(true);

        if (allAirings.isEmpty()) logger.Message("There are no recording conflict currently");
        else logger.Message("Found some recording conflict: " + allAirings.size());

        ArrayList<Airing> addList = new ArrayList<Airing>();
        for (AiringAPI.Airing airing : allAirings) addList.add(airing);

        for (SageMedia media : allMedia)
            media.sendAiringsList(addList, new ArrayList<Integer>(), MessageType.SCHEDULING_CONFLICT_LIST);
    }

    /**
    * Returns the list of Upcoming recordings, the format of the output is
    * dependant on the answer type (TXT|XML}.  Nothing is sent if there was
    * no changes since the last transmission.
    *
    * @return msgToSend String - The list of Upcoming recordings.
    */
    public void getUpcomingRecordingsList() {
        if (allMedia.isEmpty()) return;

        ArrayList<Integer> toAddList = new ArrayList<Integer>();
        ArrayList<Integer> toDeleteList = new ArrayList<Integer>();
        ArrayList<Airing> addList = new ArrayList<Airing>();
        logger.Debug("Finding all upcoming recordings...");
        AiringAPI.List upcomingRecordings = sageApi.global.GetScheduledRecordings();
        upcomingRecordings = upcomingRecordings.SortLexical(false, "GetAiringStartTime");
        String upcomingRecordingsListLength = String.valueOf(upcomingRecordings.size());
        logger.Debug(upcomingRecordingsListLength + " scheduled recordings found.");
        for (Integer recID : allUpcomingRecordings) toDeleteList.add(recID);

        for (Airing airing : upcomingRecordings) {
            if (!allUpcomingRecordings.contains(airing.GetAiringID())){
                toAddList.add(airing.GetAiringID());
                addList.add(airing);
            }
            else toDeleteList.remove(toDeleteList.indexOf(airing.GetAiringID()));
        }

        for (Integer toAdd : toAddList) allUpcomingRecordings.add(toAdd);
        for (Integer toDelete : toDeleteList) allUpcomingRecordings.remove(toDelete);

        if (toAddList.size() + toDeleteList.size() == 0) return;

        for (SageMedia media : allMedia)
            media.sendAiringsList(addList, toDeleteList, MessageType.UPCOMING_RECORDINGS_LIST);
    }
 }
