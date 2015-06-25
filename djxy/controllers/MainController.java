package djxy.controllers;

import djxy.controllers.NetworkController.PlayerConnection;
import djxy.models.ComponentManager;
import djxy.models.Form;
import djxy.models.component.Component;
import djxy.models.component.ComponentState;
import djxy.models.component.ComponentType;
import djxy.models.component.ComponentUpdate;
import djxy.models.component.Font;
import djxy.models.component.Position;
import djxy.models.component.Side;
import djxy.models.component.TextAlignment;
import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.spongepowered.api.Game;
import org.spongepowered.api.event.entity.player.PlayerJoinEvent;
import org.spongepowered.api.event.entity.player.PlayerQuitEvent;
import org.spongepowered.api.event.Subscribe;
import org.spongepowered.api.event.state.ConstructionEvent;
import org.spongepowered.api.event.state.ServerStartingEvent;
import org.spongepowered.api.event.state.ServerStoppingEvent;
import org.spongepowered.api.plugin.Plugin;

@Plugin(id = "MinecraftGUIServer", name = "Minecraft GUI Server", version = "1.0")
public final class MainController {

    public static final String PATH = "mods/MinecraftGUI";
    private static MainController instance = null;
    
    public static MainController getInstance(){
        return instance;
    }
    
    //**************************************************************************
    //**************************************************************************
    
    private final ArrayList<ComponentManager> componentManagers;
    private final HashMap<String, ArrayList<ComponentManager>> componentManagersPerComponent;
    private final HashMap<String, Boolean> playersAuthenticated;
    private final NetworkController networkController;
    private final ComponentLocationController componentLocationController;
    private final AuthenticationManager authenticationManager;
    private boolean canAddComponentManager = false;
    private boolean playerNeedAuthentication = false;

    public MainController() throws Exception {
        if(instance != null)
            throw new Exception();
        
        instance = this;
        playersAuthenticated = new HashMap<>();
        componentManagers = new ArrayList<>();
        componentManagersPerComponent = new HashMap<>();
        authenticationManager = new AuthenticationManager();
        networkController = new NetworkController(this, 20000);
        componentLocationController = new ComponentLocationController();
    }
    
    public void addComponentManager(ComponentManager manager){
        if(canAddComponentManager){
            playerNeedAuthentication = playerNeedAuthentication == false?manager.isPlayerNeedAuthentication():true;
            
            componentManagers.add(manager);
            
            for(String componentId : manager.getListOfComponentIdToListen()){
                ArrayList<ComponentManager> componentManagers = componentManagersPerComponent.get(componentId);
                
                if(componentManagers == null){
                    componentManagersPerComponent.put(componentId, new ArrayList<ComponentManager>());
                    componentManagers = componentManagersPerComponent.get(componentId);
                }
                
                componentManagers.add(manager);
            }
        }
    }
    
    public void removeComponent(String playerUUID, String componentId){
        Boolean auth = playersAuthenticated.get(playerUUID);
        
        if(auth != null && auth == true)
            networkController.sendCommandTo(playerUUID, createCommandRemoveComponent(componentId));
    }
    
    public void updateComponent(String playerUUID, ComponentUpdate componentUpdate){
        Boolean auth = playersAuthenticated.get(playerUUID);
        
        if(auth != null && auth == true){
            componentLocationController.prepareToSendComponent(playerUUID, componentUpdate);
            networkController.sendCommandTo(playerUUID, componentUpdate.getCommands());
        }
    }
    
    public void createComponent(String playerUUID, Component component){
        Boolean auth = playersAuthenticated.get(playerUUID);
        
        if(auth != null && auth == true){
            componentLocationController.prepareToSendComponent(playerUUID, component.getComponentUpdate());
            networkController.sendCommandTo(playerUUID, component.getCommands());
        }
    }
    
    private void clearPlayerScreen(String playerUUID){
        networkController.sendCommandTo(playerUUID, createCommandClearScreen());
    }
    
    protected void receiveCommand(PlayerConnection playerConnection, JSONObject object){
        String command[] = ((String) object.get("Command")).split(" ");
        
        if(command.length == 2){
            if(command[0].equals("FORM")){
                if(command[1].equals("INPUT")){
                    if(playersAuthenticated.get(playerConnection.getPlayerUUID()).equals(Boolean.TRUE))
                        callReceiveInputFormEvent(playerConnection, object);
                    else
                        authenticationManager.receiveForm(this, playerConnection.getPlayerUUID(), new Form(object));
                }
            }
            else if(command[0].equals("SET")){
                if(command[1].equals("LOCATION_RELATIVE")){
                    setComponentLocationRelative(playerConnection.getPlayerUUID(), object);
                }
            }
            else if(command[0].equals("SCREEN")){
                if(command[1].equals("CLEAR")){
                    
                }
            }
        }
    }
    
