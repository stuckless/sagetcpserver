/*
 * Utility.java
 *
 * Created on July 21, 2007, 11:44 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package sagetcpserver.utils;

import gkusnick.sagetv.api.*;

/**
 * Handles the creation of the XML strings.
 * 
 * @author Patrick Roy = Fonceur07@yahoo.ca
 */
public class XMLUtil {
   
   public XMLUtil() {}

   /** Add an XML element. */
   public static String addXMLElement(String name, boolean value)
   {
       if (value) return addXMLElement(name, "True");
       else return "";
   }
   
   /** Add an XML element. */
   public static String addXMLElement(String name, int value)
   {
       return addXMLElement(name, String.valueOf(value));
   }

   /** Add an XML element. */
   public static String addXMLElement(String name, long value)
   {
       return addXMLElement(name, String.valueOf(value));
   }

   /** Add an XML element. */
   public static String addXMLElement(String name, String value)
   {
       if (value == null || name.trim().isEmpty() || value.trim().isEmpty()) return "";
       return "<" + name + ">" + formatForXML(value) + "</" + name + ">";
   }

   /** Replace special characters by their XML version. */
   public static String formatForXML(String value)
   {
       return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
               .replace("\"", "&quot;").replace("'", "&apos;").replaceAll("[\\x00-\\x1f]", " ");
   }
   
   /** Create an XML string from aa Album. */
   public static String createXMLAlbum(AlbumAPI.Album album)
    {
        StringBuilder msgToSend =
                new StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?><Collection><List Name=\"Music\"><Album>");
        String title = album.GetAlbumName();

        msgToSend.append(addXMLElement("Title", (title.isEmpty() ? "Unknown" : title)));
        msgToSend.append(addXMLElement("Artist", album.GetAlbumArtist()));
        msgToSend.append(addXMLElement("Genre", album.GetAlbumGenre()));
        msgToSend.append(addXMLElement("Year", album.GetAlbumYear()));
        msgToSend.append(addXMLElement("Tracks", String.valueOf(album.GetNumberOfTracks())));

