package sagetcpserver.utils;

import gkusnick.sagetv.api.*;
import java.io.File;

/**
 * Handles the creation of the JSON strings.
 * 
 * @author Patrick Roy = Fonceur07@yahoo.ca
 */
public class JSONUtil {
   
   public JSONUtil() {}

   /** Add a JSON element. */
   public static String addJSONElement(String name, boolean value) {
       return "\"" + name + "\": " + value + ", ";
   }
   
   /** Add a JSON element. */
   public static String addJSONElement(String name, Integer value) {
       return "\"" + name + "\": " + value + ", ";
   }

   /** Add a JSON element. */
   public static String addJSONElement(String name, Long value) {
       return "\"" + name + "\": " + value + ", ";
   }

   /** Add a JSON element. */
   public static String addJSONElement(String name, String value) {
       return addJSONElement(name, value, false);
   }

   /** Add a JSON element. */
   public static String addJSONElement(String name, String value, boolean isLast) {
       if (value == null || name.trim().isEmpty() || value.trim().isEmpty()) return "";
       return "\"" + name + "\": \"" + formatForJSON(value) + (isLast ? "\"" :  "\", ");
   }

   /** Add a JSON element. */
   public static String addJSONElement2(String name, String value) {
       if (value == null || name.trim().isEmpty() || value.trim().isEmpty()) return "";
       return "\"" + name + "\": \"" + formatForJSON(value, false) + "\", ";
   }

   /** Add a JSON element. */
   public static String addJSONElement3(String name, String value, boolean isLast) {
       if (value == null || name.trim().isEmpty() || value.trim().isEmpty()) return "";
       return "\"" + name + "\": \"" + formatForJSON(value, false) + (isLast ? "\"" :  "\", ");
   }

   /** Replace special characters by their JSON version.
    *  Make sure the first letter is in upper case */
   public static String formatForJSON(String value) {
       return formatForJSON(value, true);
   }

   /** Replace special characters by their JSON version.
    *  Make sure the first letter is in upper case */
   public static String formatForJSON(String value, boolean capitalize) {
       String newFormat = value.replaceAll("[\\x00-\\x1f]", " ").replace("\\", "\\\\").replace("\"", "")
               .replace("'", "\'");//.replace("\n", " ").replace("\r", " ").replace((char)0x0E, ' '); // What about !

       if (!capitalize) return newFormat;
       else return newFormat.substring(0, 1).toUpperCase() + newFormat.substring(1).toLowerCase();
   }


   /** Create an JSON string from aa Album. */
   public static String createJSONAlbum(AlbumAPI.Album album) {
        StringBuilder msgToSend = new StringBuilder("{");
        String title = album.GetAlbumName();

        msgToSend.append(addJSONElement("Title", (title.isEmpty() ? "Unknown" : 
            (title.length() > 1 ? (title.substring(0, 1).toUpperCase() + title.substring(1)) : title) )));
        msgToSend.append(addJSONElement("Artist", album.GetAlbumArtist()));
        msgToSend.append(addJSONElement("Genre", album.GetAlbumGenre()));
        String yearText = album.GetAlbumYear();
        if (!yearText.isEmpty()){
            try{
                int year = Integer.valueOf(yearText.split("'")[0]);
                msgToSend.append(addJSONElement("Year", year));
            }
            catch (Exception e) {e.printStackTrace();}
        }
        msgToSend.append(addJSONElement("Tracks", album.GetNumberOfTracks()));
        msgToSend.append("\"Songs\": [");

        return msgToSend.toString();
    }

   /** Create an JSON string from a Channel. */
   public static String createJSONChannel(ChannelAPI.Channel channel, String lineup) {
        String number = channel.GetChannelNumberForLineup(lineup);
        
        if (!number.isEmpty() && channel.IsChannelViewableOnLineup(lineup)) {
            Integer stationID = channel.GetStationID();
            String desc = channel.GetChannelDescription();
            String name = channel.GetChannelName();
            String network = channel.GetChannelNetwork();
            StringBuilder msgToSend = new StringBuilder("{");

            msgToSend.append(addJSONElement("ChannelID", stationID));
            msgToSend.append(addJSONElement2("Name", name));
            msgToSend.append(addJSONElement2("Description", desc));
            msgToSend.append(addJSONElement2("Network", network));
            msgToSend.append(addJSONElement2("Channel", number));
            msgToSend.insert(msgToSend.length() - 2, "}");

            return msgToSend.toString();
        }

        return "";
    }

