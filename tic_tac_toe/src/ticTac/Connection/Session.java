package ticTac.Connection;

import controller.ControlManager;
import controller.LeaderBoardController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.*;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import org.json.JSONObject;
import ticTac.Connection.msgType;
import controller.LoginController;
import controller.MainScreen;
import controller.Player;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import org.json.JSONArray;

public class Session extends Thread{
    private Stage stage;
    private Socket socket;
    private DataInputStream inputStream;
    private PrintStream printStream;
    public ControlManager controlManager;
    public Player player;
    
    JSONObject Message ;
    boolean Connected =false;
    public boolean viewOnlinePlayers = false;
    public boolean loged = false;


    public Session(Stage stage)
    {
        controlManager = new ControlManager();
        openConnection();
        this.stage = stage;
        start();
    }

    public void openConnection(){
        try {
            socket = new Socket("127.0.0.1",5005);
            printStream = new PrintStream(socket.getOutputStream());
            inputStream = new DataInputStream(socket.getInputStream());
            Connected = true;
        } catch (IOException e) {
            Connected = false;
        }
    }

    public void CloseConnection(){
        Message = new JSONObject();
        Message.put("type",msgType.CLOSE_CONNECTION);
        String jsonStr = Message.toString();
        printStream.println(jsonStr);
        Connected = false;
        try {
            printStream.close();
            inputStream.close();
            socket.close();
            stop();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void StartCommunication() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (Connected){
                    try {
                        Message = new JSONObject(inputStream.readLine());
                        MessageHandler(Message);
                    } catch (IOException e) {
                       Connected = false;
                       break;
                    }
                }

                try {
                    socket.close();
                    inputStream.close();
                    printStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }
    
    public FXMLLoader changeScene(String xml)
    {
        FXMLLoader loader = null;
        try {
            loader = new FXMLLoader();
            loader.setLocation(getClass().getResource(xml));
            Parent fxmlViewChild = loader.load();
            Scene fxmlViewScene = new Scene(fxmlViewChild);
            Platform.runLater(new Runnable() {
            @Override public void run() {
                stage.setScene(fxmlViewScene);
                stage.show();
                }
            });
            
        } catch (IOException ex) {
            Logger.getLogger(LoginController.class.getName()).log(Level.SEVERE, null, ex);
        }
        return loader;
    }

    public void signInRequest(String Username , String Password)
    {
        JSONObject js = new JSONObject();
        js.put("type", msgType.SIGNIN);
        js.put("username", Username);
        js.put("passwd", Password);
        printStream.println(js);
    }
    
    private void signInResponse(JSONObject Message)
    {
        int id = Message.getInt("id");
        switch (id) {
            case 0:
                controlManager.getLoginController().login_failre();
                break;
            case -1:
                informationDialog("alert", "player already logged from another device");
                break;
            default:
                MainScreen.session.controlManager.setInvitationController(changeScene("/fxml/PlayerInvitationScreen.fxml"));
                loged = true;
                MainScreen.session.viewOnlinePlayers = true;
                MainScreen.session.getOnlinePlayersRequest();
                player = new Player();
                player.setID((int) Message.get("id"));
                player.setUsername((String) Message.get("username"));
                player.setScore((int) Message.get("score"));
        }
    }
    

    
    private void signUpResponse(JSONObject Message)
    {
        int id = Message.getInt("id");
        if(id == 0 )
        {
            return;
        }
        else
        {
            controlManager.setLoginController(changeScene("/fxml/login.fxml"));
            return;
        }
    }
    

    public void signUpRequest(String Username , String Password)
    {
        JSONObject js = new JSONObject();
        js.put("type", msgType.SIGNUP);
        js.put("username", Username);
        js.put("passwd", Password);
        printStream.println(js);
    }
    
    public void getLeaderboardRequest()
    {
        JSONObject js = new JSONObject();
        js.put("type", msgType.GET_LEADERBOARD);
        printStream.println(js);
    }
    
    private void getLeaderboardResponse(JSONObject Message)
    {
        controlManager.setLeaderBoardController(changeScene("/fxml/LeaderBoard.fxml"));
        controlManager.getLeaderBoardController().loadLeaderBoard(Message);
    }
    
    public void getOnlinePlayersRequest()
    {
        JSONObject js = new JSONObject();
        js.put("type", msgType.GET_ONLINE_PLAYERS);
        printStream.println(js);
    }
    
    private void getOnlinePlayersResponse(JSONObject Message)
    {
        if(viewOnlinePlayers&&!Message.get("name").toString().equals(",")){
            String[] name = Message.get("name").toString().split(",");
            String[] score = Message.get("score").toString().split(",");
            String[] id = Message.get("id").toString().split(",");
            Player [] playerList = new Player[name.length-1];
            for(int a = 1; a < name.length; a++){
                playerList[a-1] = new Player(name[a],Integer.parseInt(score[a]),Integer.parseInt(id[a]));
            }
            ObservableList<Player> list = FXCollections.observableArrayList(playerList);
            controlManager.getInvitationController().insertOnlinePlayers(list);
        }
    }
    

    public void invitationSendRequest(int id)
    {
        JSONObject js = new JSONObject();
        js.put("type", msgType.INVETATION_SEND);
        js.put("reciever id", id);
        printStream.println(js);
    }
    
    private void invitationSendResponse(JSONObject Message)
    {
        if(!viewOnlinePlayers){
            invitationReplyReqest("no", Message.getInt("sender id"));
            return;
        }
        
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                try {
                    Dialog<ButtonType> dialog = new Dialog<>();
                    FXMLLoader fxmlLoader = new FXMLLoader();
                    fxmlLoader.setLocation(getClass().getResource("/fxml/InvitationDialog.fxml"));
                    DialogPane winner = fxmlLoader.load();
                    
                    dialog.setDialogPane(winner);
                    dialog.getDialogPane().getButtonTypes().add(ButtonType.YES);
                    dialog.getDialogPane().getButtonTypes().add(ButtonType.NO);
                    dialog.setTitle("Game Invitation");
                    Label content = new Label(Message.getString("sender name").toUpperCase()+" Invited You to a Game! Would You Like to Accept?");
                    content.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 5px");
                    dialog.setGraphic(content);
                    Stage stage = (Stage) dialog.getDialogPane().getScene().getWindow();
                    stage.getIcons().add(new Image(this.getClass().getResource("/images/icon.png").toString()));
                    Optional<ButtonType> result = dialog.showAndWait();
                    if(result.get()==ButtonType.YES){
                        invitationReplyReqest("yes", Message.getInt("sender id"));
                        controlManager.setPlayerVsPlayerController(changeScene("/fxml/playerVsPlayer.fxml"));
                        MainScreen.session.viewOnlinePlayers=false;
                        controlManager.getPlayerVsPlayerController().setChoice(1);
                        controlManager.getPlayerVsPlayerController().setMyTurn(false);
                        controlManager.getPlayerVsPlayerController().setPlayersNames(Message.getString("sender name"),player.getUsername());
                        controlManager.getPlayerVsPlayerController().setOtherPlayerid(Message.getInt("sender id"));
                        JSONObject js = new JSONObject();
                        js.put("type", msgType.START_GAME);
                        js.put("player2", Message.getInt("sender id"));
                        printStream.println(js);
                    }
                    else{
                        invitationReplyReqest("no", Message.getInt("sender id"));
                    }
                } catch (IOException ex) {
                    Logger.getLogger(Session.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        });
    }

    public void invitationReplyReqest(String reply,int id)
    {
        JSONObject js = new JSONObject();
        js.put("type", msgType.INVETATION_REPLY);
        js.put("reciever id", id);
        js.put("reply", reply);
        printStream.println(js);
    }
    
    private void invitationReplyResponse(JSONObject Message)
    {
        if((Message.getString("reply")).equals("yes")){
            FXMLLoader loader = null;
            try {
                loader = new FXMLLoader();
                loader.setLocation(getClass().getResource("/fxml/playerVsPlayer.fxml"));
                Parent fxmlViewChild = loader.load();
                controlManager.setPlayerVsPlayerController(loader);
                Scene fxmlViewScene = new Scene(fxmlViewChild);
                Platform.runLater(new Runnable() {
                @Override public void run() {
                    stage.setScene(fxmlViewScene);
                    stage.show();
                    controlManager.getPlayerVsPlayerController().setChoice(0);
                    controlManager.getPlayerVsPlayerController().setMyTurn(true);
                    controlManager.getPlayerVsPlayerController().setPlayersNames(player.getUsername(), Message.getString("sender name"));
                    controlManager.getPlayerVsPlayerController().setOtherPlayerid(Message.getInt("sender id"));
                    }
                });

            } catch (IOException ex) {
                Logger.getLogger(LoginController.class.getName()).log(Level.SEVERE, null, ex);
            }
            
            MainScreen.session.viewOnlinePlayers=false;
            
            JSONObject js = new JSONObject();
            js.put("type", msgType.START_GAME);
            js.put("player2", Message.getInt("sender id"));
            printStream.println(js);
        }else{
            informationDialog("Invitation Response", "invitation refused");
        }
    }
    
    public void addMoveRequest(int cell,int choice)
    {
        JSONObject js = new JSONObject();
        js.put("type", msgType.ADD_MOVE);
        js.put("Move", cell);
        js.put("choice", choice);
        printStream.println(js);
    }
    
    private void addMoveResponse(JSONObject Message)
    {
        controlManager.getPlayerVsPlayerController().setMove(Message.getInt("Move"),Message.getInt("choice"));
    }
    
    public void sendMessageRequest(String Msg)
    {
        JSONObject js = new JSONObject();
        js.put("type", msgType.SEND_MESSAGE);
        js.put("msg", Msg);
        printStream.println(js);
    }
    
    private void sendMessageResponse(JSONObject Message)
    {
        controlManager.getPlayerVsPlayerController().recieveMessage(Message.getString("msg"));
    }
    
    public void insertDoneGameRequest(int winner,int loser,boolean draw){
        JSONObject js = new JSONObject();
        js.put("type", msgType.SAVE_DONE_GAME);
        js.put("winner", winner);
        js.put("loser", loser);
        js.put("draw", draw);
        printStream.println(js);
    }
    
    public void replayReplyRequest(boolean reply){
        JSONObject js = new JSONObject();
        js.put("type", msgType.REPLAY_REPLY);
        js.put("reply", reply);
        printStream.println(js);
    }
    
    private void replayReplyResponse(JSONObject Message){
        if(Message.getBoolean("reply")&&!viewOnlinePlayers){
            controlManager.getPlayerVsPlayerController().startNewGame();
        }else{
            informationDialog("", "player will not to re-match");
            controlManager.getPlayerVsPlayerController().setRefused(true);
            controlManager.setInvitationController(MainScreen.session.changeScene("/fxml/PlayerInvitationScreen.fxml"));
            viewOnlinePlayers = true;
            getOnlinePlayersRequest();
        }
    }
    
    
    public void run(){
        while(true){
            try {
                String re = inputStream.readLine();

                if(re == null)
                {
                    return;
                }
                JSONObject response = new JSONObject(re);
                msgType msg = response.getEnum(msgType.class,"type");
                System.out.println(response);
                switch (msg) {
                    case SIGNIN:
                        signInResponse(response);
                        break;
                    case SIGNUP:
                        signUpResponse(response);
                        break;
                    case GET_LEADERBOARD:
                        getLeaderboardResponse(response);
                        break;
                    case GET_ONLINE_PLAYERS:
                        getOnlinePlayersResponse(response);
                        break;
                    case INVETATION_SEND:
                        invitationSendResponse(response);
                        break;
                    case INVETATION_REPLY:
                        invitationReplyResponse(response);
                        break;
                    case ADD_MOVE:
                        addMoveResponse(response);
                        break;
                    case SEND_MESSAGE:
                        sendMessageResponse(response); 
                        break;
                    case REPLAY_REPLY:
                        replayReplyResponse(response);
                        break;
                }

            } catch (IOException ex) {
                Logger.getLogger(Session.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }


    //this function needs a server to connect and check for the msg type
    private void MessageHandler(JSONObject message){
        String type = (String) message.get("type");
    }
    
    private void informationDialog(String title,String information){
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                try {
                    Dialog<ButtonType> dialog = new Dialog<>();
                    FXMLLoader fxmlLoader = new FXMLLoader();
                    fxmlLoader.setLocation(getClass().getResource("/fxml/InvitationDialog.fxml"));
                    DialogPane winner = fxmlLoader.load();
                    dialog.setDialogPane(winner);
                    dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
                    dialog.setTitle(title);
                    Label content = new Label(information);
                    content.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 5px");
                    dialog.setGraphic(content);
                    Stage stage = (Stage) dialog.getDialogPane().getScene().getWindow();
                    stage.getIcons().add(new Image(this.getClass().getResource("/images/icon.png").toString()));
                    dialog.show();
                } catch (IOException ex) {
                    Logger.getLogger(Session.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        });
    }



}
