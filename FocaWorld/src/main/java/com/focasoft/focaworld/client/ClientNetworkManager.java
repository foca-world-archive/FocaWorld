package com.focasoft.focaworld.client;

import com.focasoft.focaworld.net.BadPacketException;
import com.focasoft.focaworld.net.Packet;
import com.focasoft.focaworld.net.PacketParser;
import com.focasoft.focaworld.net.packets.PacketHandshake;
import com.focasoft.focaworld.task.AsyncWorker;
import org.json.JSONObject;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.LinkedList;
import java.util.Scanner;

public class ClientNetworkManager implements Runnable
{
  private final LinkedList<String> OUT_MESSAGES = new LinkedList<>();
  private final LinkedList<Packet> IN_MESSAGES = new LinkedList<>();

  private final ClientPacketProcessor PROCESSOR;
  private final AsyncWorker WORKER;
  private final Client CLIENT;
  private final String HOST;

  private final int PORT;
  
  private Scanner input;
  private PrintWriter output;

  private Socket socket;
  private Thread thread;
  
  private volatile boolean running = true;
  
  public ClientNetworkManager(Client client, String hostname, int port)
  {
    this.HOST = hostname;
    this.PORT = port;

    this.PROCESSOR = new ClientPacketProcessor(client);
    this.CLIENT = client;

    WORKER = new AsyncWorker();
    WORKER.start();
  }
  
  public void connect() throws IOException
  {
    socket = new Socket(HOST, PORT);
    input = new Scanner(socket.getInputStream());
    output = new PrintWriter(socket.getOutputStream(), true);
    thread = new Thread(this, "Network Manager");
    running = true;
    thread.start();

    sendPacket(new PacketHandshake(CLIENT.getName()));
  }
  
  public void disconnect() throws IOException
  {
    running = false;
    
    try{
      thread.join();
    } catch(InterruptedException e)
    {
      e.printStackTrace();
    }
    
    socket.close();
    socket = null;
    input = null;
    output = null;
  }
  
  private void parseInput(String line)
  {
    System.out.println("Recebi: " + line);
    Packet packet;

    try {
      packet = PacketParser.parsePacket(line);
    } catch(BadPacketException e) {
      e.printStackTrace();
      return;
    }
    
    synchronized(IN_MESSAGES)
    {
      IN_MESSAGES.add(packet);
    }
  }
  
  public void sendMessage(String msg)
  {
    synchronized(OUT_MESSAGES)
    {
      OUT_MESSAGES.add(msg);
    }
  }

  protected void sendPacketNow(Packet packet)
  {
    output.println(packet.serialize());
  }

  public void sendMessage(JSONObject json)
  {
    sendMessage(json.toString());
  }
  
  public void sendPacket(Packet packet)
  {
    sendMessage(packet.serialize());
  }
  
  public LinkedList<Packet> drainInput()
  {
    LinkedList<Packet> in;
    
    synchronized(IN_MESSAGES)
    {
      in = new LinkedList<>(IN_MESSAGES);
      IN_MESSAGES.clear();
    }
    
    return in;
  }
  
  private LinkedList<String> drainOut()
  {
    LinkedList<String> out;

    synchronized(OUT_MESSAGES)
    {
      out = new LinkedList<>(OUT_MESSAGES);
      OUT_MESSAGES.clear();
    }

    return out;
  }

  public void processIncomingPackets()
  {
    PROCESSOR.processPackets(drainInput());
  }

  public void processOutPackets()
  {
    if(OUT_MESSAGES.size() == 0)
      return;

    WORKER.addTask(() -> {
      LinkedList<String> out = ClientNetworkManager.this.drainOut();

      out.forEach(e -> {
        ClientNetworkManager.this.output.println(e);
        System.out.println("Escrevi: " + e);
      });
    });
  }

  @Override
  public void run()
  {
    while(running)
    {
      if(socket.isClosed())
        System.out.println("Fecho");
      
      String line = input.nextLine();

      if(line != null)
      {
        parseInput(line);
      }
    }
  }
}