    protected void newPlayerConnected(PlayerConnection playerConnection){
        playersAuthenticated.put(playerConnection.getPlayerUUID(), false);
        
        if(playerNeedAuthentication)
            authenticationManager.initPlayerGUI(this, playerConnection.getPlayerUUID());
        else{
            playersAuthenticated.put(playerConnection.getPlayerUUID(), true);
            callInitPlayerGUIEvent(playerConnection);
        }
    }
    
    private void setComponentLocationRelative(String playerUUID, JSONObject object){
        String componentId = (String) object.get("ComponentId");
        int x = (int) (long) object.get("XRelative");
        int y = (int) (long) object.get("YRelative");
        
        componentLocationController.setComponentLocationRelative(playerUUID, componentId, x, y);
    }
    
    private void callInitPlayerGUIEvent(PlayerConnection playerConnection){
        for(ComponentManager manager : componentManagers){
            networkController.sendCommandTo(playerConnection.getPlayerUUID(), createCommandsDownloadImage(manager));
            manager.initPlayerGUI(this, playerConnection.getPlayerUUID());
        }
    }
    
    private JSONArray createCommandsDownloadImage(ComponentManager manager){
        JSONArray array = new JSONArray();
        
        for(Map.Entry pairs : manager.getImagesToDownload().entrySet()){
            JSONObject object = new JSONObject();
            
            object.put("Command", "DOWNLOAD IMAGE");
            object.put("Url", pairs.getKey());
            object.put("File", pairs.getValue());
            
            array.add(object);
        }
        
        return array;
    }
    
    private void callReceiveInputFormEvent(PlayerConnection playerConnection, JSONObject object){
        String buttonId = (String) object.get("ButtonId");
        ArrayList<ComponentManager> managers = componentManagersPerComponent.get(buttonId);
        
        if(managers != null){
            Form form = new Form(object);
            
            for(ComponentManager manager : managers)
                manager.receiveForm(this, playerConnection.getPlayerUUID(), form);
        }
    }
    
    private void initPath(){
        File file = new File(PATH);
        file.mkdirs();
    }
    
    //**************************************************************************
    //**************************************************************************
    
    private Game game;
    
    //EVENTS
    @Subscribe
    public void onConstructionEvent(ConstructionEvent event) {
        game = event.getGame();
        initPath();
        componentLocationController.load();
        canAddComponentManager = true;
    }

    @Subscribe
    public void onServerStartingEvent(ServerStartingEvent event) {
        canAddComponentManager = false;
        networkController.start();
    }
    
    @Subscribe
    public void onServerStoppingEvent(ServerStoppingEvent event) {
        networkController.closeServer();
        componentLocationController.save();
    }
    
    @Subscribe
    public void onPlayerJoinEvent(PlayerJoinEvent event){
        networkController.addPlayerConnected(event.getUser().getUniqueId().toString());
    }
    
    @Subscribe
    public void onPlayerQuitEvent(PlayerQuitEvent event){
        networkController.closePlayer(event.getUser().getUniqueId().toString());
    }
    
    //**************************************************************************
    //**************************************************************************
    
    private JSONArray createCommandClearScreen(){
        JSONArray array = new JSONArray();
        JSONObject object = new JSONObject();
        object.put("Command", "CLEAR SCREEN");
        
        array.add(object);
        
        return array;
    }
    
    private JSONArray createCommandRemoveComponent(String componentId){
        JSONArray array = new JSONArray();
        JSONObject object = new JSONObject();
        object.put("Command", "REMOVE COMPONENT");
        object.put("ComponentId", componentId);
        
        array.add(object);
        
        return array;
    }
    
    //**************************************************************************
    //**************************************************************************
    
    private class AuthenticationManager extends ComponentManager {
        
        private final static int maxTry = 3;
        private final static String panelId = "@AUTH_PANEL";
        private final static String buttonId = "@AUTH_BUTTON";
        private final static String inputId = "@AUTH_INPUT";
        
        private final HashMap<String, Integer> playersTrying;
        private final HashMap<String, String> playersCode;

        public AuthenticationManager() {
            super(true);
            playersTrying = new HashMap<>();
            playersCode = new HashMap<>();
        }

