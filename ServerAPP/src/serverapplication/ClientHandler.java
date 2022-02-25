/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package serverapplication;

import db.Player;
import db.database;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONObject;

/**
 *
 * @author amr
 */
public class ClientHandler extends Thread{
    
    DataInputStream dis;
    PrintStream ps;
    database db;
    JSONObject request,response;
    Player player;
    static Vector<ClientHandler> clientsVector = new Vector<ClientHandler>();
    
    public ClientHandler(Socket sc,database _db){
        db = _db;
        response = new JSONObject();
        try {
            ps = new PrintStream(sc.getOutputStream());
            dis = new DataInputStream(sc.getInputStream());
            start();
        } catch (IOException ex) {
            Logger.getLogger(ClientHandler.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    void closeConnection(){
        try {
            ps.println("closing connection");
            dis.close();
            ps.close();
            stop();
        } catch (IOException ex) {
            Logger.getLogger(ClientHandler.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public static void closeServer(){
        for(ClientHandler c : clientsVector){
            c.closeConnection();
        }
        clientsVector.clear();
    }
    
    
    @Override
    public void run(){
        if(ServerApplication.server_status == Status.ON)
        {
            if(log()){
                clientsVector.add(this);
                changeOnlineStatus();
            }else{
                return;
            }
        }
        else{
            ps.println("server is closed");
            closeConnection();
            return;
        }
        
        while(true){
            try {
                String re = dis.readLine();
                if(re == null)
                {
                    clientsVector.remove(this);
                    for(ClientHandler c : clientsVector){
                        c.get_online_players();
                    }
                    closeConnection();
                    return;
                }
                JSONObject request = new JSONObject(re);
                System.out.println(request);
                ClientMsg msg = request.getEnum(ClientMsg.class,"type");
                switch (msg) {
                    case GET_LEADERBOARD:
                        get_leaderboard();
                        break;
                    case GET_ONLINE_PLAYERS:
                        get_online_players();
                        break;
                    case INVETATION_SEND:
                        sendInvitation(request);
                        break;
                    case INVETATION_REPLY:
                        replyInvitation(request);
                        break;
                    case START_GAME:
                        startGame(request);
                        break;
                    case SAVE_DONE_GAME:
                        inserDoneGame(request);
                        break ;
                    case CLOSE_CONNECTION:
                        clientsVector.remove(this);
                        for(ClientHandler c : clientsVector){
                            c.get_online_players();
                        }
                        closeConnection();
                        return;
                }
               
           } catch (IOException ex) {
               Logger.getLogger(ClientHandler.class.getName()).log(Level.SEVERE, null, ex);
           }
        }
    }
    
    private void changeOnlineStatus(){
        for(ClientHandler c : clientsVector){
            c.get_online_players();
        }
    } 
    
    boolean log(){
        while(true){
        try {
            String re = dis.readLine();
            System.out.println(re);
            request = new JSONObject(re);
            ClientMsg msg = request.getEnum(ClientMsg.class,"type");
            switch (msg) {
                case SIGNIN:
                    if(signIn(request)){
                        return true;
                    }
                    break;
                case SIGNUP:
                    signUp(request);
                    break;
                case CLOSE_CONNECTION:
                    closeConnection();
                    return false;
            }
        } catch (IOException ex) {
            Logger.getLogger(ClientHandler.class.getName()).log(Level.SEVERE, null, ex);
        }
        }
    }
    
    boolean signIn(JSONObject request){
        response.clear();
        response.put("type", ClientMsg.SIGNIN);
        
        if(isPlayerOnline(request.getString("username"))){
            response.put("id", -1);
            ps.println(response);
            return false;
        }
        
        int r = db.signIn(request.getString("username"), request.getString("passwd"));
        if(r==0){
            response.put("id", 0);
            ps.println(response);
            return false;
        }
        else{
            player = db.getPlayerProfile(r);
            response.put("id", player.getId());
            response.put("score", player.getScore());
            response.put("loses", player.getWins());
            response.put("wins", player.getLosses());
            response.put("username", player.getUsername());
            ps.println(response);
            return true;
        }
    }

    boolean signUp(JSONObject request){
        int r = db.signUp(request.getString("username"), request.getString("passwd"));
        response.clear();
        response.put("type", ClientMsg.SIGNUP);
        if(r==0){
            response.put("id", 0);
            ps.println(response);
            return false;
        }
        else{
            player = db.getPlayerProfile(r);
            response.put("id", player.getId());
            ps.println(response);
            return true;
        }
    }

    
    void get_leaderboard(){
        response = db.getLeaderBoard();
        response.put("type", ClientMsg.GET_LEADERBOARD);
        ps.println(response);
    }
    
    void get_online_players(){
        String names="",scores="",id="";
        for(ClientHandler c : clientsVector){
            if(c!=this){
                names = names + ","+c.player.getUsername();
                scores = scores+","+c.player.getScore();
                id = id+","+c.player.getId();
            }
        }
        response.clear();
        response.put("name", names);
        response.put("score", scores);
        response.put("id", id);
        response.put("type", ClientMsg.GET_ONLINE_PLAYERS);
        ps.println(response);
    }
    
    void sendInvitation(JSONObject request){
        ClientHandler player_info = GetPlayerByID(request.getInt("reciever id"));
        response.clear();
        response.put("type", ClientMsg.INVETATION_SEND);
        response.put("sender id", player.getId());
        response.put("sender name", player.getUsername());
        response.put("reciever id", player_info.player.getId());
        player_info.ps.println(response);
    }
    
    void replyInvitation(JSONObject request){
        ClientHandler player_info = GetPlayerByID(request.getInt("reciever id"));
        response.clear();
        response.put("type", ClientMsg.INVETATION_REPLY);
        response.put("sender id", player.getId());
        response.put("sender name", player.getUsername());
        response.put("reply", request.getString("reply"));
        response.put("reciever id", player_info.player.getId());
        player_info.ps.println(response);
    }
    
    void inserDoneGame(JSONObject request){
        if(db.insertDoneGame(request.getInt("winner"), request.getInt("loser"), request.getBoolean("draw"))!=0)
        db.increaseScore(request.getInt("winner"));
        player = db.getPlayerProfile(player.getId());
        if(GetPlayerByID(request.getInt("loser"))!=null)
            GetPlayerByID(request.getInt("loser")).player = db.getPlayerProfile(request.getInt("loser"));
    }
    
    boolean replyForReplay(JSONObject request,ClientHandler p2){
        p2.ps.println(request);
        return request.getBoolean("reply");
    }
    
    private void startGame(JSONObject request){
        ClientHandler player2 = GetPlayerByID(request.getInt("player2"));
        while(true){
            try {
            String re = dis.readLine();
            if(re == null)
            {
                clientsVector.remove(this);
                for(ClientHandler c : clientsVector){
                    c.get_online_players();
                }
                closeConnection();
                return;
            }
            request = new JSONObject(re);
            System.out.println(request);
            ClientMsg msg = request.getEnum(ClientMsg.class,"type");
            switch (msg) {
                case ADD_MOVE:
                    player2.ps.println(request);
                    break;
                case SEND_MESSAGE:
                    player2.ps.println(request);
                    break;
                case SAVE_DONE_GAME:
                    inserDoneGame(request);
                    break ;
                case REPLAY_REPLY:
                    if(!replyForReplay(request,player2))
                        return;
                    break ;
                case GET_ONLINE_PLAYERS:
                    get_online_players();
                    return;
                case CLOSE_CONNECTION:
                    clientsVector.remove(this);
                    for(ClientHandler c : clientsVector){
                        c.get_online_players();
                    }
                    closeConnection();
                    return;
            }
            } catch (IOException ex) {
                Logger.getLogger(ClientHandler.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    
    
    
    ClientHandler GetPlayerByID(int id){
        for(ClientHandler c : clientsVector){
            if(c.player.getId() == id){
                return c;
            }
        }
        return null;
    }

    boolean isPlayerOnline(String username){
        for(ClientHandler c : clientsVector){
            if(c.player.getUsername().equals(username)){
                return true;
            }
        }
        return false;
    }
    
}
