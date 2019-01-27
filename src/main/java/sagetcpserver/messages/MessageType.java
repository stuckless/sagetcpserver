/*
 * MessageType.java
 *
 * Created on July 30, 2007, 11:43 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package sagetcpserver.messages;

import sagetcpserver.utils.SageLogger;

/**
 *
 * @author Rob
 */
public enum MessageType {
    BUTTON("BTN"),
    EXECUTE("EXE"),
    EXECUTE_ON_CLIENT("EXC"),
    WATCH_SELECTED_SHOW("WSS"),
    WATCH_SELECTED_IMAGE("WSI"),
    WATCH_SHOW_PATH("WSP"),
    VOLUME("VOL"),
    MUTE("MUT"),
    PLAY_MODE("PLM"),
    CURRENT_TITLE("CTT"),
    CURRENT_TYPE("CTY"),
    CURRENT_ID("CID"),
    CURRENT_EPISODE("CEP"),
    CURRENT_TIME("CTM"),
    CURRENT_DURATION("CDU"),
    CURRENT_DESC("CDE"),
    CURRENT_ARTIST("CAR"),
    CURRENT_YEAR("CYR"),
    CURRENT_CHANNEL("CHA"),
    CURRENT_STATION_NAME("CSN"),
    CURRENT_ACTORS("CAC"),
    CURRENT_CATEGORY("CUA"),
    SET_CHANNEL("SCA"),
    ALL_THE_LINEUPS("ATL"),
    LIST_OF_CLIENTS("LOC"),
    FAVORITE_SHOW_LIST("FSL"),
    CHANGE_FAVORITE_FREQUENCY("CFF"),
    DELETE_FAVORITE_SHOW("DFS"),
    MANUAL_RECORDINGS_LIST("MRL"),
    MUSIC_FILES_LIST("MFL"),
    OTHER_FILES_LIST("OFL"),
    PICTURE_FILES_LIST("PFL"),
    RECORDED_SHOW_LIST("RSL"),
    UPCOMING_EPISODES_LIST("UEL"),
    UPCOMING_RECORDINGS_LIST("URL"),
    WINDOWING_MODE("WIN"),
    CLEAR_CURRENT("CLR"),
    CURRENT_START_TIME("CST"),
    CURRENT_END_TIME("CET"),
    COMMAND("CMD"),
    EXTENDER_TELNET_COMMAND("XTC"),
    ALL_CHANNELS("ACH"),
    AIRINGS_ON_CHANNEL_AT_TIME("ACT"),
    MODE("MOD"),
    ANSWER("ANS"),
    RESET("RST"),
    INITIALIZE("INI"),
    VERSION("VER"),
    RECORD_A_SHOW("RAS"),
    VIDEO_DISK_SPACE("VDS"),
    ADD_TO_PLAYLIST("ATP"),
    ADD_ALBUM_TO_PLAYLIST("AAP"),
    CHANGE_THE_PLAYLIST("CTP"),
    CLEAR_THE_PLAYLIST("CLP"),
    DELETE_THE_PLAYLIST("DTP"),
    DELETE_PLAYLIST_ITEM("DPI"),
    GET_THE_PLAYLIST("GTP"),
    LIST_THE_PLAYLISTS("LTP"),
    START_PLAYLIST("SPL"),
    SEARCH_BY_TITLE("SBT"),
    MATCH_EXACT_TITLE("MET"),
    NUM_CURRENTLY_RECORDING_FILES("CRF"),
    LAST_EPG_DOWNLOAD("LED"),
    NEXT_EPG_DOWNLOAD("NED"),
    DELETE_SHOW("DEL"),
    MAXIMUM_ITEMS("MAX"),
    STREAM_VLC("SVL"),
    STREAM_VLC_ALBUM("SVA"),
    STREAM_VLC_PROFILE("SVP"),
    PROTOCOL_STREAMING_TYPE("PST"),
    STREAMING_PORT("SPT"),
    TOTAL_AVAILABLE_DATA("TAD"),
    KEY("KEY"),
    RESET_SYSTEM_MESSAGE("RSM"),
    SCHEDULING_CONFLICT_LIST("SCL"),
    CATEGORIES_FOR_ALBUMS("CFA"),
    CATEGORIES_FOR_OTHERS("CFO"),
    CATEGORIES_FOR_TV("CFT"),
    SYSTEM_MESSAGE("SMS"),
    JETTY_HTTP_PORT("JHP");

    static SageLogger logger = new SageLogger("MessageType");
    
    private String prefix;
    
    MessageType(String s)
    {
        prefix = s;
    }
    
    public String getPrefix()
    {
        return prefix;
    }
    
    public static MessageType fromPrefix(String s)
    {
        for (MessageType m : MessageType.values())
        {
            if (s.equals(m.prefix))
            {
                logger.Debug("Found match with " + s);
                return m;
                
            }
        }
        
        return null;
    }

    @Override
    public String toString() {
          
        return prefix;
    }
}


