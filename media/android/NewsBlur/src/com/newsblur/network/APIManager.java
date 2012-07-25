package com.newsblur.network;

import java.util.Date;
import java.util.List;
import java.util.Map.Entry;

import org.apache.http.HttpStatus;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.newsblur.database.DatabaseConstants;
import com.newsblur.database.FeedProvider;
import com.newsblur.domain.Comment;
import com.newsblur.domain.Feed;
import com.newsblur.domain.FolderStructure;
import com.newsblur.domain.Story;
import com.newsblur.network.domain.FeedFolderResponse;
import com.newsblur.network.domain.FeedRefreshResponse;
import com.newsblur.network.domain.LoginResponse;
import com.newsblur.network.domain.ProfileResponse;
import com.newsblur.network.domain.StoriesResponse;
import com.newsblur.serialization.DateStringTypeAdapter;
import com.newsblur.serialization.FolderStructureTypeAdapter;
import com.newsblur.util.PrefsUtil;

public class APIManager {

	private static final String TAG = "APIManager";
	private Context context;
	private Gson gson;
	private ContentResolver contentResolver;

	public APIManager(final Context context) {
		this.context = context;
		final GsonBuilder builder = new GsonBuilder();
		builder.registerTypeAdapter(FolderStructure.class, new FolderStructureTypeAdapter());
		builder.registerTypeAdapter(Date.class, new DateStringTypeAdapter());
		contentResolver = context.getContentResolver();
		gson = builder.create();
	}

	public LoginResponse login(final String username, final String password) {
		final APIClient client = new APIClient(context);
		final ContentValues values = new ContentValues();
		values.put(APIConstants.PARAMETER_USERNAME, username);
		values.put(APIConstants.PARAMETER_PASSWORD, password);
		final APIResponse response = client.post(APIConstants.URL_LOGIN, values);
		if (response.responseCode == HttpStatus.SC_OK && !response.hasRedirected) {
			LoginResponse loginResponse = gson.fromJson(response.responseString, LoginResponse.class);
			PrefsUtil.saveCookie(context, response.cookie);
			return loginResponse;
		} else {
			return new LoginResponse();
		}		
	}
	
	public LoginResponse signup(final String username, final String password) {
		final APIClient client = new APIClient(context);
		final ContentValues values = new ContentValues();
		values.put(APIConstants.PARAMETER_USERNAME, username);
		values.put(APIConstants.PARAMETER_PASSWORD, password);
		final APIResponse response = client.post(APIConstants.URL_SIGNUP, values);
		if (response.responseCode == HttpStatus.SC_OK && !response.hasRedirected) {
			LoginResponse loginResponse = gson.fromJson(response.responseString, LoginResponse.class);
			PrefsUtil.saveCookie(context, response.cookie);
			return loginResponse;
		} else {
			return new LoginResponse();
		}		
	}
	
	public ProfileResponse updateUserProfile() {
		final APIClient client = new APIClient(context);
		final APIResponse response = client.get(APIConstants.URL_MY_PROFILE);
		if (response.responseCode == HttpStatus.SC_OK && !response.hasRedirected) {
			ProfileResponse profileResponse = gson.fromJson(response.responseString, ProfileResponse.class);
			PrefsUtil.saveUserDetails(context, profileResponse.user);
			return profileResponse;
		} else {
			return null;
		}
	}
	