        @Override
        public void initPlayerGUI(MainController mainController, String playerUUID) {
            Component panel = new Component(ComponentType.PANEL, panelId);
            
            panel.getComponentUpdate().setPosition(Position.MIDDLE);
            panel.getComponentUpdate().setXRelative(-.5f);
            panel.getComponentUpdate().setYRelative(-.5f);
            panel.getComponentUpdate().setWidth(ComponentState.NORMAL, 200);
            panel.getComponentUpdate().setHeight(ComponentState.NORMAL, 62);
            panel.getComponentUpdate().setBackground(ComponentState.NORMAL, new Color(22, 73, 154, 255));
            panel.getComponentUpdate().setBorderSide(ComponentState.NORMAL, new Side(true, true, true, true));
            panel.getComponentUpdate().setBorderSize(ComponentState.NORMAL, 2);
            panel.getComponentUpdate().setBorderColor(ComponentState.NORMAL, new Color(69, 90, 100, 255));
            
            Component input = new Component(ComponentType.INPUT_NUMERIC_NO_DECIMAL, inputId, panel.getId());
            
            input.getComponentUpdate().setPosition(Position.MIDDLE);
            input.getComponentUpdate().setXRelative(-.5f);
            input.getComponentUpdate().setYRelative(-27);
            input.getComponentUpdate().setWidth(ComponentState.NORMAL, 160);
            input.getComponentUpdate().setHeight(ComponentState.NORMAL, 21);
            input.getComponentUpdate().setBackground(ComponentState.NORMAL, new Color(110, 126, 148, 255));
            input.getComponentUpdate().setBorderSide(ComponentState.NORMAL, new Side(false, false, false, true));
            input.getComponentUpdate().setBorderSize(ComponentState.NORMAL, 1);
            input.getComponentUpdate().setBorderColor(ComponentState.NORMAL, new Color(182, 182, 182, 255));
            input.getComponentUpdate().setTextAlignment(ComponentState.NORMAL, TextAlignment.MIDDLE);
            input.getComponentUpdate().setTextColor(ComponentState.NORMAL, new Color(33, 33, 33, 255));
            input.getComponentUpdate().setFont(ComponentState.NORMAL, Font.NORMAL);
            input.getComponentUpdate().setFont(ComponentState.HOVER, Font.SHADOW);
            
            Component button = new Component(ComponentType.BUTTON, buttonId, panel.getId());
            
            button.getComponentUpdate().setPosition(Position.MIDDLE);
            button.getComponentUpdate().setXRelative(-.5f);
            button.getComponentUpdate().setYRelative(6);
            button.getComponentUpdate().setWidth(ComponentState.NORMAL, 75);
            button.getComponentUpdate().setHeight(ComponentState.NORMAL, 21);
            button.getComponentUpdate().setBackground(ComponentState.NORMAL, new Color(27, 88, 184, 255));
            button.getComponentUpdate().setBackground(ComponentState.HOVER, new Color(31, 174, 255, 255));
            button.getComponentUpdate().setBorderSide(ComponentState.NORMAL, new Side(true, true, true, true));
            button.getComponentUpdate().setBorderSize(ComponentState.NORMAL, 2);
            button.getComponentUpdate().setBorderColor(ComponentState.NORMAL, new Color(182, 182, 182, 255));
            button.getComponentUpdate().setValue("Send code");
            button.getComponentUpdate().setTextAlignment(ComponentState.NORMAL, TextAlignment.MIDDLE);
            button.getComponentUpdate().setTextColor(ComponentState.NORMAL, new Color(255, 0, 0, 255));
            button.getComponentUpdate().addInput(input.getId());
            
            
            networkController.sendCommandTo(playerUUID, panel.getCommands());
            networkController.sendCommandTo(playerUUID, input.getCommands());
            networkController.sendCommandTo(playerUUID, button.getCommands());
            
            initPlayerAuth(playerUUID);
        }

        @Override
        public void receiveForm(MainController mainController, String playerUUID, Form form) {
            String codeReceived = form.getInputs().get(inputId);
            String playerCode = playersCode.get(playerUUID);
            
            if(codeReceived.equals(playerCode)){
                playersAuthenticated.put(playerUUID, true);
                removeComponent(playerUUID, panelId);
                callInitPlayerGUIEvent(networkController.getPlayerConnection(playerUUID));
            }
            else{
                playersTrying.put(playerUUID, playersTrying.get(playerUUID)+1);
                
                
                if(playersTrying.get(playerUUID) == maxTry)
                    networkController.closePlayer(playerUUID);
            }
        }
        
        private void initPlayerAuth(String playerUUID){
            String code = (new Random().nextInt(900000)+100000)+"";
            playersTrying.put(playerUUID, 0);
            playersCode.put(playerUUID, code);
            System.out.println(code);
        }
        
    }
    
}