   public static String createJSONButton(String button){
       String[] parts = button.split(",", 2);
       StringBuilder msgToSend = new StringBuilder("{");
       msgToSend.append(addJSONElement2("Command", parts[1]));
       msgToSend.append(addJSONElement3("Name", parts[0], true));
       msgToSend.append("}");

       return msgToSend.toString();
   }

   public static String createJSONClient(String name, int port, String id){
       StringBuilder msgToSend = new StringBuilder("{");
       msgToSend.append(addJSONElement2("Name", name));
       msgToSend.append(addJSONElement("Port", port));
       msgToSend.append(addJSONElement("ID", id));
       msgToSend.insert(msgToSend.length() - 2, "}");

       return msgToSend.toString();
   }

   /** Create an JSON string from an Airing. */
   public static String createJSONPlaylist(AiringAPI.Airing air, Integer ID,
           ShowAPI sageApiShow, boolean isLimited) {
       return createJSONString(air, ID, "", sageApiShow, "", isLimited, "IsTVFile", "");
   }

   /** Create an JSON string from an Airing. */
   public static String createJSONString(AiringAPI.Airing air, Integer ID,
           ShowAPI sageApiShow, boolean isLimited) {
       return createJSONString(air, ID, "", sageApiShow, "", isLimited, "IsTVFile", "");
   }
   