        return msgToSend.toString();
    }

   /** Create an XML string from a Channel. */
   public static String createXMLChannel(ChannelAPI.Channel channel, String lineup)
    {
        boolean isViewable = channel.IsChannelViewableOnLineup(lineup);
        String desc = channel.GetChannelDescription();
        String name = channel.GetChannelName();
        String network = channel.GetChannelNetwork();
        String number = channel.GetChannelNumberForLineup(lineup);
        Integer stationID = channel.GetStationID();
        StringBuilder msgToSend = new StringBuilder();
        
        if (!number.isEmpty() && isViewable)
        {
            msgToSend.append("<Channel>");
            msgToSend.append(addXMLElement("ID", stationID.toString()));
            msgToSend.append(addXMLElement("Name", name));
            msgToSend.append(addXMLElement("Desc", desc));
            msgToSend.append(addXMLElement("Network", network));
            msgToSend.append(addXMLElement("Number", number));
            msgToSend.append("</Channel>");
        }

        return msgToSend.toString();
    }

   /** Create an XML string for a Client. */
   public static String createXMLClient(String name, int port, String id){
       StringBuilder msgToSend = new StringBuilder("<Client>");
       msgToSend.append(addXMLElement("Name", name));
       msgToSend.append(addXMLElement("Port", port));
       msgToSend.append(addXMLElement("ID", id));
       msgToSend.append("</Client>");

       return msgToSend.toString();
    }


   /** Create an XML string from an Airing. */
   public static String createXMLString(AiringAPI.Airing air, Integer ID, 
           String type, ShowAPI sageApiShow, boolean isLimited)
   {
       return createXMLString(air, ID, type, sageApiShow, "", "", isLimited, "IsTVFile", "");
   }
   
   /** Create an XML string from an Airing. */
   public static String createXMLString(AiringAPI.Airing air, Integer ID, String type, ShowAPI sageApiShow, 
           String modifier, String attribute, boolean isLimited, String fileType, String filename)
    {
       try
       {
        ShowAPI.Show show = air.GetShow();
        ChannelAPI.Channel channel;
        Integer stationID;
        Long duration, originalAirDate;
        String actors = "", channelNumber = "", credits = "", expandedRatings = "";
        String externalID = "", rating = "", stationName = "", year = "";
        StringBuilder msgToSend = new StringBuilder();
        String title = air.GetAiringTitle();
        String episode = show.GetShowEpisode();
        Integer rescale = 100000;
        Long endTime = air.GetAiringEndTime()/rescale;
        Long startTime = air.GetAiringStartTime()/rescale;
        boolean isWatched = air.IsWatched();
        boolean isMusicFile = fileType.equalsIgnoreCase("IsMusicFile");
        
        if (startTime == endTime)
        {
            MediaFileAPI.MediaFile mf = air.GetMediaFileForAiring();
            endTime = startTime + mf.GetFileDuration();
        }
        
        msgToSend.append("<").append(type).append(attribute).append(">");
        msgToSend.append(addXMLElement("Modifier", modifier));
        msgToSend.append(addXMLElement("ID", ID.toString()));
        msgToSend.append(addXMLElement("Title", title));
        if (!title.equalsIgnoreCase(episode) || isMusicFile) msgToSend.append(addXMLElement("Episode", episode));
        msgToSend.append(addXMLElement("Category", show.GetShowCategory()));
        msgToSend.append(addXMLElement("StartTime", (startTime > 0 ? startTime.toString() : "0")));
        if (!fileType.equalsIgnoreCase("IsPictureFile"))
        {
            msgToSend.append(addXMLElement("EndTime", endTime.toString()));

            if (!isMusicFile)
            {
                msgToSend.append(addXMLElement("IsWatched", isWatched));
                msgToSend.append(addXMLElement("Description", show.GetShowDescription()));
            }
        }
        if (!filename.isEmpty()) msgToSend.append(addXMLElement("FileName", filename));
        
        if (fileType.equalsIgnoreCase("IsTVFile"))
        {
            channel = air.GetChannel();
            stationID = channel.GetStationID();
            duration = show.GetShowDuration();
            originalAirDate = show.GetOriginalAiringDate();
            actors = show.GetPeopleInShowInRoles(new String[] 
                {"Actor", "Acteur", "Guest", "Invité", "Special guest", "Invité spécial"});
            channelNumber = channel.GetChannelNumber();
            credits = show.GetPeopleInShowInRoles(new String[] 
                {"Director", "Producer", "Producteur", "Réalisateur", "Executive producer", "Producteur exécutif"});
            expandedRatings = show.GetShowExpandedRatings();
            externalID = show.GetShowExternalID();
            rating = show.GetShowRated();
            stationName = channel.GetChannelDescription();
            year = show.GetShowYear();

            if (type.equalsIgnoreCase("Program")) msgToSend.append(addXMLElement("EPGID", externalID));
            else msgToSend.append(addXMLElement("StationID", stationID.toString()));
            msgToSend.append(addXMLElement("Actors", actors));
            msgToSend.append(addXMLElement("FirstRun", sageApiShow.IsShowFirstRun(air)));
            if (originalAirDate > 0) msgToSend.append(addXMLElement("OriginalAirDate", String.valueOf(originalAirDate/rescale)));
            if (!rating.isEmpty()) msgToSend.append(addXMLElement("Rating", rating));
            if (!expandedRatings.isEmpty()) msgToSend.append(addXMLElement("ExpandedRatings", expandedRatings));
            if (!year.isEmpty()) msgToSend.append(addXMLElement("MovieYear", year));
            if (!isLimited)
            {
                if (!channelNumber.isEmpty()) msgToSend.append(addXMLElement("Channel", channelNumber));
                if (!stationName.isEmpty()) msgToSend.append(addXMLElement("StationName", stationName));
                if (duration > 0) msgToSend.append(addXMLElement("Duration", duration.toString()));
                msgToSend.append(addXMLElement("Credits", credits));
                msgToSend.append(addXMLElement("IsHD", air.IsAiringHDTV()));
            }
        }
        else if (fileType.equalsIgnoreCase("IsManual"))
        {
            channel = air.GetChannel();
            channelNumber = channel.GetChannelNumber();
            if (!channelNumber.isEmpty()) msgToSend.append(addXMLElement("Channel", channelNumber));
        }
        
        msgToSend.append("</").append(type).append(">");

        return msgToSend.toString();
       }
       catch (Exception e) { return ""; }
    }
   
   /** Create an XML song from an Airing. */
   public static String createXMLSong(AiringAPI.Airing air, Integer ID, ShowAPI sageApiShow, 
           String albumArtist, String albumGenre, Integer index)
    {
       try
       {
        ShowAPI.Show show = air.GetShow();
        StringBuilder msgToSend = new StringBuilder();
        String artist = show.GetPeopleInShowInRoles(new String[] {"Artist", "Artiste"});
        String duration = String.valueOf((air.GetAiringEndTime() - air.GetAiringStartTime()) / 1000);
        String genre = show.GetShowCategory();
        String title = show.GetShowEpisode();
        String filename = air.GetMediaFileForAiring().GetSegmentFiles()[0].toString();
        
        msgToSend.append("<Song num = \"").append(index).append("\">");
        msgToSend.append(addXMLElement("ID", ID.toString()));
        msgToSend.append(addXMLElement("Title", title));
        if (!artist.equalsIgnoreCase(albumArtist)) msgToSend.append(addXMLElement("Artist", artist));
        if (!genre.equalsIgnoreCase(albumGenre)) msgToSend.append(addXMLElement("Genre", genre));
        msgToSend.append(addXMLElement("Duration", duration));
        msgToSend.append(addXMLElement("FileName", filename));
        msgToSend.append("</Song>");

        return msgToSend.toString();
       }
       catch (Exception e) { return ""; }
    }

   /** Create an XML string from a Favorite. */
   public static String createXMLString(FavoriteAPI.Favorite favorite, Integer ID, String type, String modifier)
    {

        boolean favFirstRuns = favorite.IsFirstRuns();
        boolean favReRuns = favorite.IsReRuns();
        String favChannel = favorite.GetFavoriteChannel();
        String favDesc = favorite.GetFavoriteDescription();
        String favTitle = favorite.GetFavoriteTitle();
        if (favChannel.isEmpty()) favChannel = "Any";
        StringBuilder msgToSend = new StringBuilder();
        
        msgToSend.append("<").append(type).append(">");
        msgToSend.append(addXMLElement("Modifier", modifier));
        msgToSend.append(addXMLElement("ID", ID.toString()));
        msgToSend.append(addXMLElement("Title", favTitle));
        msgToSend.append(addXMLElement("Description", favDesc));
        msgToSend.append(addXMLElement("Channel", favChannel));
        msgToSend.append(addXMLElement("FirstRuns", favFirstRuns));
        msgToSend.append(addXMLElement("ReRuns", favReRuns));
        msgToSend.append("</").append(type).append(">");

        return msgToSend.toString();
    }

   /** Create an XML string from some text and ID. */
   public static String createXMLString(Integer ID, String type, String modifier)
    {
        StringBuilder msgToSend = new StringBuilder("<").append(type).append(">");
        msgToSend.append(addXMLElement("Modifier", modifier));
        msgToSend.append(addXMLElement("ID", ID.toString()));
        msgToSend.append("</").append(type).append(">");

        return msgToSend.toString();
    }

   /** Create an XML string from some text and ID. */
   public static String createXMLString(SystemMessageAPI.SystemMessage message, String type){
       Integer rescale = 100000;
        StringBuilder msgToSend = new StringBuilder("<" + type + ">");
        msgToSend.append(addXMLElement("Code", message.GetSystemMessageTypeCode()));
        msgToSend.append(addXMLElement("Level", message.GetSystemMessageLevel()));
        msgToSend.append(addXMLElement("StartTime", message.GetSystemMessageTime()/rescale));
        //msgToSend.append(addXMLElement("Type", message.GetSystemMessageTypeName()));
        msgToSend.append(addXMLElement("Description", message.GetSystemMessageString()));
        msgToSend.append("</").append(type).append(">");

        return msgToSend.toString();
   }
}
