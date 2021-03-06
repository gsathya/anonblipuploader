package com.apress.proandroidmedia.ch12.blipuploader;

import java.io.File;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class BlipTVUploader extends Activity implements OnClickListener {

	final static int VIDEO_CAPTURED = 0;
	final static int VIDEO_PLAYED = 1;
	private static final int GALLERY_RESULT = 2;

	File videoFile;
	String TAG = "BlipTVUploader";
	String title = "A Video";
	String username = "bliptest123";
	String password = "bliptest123";
	String host = "localhost";
	int port = 8118;

	String postingResult = "";

	long fileLength = 0;

	Button selectVideo;
	Button captureVideo;
	
	Context context = this;
	ProgressDialog dialog;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.mainmenu);

		selectVideo = (Button) findViewById(R.id.selectVideo);
		captureVideo = (Button) findViewById(R.id.captureVideo);
		selectVideo.setOnClickListener(this);
		captureVideo.setOnClickListener(this);
		

		
	}
	
	public void onClick(View v){
		
		if(v == captureVideo){
			Intent captureVideoIntent = new Intent(
					android.provider.MediaStore.ACTION_VIDEO_CAPTURE);
			startActivityForResult(captureVideoIntent, VIDEO_CAPTURED);
		}
		else if(v == selectVideo){
			Intent i = new Intent(Intent.ACTION_GET_CONTENT, android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
			startActivityForResult(i, GALLERY_RESULT);
		}
	}
	
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {

		if (resultCode == RESULT_OK)
		{	
			String videoFilePath = null;
			Uri videoFileUri = null;
			if(requestCode == VIDEO_CAPTURED) {
				videoFileUri = data.getData();
			
				String[] columns = { android.provider.MediaStore.Video.Media.DATA };
				Cursor cursor = managedQuery(videoFileUri, columns, null, null,
						null);
				int fileColumn = cursor
						.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DATA);
				if (cursor.moveToFirst()) {
				videoFilePath = cursor.getString(fileColumn);
				Log.d(TAG,"CAPTURED VIDEO FILE PATH"+videoFilePath);

				
				}
		
			} 
			else if(requestCode == GALLERY_RESULT){
				videoFileUri = data.getData();
				
				String[] columns = { android.provider.MediaStore.Video.Media.DATA };
				Cursor cursor = managedQuery(videoFileUri, columns, null, null,
						null);
				int fileColumn = cursor
						.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DATA);
				if (cursor.moveToFirst()) {
				videoFilePath = cursor.getString(fileColumn);
				Log.v(TAG,"SELECTED VIDEO FILE PATH"+videoFilePath);
				}
			}
			
			videoFile = new File(videoFilePath);
			fileLength = videoFile.length();
			BlipTVFilePoster btvfp = new BlipTVFilePoster();
			btvfp.execute();
		}
		else if (requestCode == VIDEO_PLAYED) {
			finish();
		}
	}

	class BlipTVFilePoster extends AsyncTask<Void, String, Void> implements
			ProgressListener, BlipXMLParserListener {

		String videoUrl;
		
		protected void onPreExecute(){

	        dialog = new ProgressDialog(context);
	        dialog.setMessage("Uploading..");
	        dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
	        dialog.setCancelable(false);
	        dialog.setProgress(0);
	        dialog.show();
		}
		@Override
		protected Void doInBackground(Void... params) {

			HttpClient httpclient = new DefaultHttpClient();
			HttpPost httppost = new HttpPost("http://blip.tv/file/post");
			HttpHost proxy = new HttpHost(host, port);
    		httpclient.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);

			ProgressMultipartEntity multipartentity = new ProgressMultipartEntity(
					this);

			try {
				multipartentity.addPart("file", new FileBody(videoFile));

				multipartentity.addPart("userlogin", new StringBody(username));
				multipartentity.addPart("password", new StringBody(password));
				multipartentity.addPart("title", new StringBody(title));
				multipartentity.addPart("post", new StringBody("1"));
				multipartentity.addPart("skin", new StringBody("api"));

				httppost.setEntity(multipartentity);
				HttpResponse httpresponse = httpclient.execute(httppost);

				HttpEntity responseentity = httpresponse.getEntity();
				if (responseentity != null) {

					InputStream inputstream = responseentity.getContent();

					SAXParserFactory aSAXParserFactory = SAXParserFactory
							.newInstance();
					try {

						SAXParser aSAXParser = aSAXParserFactory.newSAXParser();
						XMLReader anXMLReader = aSAXParser.getXMLReader();

						BlipResponseXMLHandler xmlHandler = new BlipResponseXMLHandler(
								this);
						anXMLReader.setContentHandler(xmlHandler);
						anXMLReader.parse(new InputSource(inputstream));

					} catch (ParserConfigurationException e) {
						e.printStackTrace();
					} catch (SAXException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}

					inputstream.close();

				}
			} catch (ClientProtocolException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}

			return null;
		}
		
		protected void onPostExecute(Void result) {
			
			//Dismiss progressbar dialog
			dialog.dismiss();
			
			//Display alert dialog
			String msg = null;
			msg = "Your video has been uploaded.";
			
			new AlertDialog.Builder(context)
	        .setPositiveButton("OK", null)
	        .setMessage(msg)
	        .show();
		}

		public void transferred(long num) {
			double percent = (double) num / (double) fileLength;
			int percentInt = (int) (percent * 100);
			
			//Update progressbar dialog
			dialog.setProgress(percentInt);
			
		}

		public void parseResult(String result) {
			publishProgress(result);
		}

		public void setVideoUrl(String url) {
			videoUrl = url;
		}
	}

	class ProgressMultipartEntity extends MultipartEntity {
		ProgressListener progressListener;

		public ProgressMultipartEntity(ProgressListener pListener) {
			super();
			this.progressListener = pListener;
		}

		@Override
		public void writeTo(OutputStream outstream) throws IOException {
			super.writeTo(new ProgressOutputStream(outstream,
					this.progressListener));
		}
	}

	interface ProgressListener {
		void transferred(long num);
	}

	static class ProgressOutputStream extends FilterOutputStream {

		ProgressListener listener;
		int transferred;

		public ProgressOutputStream(final OutputStream out,
				ProgressListener listener) {
			super(out);
			this.listener = listener;
			this.transferred = 0;
		}

		public void write(byte[] b, int off, int len) throws IOException {
			out.write(b, off, len);
			this.transferred += len;
			this.listener.transferred(this.transferred);
		}

		public void write(int b) throws IOException {
			out.write(b);
			this.transferred++;
			this.listener.transferred(this.transferred);
		}
	}

	interface BlipXMLParserListener {
		void parseResult(String result);

		void setVideoUrl(String url);
	}

	class BlipResponseXMLHandler extends DefaultHandler {

		int NONE = 0;
		int ONSTATUS = 1;
		int ONFILE = 2;
		int ONERRORMESSAGE = 3;

		int state = NONE;

		int STATUS_UNKNOWN = 0;
		int STATUS_OK = 1;
		int STATUS_ERROR = 2;

		int status = STATUS_UNKNOWN;

		String message = "";

		BlipXMLParserListener listener;

		public BlipResponseXMLHandler(BlipXMLParserListener bxpl) {
			super();
			listener = bxpl;
		}

		@Override
		public void startDocument() throws SAXException {
		}

		@Override
		public void endDocument() throws SAXException {
		}

		@Override
		public void startElement(String uri, String localName, String qName,
				Attributes attributes) throws SAXException {
			if (localName.equalsIgnoreCase("status")) {
				state = ONSTATUS;
			} else if (localName.equalsIgnoreCase("file")) {
				state = ONFILE;

				listener.parseResult("onFile");
				message = attributes.getValue("src");
				listener.parseResult("filemessage:" + message);

				listener.setVideoUrl(message);
			} else if (localName.equalsIgnoreCase("message")) {
				state = ONERRORMESSAGE;
				listener.parseResult("onErrorMessage");
			}
		}

		@Override
		public void endElement(String uri, String localName, String qName)
				throws SAXException {
			if (localName.equalsIgnoreCase("status")) {
				state = NONE;
			} else if (localName.equalsIgnoreCase("file")) {
				state = NONE;
			} else if (localName.equalsIgnoreCase("message")) {
				state = NONE;
			}
		}

		@Override
		public void characters(char[] ch, int start, int length)
				throws SAXException {
			String stringChars = new String(ch, start, length);
			if (state == ONSTATUS) {
				if (stringChars.equalsIgnoreCase("OK")) {
					status = STATUS_OK;
				} else if (stringChars.equalsIgnoreCase("ERROR")) {
					status = STATUS_ERROR;
				} else {
					status = STATUS_UNKNOWN;
				}
			} else if (state == ONERRORMESSAGE) {
				message += stringChars.trim();
				listener.parseResult(message);
			}
		}
	}
}
