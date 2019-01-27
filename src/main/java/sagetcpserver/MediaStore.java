/*
 * MediaStore.java
 *
 * Created on July 28, 2007, 12:05 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package sagetcpserver;

import gkusnick.sagetv.api.AiringAPI;
import gkusnick.sagetv.api.AlbumAPI;
import gkusnick.sagetv.api.MediaFileAPI;
import gkusnick.sagetv.api.ShowAPI;

/**
 *
 * @author Rob
 */
public class MediaStore {
    
    private AiringAPI.Airing airing = null;
    private ShowAPI.Show show = null;
    private AlbumAPI.Album album = null;

    private boolean isDvd;
    private boolean isLive;
    private boolean isMusicFile;
    private boolean isTvFile;
    private int mediaFileId;
    private Long duration;
    private Long endTime;
    private Long startTime;
    private String channel;
    private String description;
    private String episode;
    private String genre;
    private String title;
    private String year;
    
    
    /** Creates a new instance of MediaStore */
    public MediaStore(MediaFileAPI.MediaFile mf, boolean isLiveRecording) {
        airing = mf.GetMediaFileAiring();
        show = airing.GetShow();
        title = mf.GetMediaTitle();
        isTvFile = mf.IsTVFile();        
        isLive = isLiveRecording;
        isDvd = mf.IsDVD();
        isMusicFile = mf.IsMusicFile();
        channel = airing.GetAiringChannelNumber();
        mediaFileId = mf.GetMediaFileID();
        duration = (Long)mf.GetFileDuration() / 1000; // convert duration from millisecs to seconds

        if (show != null) {
            description = show.GetShowDescription();
            episode = show.GetShowEpisode();
            genre = show.GetShowCategory();
            year = show.GetShowYear();
        }

        if(isMusicFile) {
            album = mf.GetAlbumForFile();
            year = album.GetAlbumYear();
        }
        
        if (description == null || description.length() == 0) {
            description = "None";
        }
        
        if (episode == null) episode = "";

        if (isLive) {
            // Convert start and end time from millisecs to seconds
            startTime = airing.GetAiringStartTime() / 1000; 
            endTime = airing.GetAiringEndTime() / 1000;
        }
        else {
            startTime = mf.GetFileStartTime() / 1000;
            endTime = mf.GetFileEndTime() / 1000;
        }
    }

    String getTitle() {
        return title;
    }
    
    String getDurationStr() {
        return duration.toString();
    }

    String getDescription() {
        return description;
    }

    String getEpisode() {
        return episode;
    }

    String getGenre() {
        return genre;
    }

    String getYear() {
        return year;
    }

    boolean getIsLive() {
        return isLive;
    }
    
    boolean getIsTvFile() {
        return isTvFile;
    }

    int getMediaFileId() {
        return mediaFileId;
    }

    Long getStartTime() {
        return startTime;
    }
    
    String getStartTimeStr() {
        return startTime.toString();
    }
    
    Long getEndTime() {
        return endTime;
    }

    String getEndTimeStr() {
        return endTime.toString();
    }

    boolean isDvd() {
        return isDvd;
    }

    boolean isMusicFile()  {
        return isMusicFile;
    }
    
    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public AiringAPI.Airing getAiring() {
        return airing;
    }

    public AlbumAPI.Album getAlbum() {
        return album;
    }

    public ShowAPI.Show getShow() {
        return show;
    }
}