   /** Create an JSON string from an Airing. */
   public static String createJSONString(AiringAPI.Airing air, Integer ID, String type, ShowAPI sageApiShow,
           String modifier, boolean isLimited, String fileType, String filename) {
       try
       {
        ShowAPI.Show show = air.GetShow();
        ChannelAPI.Channel channel;
        Integer stationID;
        Integer rescale = 100000;
        Long duration, originalAirDate;
        String title = air.GetAiringTitle();
        Long endTime = air.GetAiringEndTime()/rescale;
        Long startTime = air.GetAiringStartTime()/rescale;
        String actors = "", credits = "", expandedRatings = "";
        String externalID = "", rating = "";
        StringBuilder msgToSend = new StringBuilder("{");
        String episode = show.GetShowEpisode();
        boolean isWatched = air.IsWatched();
        boolean isMusicFile = fileType.equalsIgnoreCase("IsMusicFile");

        if (air.GetMediaFileForAiring() != null) {
            String tmpTitle = air.GetMediaFileForAiring().GetMediaFileMetadata("MediaTitle");

            if (tmpTitle != null && !tmpTitle.isEmpty()) title = tmpTitle;
        }

        if (startTime == endTime)
        {
            MediaFileAPI.MediaFile mf = air.GetMediaFileForAiring();
            endTime = startTime + mf.GetFileDuration();
        }
        
        msgToSend.append(addJSONElement("Modifier", modifier));
        msgToSend.append(addJSONElement("ID", ID));
        msgToSend.append(addJSONElement("Genre", show.GetShowCategory()));
        msgToSend.append(addJSONElement("StartTime", (startTime > 0 ? startTime : 0)));
        
        if (!fileType.equalsIgnoreCase("IsPictureFile"))
        {
            msgToSend.append(addJSONElement("EndTime", endTime));
            if (title == null || title.isEmpty())
                msgToSend.append(addJSONElement("Title", episode));
            else{
                msgToSend.append(addJSONElement("Title", title));
                if (!title.equalsIgnoreCase(episode) || isMusicFile)
                    msgToSend.append(addJSONElement("Episode", episode));
            }

            if (!isMusicFile)
            {
                if (isWatched) msgToSend.append(addJSONElement("IsWatched", isWatched));
                String description = show.GetShowDescription();
                int loc = description.indexOf("----");

                if (loc > 0) description = description.substring(0, loc - 2).trim();

                loc = description.indexOf("See more (warning");

                if (loc > 0) description = description.substring(0, loc).trim();
                else if (description.length() > 500){
                    loc = description.indexOf("Written by");
                    if (loc > 0) description = description.substring(0, loc).trim();
                }
                msgToSend.append(addJSONElement("Description", description));

                if (air.GetMediaFileForAiring() != null){
                    File file = new File(air.GetMediaFileForAiring().GetMediaFileRelativePath());
                    if (file.getParent() != null) msgToSend.append(
                            addJSONElement("Folder", file.getParent().replace('\\', '/')));//.toString().replace('\\', '/')));
                }
            }
        }
        else{
            msgToSend.append(addJSONElement("Title", episode));
            msgToSend.append(addJSONElement("Filename", 
                    air.GetMediaFileForAiring().GetSegmentFiles()[0].toString()));
            File file = new File(air.GetMediaFileForAiring().GetMediaFileRelativePath());
            if (file.getParent() != null) msgToSend.append(
                    addJSONElement("Folder", file.getParent().replace('\\', '/')));
            //MetaImage image = air.GetMediaFileForAiring().GetFullImage();
        }
        //if (!filename.isEmpty()) msgToSend.append(addJSONElement("FileName", filename));
        channel = air.GetChannel();
        stationID = channel.GetStationID();
        if (stationID > 0 && !type.equalsIgnoreCase("Programs"))
            msgToSend.append(addJSONElement("ChannelID", stationID));
        
        if (fileType.equalsIgnoreCase("IsTVFile"))
        {
            duration = show.GetShowDuration();
            originalAirDate = show.GetOriginalAiringDate();
            // Use actors[] to build a json array, same for artist...
//            actors = show.GetPeopleListInShowInRoles(new String[]
//                {"Actor", "Acteur", "Guest", "Invité", "Special guest", "Invité spécial"});
            actors = show.GetPeopleInShowInRoles(new String[] 
                {"Actor", "Acteur", "Guest", "Invité", "Special guest", "Invité spécial"});
            credits = show.GetPeopleInShowInRoles(new String[] 
                {"Director", "Producer", "Producteur", "Réalisateur", "Executive producer", "Producteur exécutif"});
            expandedRatings = show.GetShowExpandedRatings();
            externalID = show.GetShowExternalID();
            rating = show.GetShowRated();

            if (!externalID.isEmpty()) msgToSend.append(addJSONElement("EPGID", externalID));
            msgToSend.append(addJSONElement("Actors", actors));
            if (sageApiShow.IsShowFirstRun(air)) msgToSend.append(addJSONElement("FirstRun", true));
            if (originalAirDate > 0) msgToSend.append(addJSONElement("OriginalAirDate", originalAirDate/rescale));
            if (!rating.isEmpty()) msgToSend.append(addJSONElement("Rating", rating));
            if (!expandedRatings.isEmpty()) msgToSend.append(addJSONElement("ExpandedRatings", expandedRatings));
            String yearText = show.GetShowYear();
            if (!yearText.isEmpty()){
                try{
                    int year = Integer.valueOf(yearText.split("'")[0]);
                    msgToSend.append(addJSONElement("Year", year));
                }
                catch (Exception e) {e.printStackTrace();}
            }
            if (!isLimited)
            {
                if (duration > 0) msgToSend.append(addJSONElement("Duration", duration/1000));
                msgToSend.append(addJSONElement("Credits", credits));
                if (air.IsAiringHDTV()) msgToSend.append(addJSONElement("IsHD", true));
            }
        }
        
        msgToSend.insert(msgToSend.length() - 2, "}");

        return msgToSend.toString();
       }
       catch (Exception e) { return ""; }
    }
   
