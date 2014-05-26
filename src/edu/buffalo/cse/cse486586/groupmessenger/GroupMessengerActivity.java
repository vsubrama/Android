package edu.buffalo.cse.cse486586.groupmessenger;
//Android Imports
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.EditText;

//Java Imports
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.PriorityQueue;
import java.io.Serializable;


/**
 * GroupMessengerActivity is the main Activity for the assignment.
 * 
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {
	//Define all Ports in an array
	public String[] ports = new String[]{"11108","11112","11116","11120","11124"};
	//Define server Port
    static final int SERVER_PORT = 10000;
    //Define Sequencer Port
    static final String SEQUENCER_PORT = "11112";
    //Define Ordering Index
    private static int orderingIndex = -1;
    //Define Priority Queue for holding out of order packets
    public PriorityQueue<Message> holdBackQueue = new PriorityQueue<Message>();
    //Define Content Resolver , to connect to the data provider 
    ContentResolver mContentResolver;
    //Uri for content provider
    private final Uri mUri = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger.provider");
       
    private Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
    	mContentResolver = this.getContentResolver();
        super.onCreate(savedInstanceState);
        TelephonyManager tel;
         
         tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
           String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
           final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));
           setContentView(R.layout.activity_group_messenger);
        
        try
        {
        	ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
        	new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        }
        catch(IOException e)
        {
        	Log.e("message", "socket not created");
        	return;
        }
        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());
        
        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));
        // button event for client send click
        final Button rightButton = (Button) findViewById(R.id.button4);
        rightButton.setOnClickListener(new Button.OnClickListener() {  
            public void onClick(View v)
                {
            	  
            	  EditText editText = (EditText) findViewById(R.id.editText1);
            	  String msg = editText.getText().toString() + "\n";
                  editText.setText(""); // This is one way to reset the input box.
                  new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, myPort);

                    //perform action
                }
             });
        
                /*
         * TODO: You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs in a total-causal order.
         */
    }
    
    
    
    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            
            /*
             * TODO: Fill in your server code that receives messages and passes them
             * to onProgressUpdate().
             */
            if(serverSocket != null)
            {
            	while(true)
            	{
	            	try {
	            		
						Socket sock = serverSocket.accept();
						if(sock != null)
						{
							InputStream is = sock.getInputStream();
							ObjectInputStream ois = new ObjectInputStream(is);
							Message m = (Message)ois.readObject();
							ois.close();
							is.close();
							if(m.messageType.equals("M"))
							{
								//Call Client task for sending sequenced messages
								new ClientTask().executeOnExecutor(SERIAL_EXECUTOR,m.message,SEQUENCER_PORT);
								
								
							}
							else
							{
								if(orderingIndex + 1 < m.id)
								{
									holdBackQueue.add(m);
									
								}
								else
								{
									ContentValues values = new ContentValues();
									
									values.put("key",Integer.toString(m.id));
									values.put("value", m.message);
									
									mContentResolver.insert(mUri, values);
									
									orderingIndex = m.id;
									publishProgress(m.message + ":"  + Integer.toString(orderingIndex));
									
									
								}
								if(!holdBackQueue.isEmpty())
								{
									while(!holdBackQueue.isEmpty())
									{
										if(holdBackQueue.peek().id == orderingIndex + 1)
										{
											ContentValues values = new ContentValues();
											
											Message msg = holdBackQueue.poll();
											values.put("key",Integer.toString(msg.id));
											values.put("value", msg.message);
											mContentResolver.insert(mUri, values);
											orderingIndex = msg.id;
											publishProgress(msg.message + ":"  + Integer.toString(orderingIndex));
											
										}
										else
										{
											break;
										}
									 }
									
								 }
								 
								
								
							}
							sock.close();
						}
						
	            	}
					catch (Exception e) {
						// TODO Auto-generated catch block
						Log.e("error",e.getMessage());
					}
	            	
            	}
            }
                 

            return null;
        }
        
        
        protected void onProgressUpdate(String...strings) {
            /*
             * The following code displays what is received in doInBackground().
             */
            String strReceived = strings[0].trim();
            TextView remoteTextView = (TextView) findViewById(R.id.textView1);
            remoteTextView.append(strReceived + "\t\n");
            
            /*
             * The following code creates a file in the AVD's internal storage and stores a file.
             * 
             * For more information on file I/O on Android, please take a look at
             * http://developer.android.com/training/basics/data-storage/files.html
             */
            
            String filename = "GroupMessengerOutput";
            String string = strReceived + "\n";
            FileOutputStream outputStream;

            try {
                outputStream = openFileOutput(filename, Context.MODE_PRIVATE);
                outputStream.write(string.getBytes());
                outputStream.close();
            } catch (Exception e) {
                Log.e("message", "File write failed");
            }

            return;
        }
    }
    
    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
        	String msg = msgs[0];
        	
        	if(msgs[1].equals(SEQUENCER_PORT))
        	{
        		Message msgToSend = new Message(orderingIndex + 1,"OM",msg);
        		for (int i= 0; i<5;i++)
                {
            		String remotePort = ports[i];
            		
    	        	try {
    	                
    	                Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
    	                        Integer.parseInt(remotePort));
    	                
    	                /*
    	                 * TODO: Fill in your client code that sends out a message.
    	                 */
    	                OutputStream os =  socket.getOutputStream();
    	                ObjectOutputStream ois = new ObjectOutputStream(os);
    	                ois.writeObject(msgToSend);
    	                //ois.flush();
    	                ois.close();
    	                os.close();
    	                socket.close();
    	                
    	            } catch (UnknownHostException e) {
    	                Log.e("message", "ClientTask UnknownHostException");
    	            } catch (IOException e) {
    	                Log.e("message", "ClientTask socket IOException");
    	            }
                }

        	}
        	else
        	{
        		try {
        			Message msgToSend = new Message(0,"M",msg);
	                Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
	                        Integer.parseInt(SEQUENCER_PORT));
	                
	                /*
	                 * TODO: Fill in your client code that sends out a message.
	                 */
	                if(socket != null)
	                {
		                
		                OutputStream os =  socket.getOutputStream();
		                ObjectOutputStream ois = new ObjectOutputStream(os);
		                ois.writeObject(msgToSend);
		                ois.close();
		                os.close();
		                socket.close();
	                }
	                
	            } catch (UnknownHostException e) {
	                Log.e("message", "ClientTask UnknownHostException");
	            } catch (IOException e) {
	                Log.e("message", e.getMessage());
	            }
        	}
        	
        	
        	
            return null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }

    
    
}
class Message implements Serializable,Comparable<Message>{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	int id;
	String messageType;
	String message;
	
	
	Message(int id,String messageType, String message)
	{
		this.id = id;
		this.messageType = messageType;
		this.message = message;
		
	}

	@Override
	public int compareTo(Message msg) {
		if(this.id > msg.id) 
		{
			return 1;
		}
		else if(this.id < msg.id)
		{
			return -1;
		}
		return 0;		
	}
}