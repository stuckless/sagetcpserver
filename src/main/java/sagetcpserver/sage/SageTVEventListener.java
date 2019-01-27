/*
 * SageTVEventListener.java
 *
 * Created on March 5, 2010, 3:13 PM
 *
 * Copyright 2001-2010 SageTV, LLC. All rights reserved.
 */

package sage;

/**
 * Interface definition for implementation classes that listen for events from the SageTV core
 *
 * Variable types are in brackets[] after the var name unless they are the same as the var name itself.
 * List of known core events:
 *
 * MediaFileImported - vars: MediaFile
 * ImportingStarted
 * ImportingCompleted - vars: FullReindex[Boolean]
 * RecordingCompleted (called when a complete recording is done) - vars: MediaFile 
 * RecordingStarted (called when any kind of recording is started) - vars: MediaFile 
 * RecordingSegmentAdded (called an active recording transitions to a new file but would not have fired a RecordingStarted event) - vars: MediaFile 
 * RecordingStopped (called whenever a recording is stopped for any reason) - vars: MediaFile
 * AllPluginsLoaded
 * PluginStarted (called when a plugin is started by being explicitly enabled after startup, will not be called when all enabled plugins are started at startup) - vars: Plugin
 * PluginStopped (called when a plugin is stopped by being explicitly disabled before shutdown, will not be called when all enabled plugins are stopped at shutdown) - vars: Plugin
 * RecordingScheduleChanged
 * ConflictStatusChanged
 * SystemMessagePosted - vars: SystemMessage
 * EPGUpdateCompleted
 * MediaFileRemoved - vars: MediaFile, Reason[String; will be one of Diskspace, KeepAtMost, PartialOrUnwanted, VerifyFailed, User or ImportLost], UIContext[String, will be null or undefined if not from a 'User' reason]
 * PlaybackStopped (called when the file is closed) - vars: MediaFile, UIContext[String], Duration[Long], MediaTime[Long], ChapterNum[Integer], TitleNum[Integer]
 * PlaybackFinished (called at the EOF) - vars: MediaFile, UIContext[String], Duration[Long], MediaTime[Long], ChapterNum[Integer], TitleNum[Integer]
 * PlaybackStarted - vars: MediaFile, UIContext[String], Duration[Long], MediaTime[Long], ChapterNum[Integer], TitleNum[Integer]
 * FavoriteAdded - vars: Favorite
 * FavoriteModified - vars: Favorite
 * FavoriteRemoved - vars: Favorite
 * PlaylistAdded - vars: Playlist, UIContext[String]
 * PlaylistModified - vars: Playlist, UIContext[String]
 * PlaylistRemoved - vars: Playlist, UIContext[String]
 * ClientConnected - vars: IPAddress[String], MACAddress[String] (if its a placeshifter/extender, MACAddress is null otherwise)
 * ClientDisconnected - vars: IPAddress[String], MACAddress[String] (if its a placeshifter/extender, MACAddress is null otherwise)
 */
public interface SageTVEventListener
{
	// This is a callback method invoked from the SageTV core for any events the listener has subscribed to
	// See the sage.SageTVPluginRegistry interface definition for details regarding subscribing and unsubscribing to events.
	// The eventName will be a predefined String which indicates the event type
	// The eventVars will be a Map of variables specific to the event information. This Map should NOT be modified.
	// The keys to the eventVars Map will generally be Strings; but this may change in the future and plugins that submit events
	// are not required to follow that rule.
	public void sageEvent(String eventName, java.util.Map eventVars);
}