   /** Create an JSON song from an Airing. */
   public static String createJSONSong(AiringAPI.Airing air, Integer ID, ShowAPI sageApiShow,
           String albumArtist, String albumGenre, Integer index) {
       try
       {
        ShowAPI.Show show = air.GetShow();
        StringBuilder msgToSend = new StringBuilder("{");
        String artist = show.GetPeopleInShowInRoles(new String[] {"Artist", "Artiste"});
        long duration = (air.GetAiringEndTime() - air.GetAiringStartTime()) / 1000;
        String genre = show.GetShowCategory();
        String title = show.GetShowEpisode();
        //String filename = air.GetMediaFileForAiring().GetSegmentFiles()[0].toString();
        
        msgToSend.append(addJSONElement("Number", index));
        msgToSend.append(addJSONElement("ID", ID));
        msgToSend.append(addJSONElement("Title", (title.isEmpty() ? "Unknown" :
            (title.length() > 1 ? (title.substring(0, 1).toUpperCase() + title.substring(1)) : title) )));
        if (!artist.equalsIgnoreCase(albumArtist)) msgToSend.append(addJSONElement("Artist", artist));
        if (!genre.equalsIgnoreCase(albumGenre)) msgToSend.append(addJSONElement("Genre", genre));
        msgToSend.append(addJSONElement("Duration", duration));
        //msgToSend.append(addJSONElement("FileName", filename));
        msgToSend.insert(msgToSend.length() - 2, "}");

        return msgToSend.toString();
       }
       catch (Exception e) { return ""; }
    }

   /** Create an JSON string from a Favorite. */
   public static String createJSONString(FavoriteAPI.Favorite favorite,
           Integer ID, String type, String modifier) {

        boolean favFirstRuns = favorite.IsFirstRuns();
        boolean favReRuns = favorite.IsReRuns();
        String favChannel = favorite.GetFavoriteChannel();
        String favDesc = favorite.GetFavoriteDescription();
        String favTitle = favorite.GetFavoriteTitle();
        if (favChannel.isEmpty()) favChannel = "Any";
        if (favTitle.isEmpty()) favTitle = favDesc; // By keywords
        StringBuilder msgToSend = new StringBuilder("{");
        
        msgToSend.append(addJSONElement("Modifier", modifier));
        msgToSend.append(addJSONElement("ID", ID));
        msgToSend.append(addJSONElement("Title", favTitle));
        if (!favDesc.equalsIgnoreCase(favTitle))
            msgToSend.append(addJSONElement("Description", favDesc));
        msgToSend.append(addJSONElement("DisplayedChannel", favChannel));
        if (favFirstRuns) msgToSend.append(addJSONElement("FirstRun", favFirstRuns));
        if (favReRuns) msgToSend.append(addJSONElement("ReRun", favReRuns));
        msgToSend.insert(msgToSend.length() - 2, "}");

        return msgToSend.toString();
    }

   /** Create an JSON string from some text and ID. */
   public static String createJSONString(Integer ID, String modifier) {
        StringBuilder msgToSend = new StringBuilder("{");
        msgToSend.append(addJSONElement("Modifier", modifier));
        msgToSend.append(addJSONElement("ID", ID));
        msgToSend.insert(msgToSend.length() - 2, "}");

        return msgToSend.toString();
    }

      /** Create an JSON string from some text and ID. */
   public static String createJSONString(SystemMessageAPI.SystemMessage message) {
       Integer rescale = 100000;
        StringBuilder msgToSend = new StringBuilder("{");
        msgToSend.append(addJSONElement("Code", message.GetSystemMessageTypeCode()));
        msgToSend.append(addJSONElement("Level", message.GetSystemMessageLevel()));
        msgToSend.append(addJSONElement("StartTime", message.GetSystemMessageTime()/rescale));
        //msgToSend.append(addJSONElement("Type", message.GetSystemMessageTypeName()));
        msgToSend.append(addJSONElement("Description", message.GetSystemMessageString()));
        msgToSend.insert(msgToSend.length() - 2, "}");

        return msgToSend.toString();
    }

   public static String GetRelativePath(File[] importfolders, MediaFileAPI.MediaFile mf) {
        String mediafilepath = mf.GetMediaFileRelativePath();
        for (File impfold : importfolders) {
            String importpathname = impfold.getAbsolutePath();
            if (mediafilepath.startsWith(importpathname)) {
                String relativepath = mediafilepath.substring(importpathname.length() + 1);
                return relativepath;
            }
        }
        return mediafilepath;
    }
}