	public StoriesResponse getStoriesForFeed(String feedId) {
		final APIClient client = new APIClient(context);
		final ContentValues values = new ContentValues();
		values.put(APIConstants.PARAMETER_FEEDS, feedId);
		Uri feedUri = Uri.parse(APIConstants.URL_FEED_STORIES).buildUpon().appendPath(feedId).build();
		final APIResponse response = client.get(feedUri.toString(), values);
		StoriesResponse storiesResponse = gson.fromJson(response.responseString, StoriesResponse.class);
		if (response.responseCode == HttpStatus.SC_OK && !response.hasRedirected) {
			Uri storyUri = FeedProvider.STORIES_URI.buildUpon().appendPath(feedId).build();
			for (Story story : storiesResponse.stories) {
				contentResolver.insert(storyUri, story.getValues());
				
				for (Comment comment : story.comments) {
					StringBuilder builder = new StringBuilder();
					builder.append(story.id);
					builder.append(story.feedId);
					builder.append(comment.userId);
					comment.storyId = story.id;
					comment.id = (builder.toString());
					contentResolver.insert(FeedProvider.COMMENTS_URI, comment.getValues());
				}
			}
			return storiesResponse;
		} else {
			return null;
		}
	}
	
	public boolean followUser(final String userId) {
		final APIClient client = new APIClient(context);
		final ContentValues values = new ContentValues();
		values.put(APIConstants.PARAMETER_USERID, userId);
		final APIResponse response = client.post(APIConstants.URL_FOLLOW, values);
		if (response.responseCode == HttpStatus.SC_OK && !response.hasRedirected) {
			return true;
		} else {
			return false;
		}
	}
	
	public boolean unfollowUser(final String userId) {
		final APIClient client = new APIClient(context);
		final ContentValues values = new ContentValues();
		values.put(APIConstants.PARAMETER_USERID, userId);
		final APIResponse response = client.post(APIConstants.URL_UNFOLLOW, values);
		if (response.responseCode == HttpStatus.SC_OK && !response.hasRedirected) {
			return true;
		} else {
			return false;
		}
	}

	public void getFolderFeedMapping() {
		final APIClient client = new APIClient(context);
		final APIResponse response = client.get(APIConstants.URL_FEEDS);
		final FeedFolderResponse feedUpdate = gson.fromJson(response.responseString, FeedFolderResponse.class);
		
		for (final Entry<String, Feed> entry : feedUpdate.feeds.entrySet()) {
			final Feed feed = entry.getValue();
			contentResolver.insert(FeedProvider.FEEDS_URI, feed.getValues());
		}

		for (final Entry<String, List<Long>> entry : feedUpdate.folderStructure.folders.entrySet()) {	
			final ContentValues folderValues = new ContentValues();
			folderValues.put(DatabaseConstants.FOLDER_NAME, entry.getKey());
			contentResolver.insert(FeedProvider.FOLDERS_URI, folderValues);
			
			for (Long feedId : entry.getValue()) {
				ContentValues values = new ContentValues(); 
				values.put(DatabaseConstants.FEED_FOLDER_FEED_ID, feedId);
				values.put(DatabaseConstants.FEED_FOLDER_FOLDER_NAME, entry.getKey());
				contentResolver.insert(FeedProvider.FEED_FOLDER_MAP_URI, values);
			}
		}
	}

	public ProfileResponse getUser(String userId) {
		final APIClient client = new APIClient(context);
		final ContentValues values = new ContentValues();
		values.put(APIConstants.PARAMETER_USER_ID, userId);
		final APIResponse response = client.get(APIConstants.URL_USER_PROFILE, values);
		if (response.responseCode == HttpStatus.SC_OK && !response.hasRedirected) {
			ProfileResponse profileResponse = gson.fromJson(response.responseString, ProfileResponse.class);
			return profileResponse;
		} else {
			return null;
		}
	}

	public void refreshFeedCounts() {
		final APIClient client = new APIClient(context);
		final APIResponse response = client.get(APIConstants.URL_FEED_COUNTS);
		if (response.responseCode == HttpStatus.SC_OK && !response.hasRedirected) {
			final FeedRefreshResponse feedCountUpdate = gson.fromJson(response.responseString, FeedRefreshResponse.class);
			for (String feedId : feedCountUpdate.feedCounts.keySet()) {
				Uri feedUri = FeedProvider.FEEDS_URI.buildUpon().appendPath(feedId).build();
				contentResolver.update(feedUri, feedCountUpdate.feedCounts.get(feedId).getValues(), null, null);
			}
		}
	}

}
