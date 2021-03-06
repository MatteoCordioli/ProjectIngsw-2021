package it.polimi.ingsw.controller;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.polimi.ingsw.client.data.*;
import it.polimi.ingsw.exception.InvalidStateActionException;
import it.polimi.ingsw.message.clientMessage.*;
import it.polimi.ingsw.model.GameMaster;
import it.polimi.ingsw.model.PlayerState;
import it.polimi.ingsw.model.card.Development;
import it.polimi.ingsw.model.card.Leader;
import it.polimi.ingsw.model.personalBoard.PersonalBoard;
import it.polimi.ingsw.model.personalBoard.cardManager.CardManager;
import it.polimi.ingsw.model.personalBoard.faithTrack.FaithTrack;
import it.polimi.ingsw.model.personalBoard.market.Market;
import it.polimi.ingsw.model.personalBoard.resourceManager.ResourceManager;
import it.polimi.ingsw.model.resource.Resource;
import it.polimi.ingsw.model.resource.ResourceFactory;
import it.polimi.ingsw.model.resource.ResourceType;
import it.polimi.ingsw.server.*;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;

/**
 * Handle all the request that modify the game.
 */
public class Controller {
    private final GameMaster gameMaster;
    private final Match match;

    /**
     * Construct a Controller of a specific match link to a specific GameMaster.
     * @param gameMaster the GameMaster of the game.
     * @param match the reference to the match.
     */
    public Controller(GameMaster gameMaster, Match match) {
        this.gameMaster = gameMaster;
        this.match = match;
        registerAllVirtualClientObserver();
        if (getNumberOfPlayer() == 1){
            registerLorenzoIlMagnificoVC();
        }
    }

    /**
     * Return the number of player.
     * @return the number of player.
     */
    public int getNumberOfPlayer(){
        return gameMaster.getNumberOfPlayer();
    }

    /**
     * Send a error message to a user.
     * @param customMessage the message to send.
     * @param username the username of the user.
     */
    private void sendErrorTo(String customMessage, String username){
        match.sendSinglePlayer(username, new ErrorMessage(customMessage));
    }

    /**
     * Send a error to the current player.
     * @param customMessage the message to send.
     */
    private void sendError(String customMessage){
        match.sendSinglePlayer(getCurrentPlayer(), new ErrorMessage(customMessage));
    }

    /**
     * Return the current player state.
     * @return the current player state.
     */
    public PlayerState getPlayerState(){
        return gameMaster.getPlayerState();
    }

    /**
     * Return the current player.
     * @return the current player.
     */
    public String getCurrentPlayer(){
        return gameMaster.getCurrentPlayer();
    }

    /**
     * Attach all the virtual client to the GameMaster.
     */
    private void registerAllVirtualClientObserver(){
        for (VirtualClient virtualClient: match.getAllPlayers()){
            gameMaster.attachPlayerVC(virtualClient);
        }
    }

    /**
     * Attach Lorenzo il Magnifico virtual client to the GameMaster.
     */
    private void registerLorenzoIlMagnificoVC(){
        VirtualClient lorenzoIlMagnificoVC = new VirtualClient(GameMaster.getNameLorenzo(), match);
        gameMaster.attachLorenzoIlMagnificoVC(lorenzoIlMagnificoVC);
    }

    //UTIL GETTER
    /**
     * Return the current player Personal Board.
     * @return the current player Personal Board.
     */
    private PersonalBoard getPlayerPB() {
        return gameMaster.getCurrentPlayerPersonalBoard();
    }

    /**
     * Return the current player Card Manager.
     * @return the current player Card Manager.
     */
    private CardManager getPlayerCM() {
        return getPlayerPB().getCardManager();
    }

    /**
     * Return the current player Resource Manager.
     * @return the current player Resource Manager.
     */
    private ResourceManager getPlayerRM() {
        return getPlayerPB().getResourceManager();
    }

    /**
     * Return the Market of the match.
     * @return the Market of the match.
     */
    private Market getMarket(){
        return gameMaster.getMarket();
    }

    /**
     * Return true if is the player turn.
     * @param username the username of the player.
     * @return true if is the player turn.
     */
    public boolean isYourTurn(String username){
        if(!username.equals(getCurrentPlayer())){
            sendErrorTo(ErrorType.NOT_YOUR_TURN.getMessage(), username);
            return false;
        }
        return true;
    }

    //UTIL
    /**
     * Handle the request of next turn.
     */
    public void nextTurn() {
        do {
            try {
                //in case of player disconnection during is turn before normal action
                if (match.getActivePlayers().stream()
                        .map(VirtualClient::getUsername)
                        .noneMatch(x -> x.equals(gameMaster.getCurrentPlayer()))){
                    gameMaster.onPlayerStateChange(PlayerState.LEADER_MANAGE_AFTER);
                }
                gameMaster.nextPlayer();
            }catch (InvalidStateActionException e){
                sendError(e.getMessage());
            }
        }while(match.isInactive(gameMaster.getCurrentPlayer()));

        getPlayerRM().restoreRM();
        getPlayerCM().restoreCM();

        String currentPlayer = gameMaster.getCurrentPlayer();
        if (match.isReconnected(currentPlayer)){
            match.playerReturnInGame(currentPlayer);
            match.sendSinglePlayer(currentPlayer, reconnectGameMessage(currentPlayer));
        }

        if(gameMaster.isGameEnded()){
            endGame();
        }else{
            saveMatchState();
        }
    }

    /**
     * End the match.
     */
    public void endGame(){
        match.removeMatchFromServer();
    }

    /**
     * Return the Reconnect Game Massage with all the information.
     * @param playerUsername the username of the player.
     * @return the Reconnect Game Massage with all the information.
     */
    public ReconnectGameMessage reconnectGameMessage(String playerUsername){
        ArrayList<String> usernames = match.getUsernames();
        MarketData marketData = gameMaster.getMarket().toMarketData();
        DeckDevData deckDevData = gameMaster.toDeckDevData();
        ArrayList<EffectData> baseProdData = gameMaster.toEffectDataBasePro();
        ArrayList<ModelData> models = new ArrayList<>();
        for (String username : usernames){
            models.add(modelData(username));
        }
        return new ReconnectGameMessage(usernames,marketData,deckDevData,baseProdData,models, playerUsername);
    }

    /**
     * Return the ModelData of a player.
     * @param username the player username.
     * @return the ModelData of a player.
     */
    private ModelData modelData(String username){
        return gameMaster.getPlayerModelData(username);
    }

    //LEADER MANAGING
    /**
     * Handle the request of activation/discard of a leader.
     * @param leaderIndex the index of the leader.
     * @param discard true if the request is to discard.
     */
    public void leaderManage(int leaderIndex, boolean discard){
        try{
            if(discard){
                getPlayerCM().discardLeader(leaderIndex);
            }else{
                getPlayerCM().activateLeader(leaderIndex);
                getPlayerRM().restoreRM();
            }
        }catch (Exception e){
            sendError(e.getMessage());
        }
    }

    //MARKET ACTION
    /**
     * Handle the request of a market action.
     * @param selection the row/column selected.
     * @param isRow true if a row is selected
     */
    public void marketAction(int selection, boolean isRow){
        Market market = getMarket();
        CardManager cardManager = getPlayerCM();
        ResourceManager resourceManager = getPlayerRM();
        try{
            if (isRow){
                market.insertMarbleInRow(selection);
            }else{
                market.insertMarbleInCol(selection);
            }
        }catch (Exception e){
            sendError(e.getMessage());
            return;
        }

        int numOfMarbleEffects = cardManager.howManyMarbleEffects();
        int whiteMarbleDrew = market.getWhiteMarbleDrew();

        if (!(numOfMarbleEffects >= 2 && whiteMarbleDrew > 0)){
            if (numOfMarbleEffects == 1 && whiteMarbleDrew > 0){
                try{
                    market.setWhiteMarbleToTransform(market.getWhiteMarbleDrew());
                    cardManager.getLeaders().stream()
                            .filter(Leader::isActive)
                            .forEach(x -> {
                                try {
                                    x.doActivationEffects(getPlayerState());
                                } catch (Exception ignored) {}
                            });
                }catch (InvalidStateActionException e){
                    sendError(e.getMessage());
                }
            }
            resourceManager.resourceFromMarket(market.getResourceToSend());
            market.reset();
        }else{
            match.sendSinglePlayer(getCurrentPlayer(),
                    new WhiteMarbleConversionRequest(whiteMarbleDrew, cardManager.mapOfMarbleEffect()));
        }
    }

    /**
     * Handle the request of a conversion of white marble from a leader.
     * @param leaderIndex the index of the leader.
     * @param numOfWhiteMarble the num of marble to convert.
     */
    public void leaderWhiteMarbleConversion(int leaderIndex, int numOfWhiteMarble){
        CardManager cardManager = getPlayerCM();
        Market market = getMarket();
        if(cardManager.howManyMarbleEffects() <= 0){
            sendError("You don't have leader with marble effects");
            return;
        }

        try{
            market.setWhiteMarbleToTransform(numOfWhiteMarble);
            cardManager.activateLeaderInfinite(leaderIndex, getPlayerState());

            if(market.getWhiteMarbleDrew() == 0){
                getPlayerRM().resourceFromMarket(market.getResourceToSend());
                market.reset();
            }
        }catch (Exception e){
            sendError(e.getMessage());
        }
    }

    /**
     * Handle the request to discard the resources got from market.
     */
    public void clearBufferFromMarket(){
        try {
            getPlayerRM().discardResourcesFromMarket();
            controlBufferStatus();
        }catch (InvalidStateActionException e){
            sendError(e.getMessage());
        }
    }

    //BUY DEVELOPMENT CARD
    /**
     * Handle the request to buy a development card.
     * @param row the row of the development's deck.
     * @param col the column of the development's deck.
     * @param locateSlot the index of the card slot to put the card.
     */
    public void developmentAction(int row, int col, int locateSlot){
        Development card;
        CardManager cardManager = getPlayerCM();
        try {
            card = gameMaster.getDeckDevelopmentCard(row, col);
            cardManager.addDevCardTo(card, locateSlot);
            cardManager.setDeckBufferInfo(row, col);
            card.checkRequirements();
        } catch (Exception e) {
            sendError(e.getMessage());
            return;
        }
        controlBufferStatus();
    }

    //PRODUCTION ACTION
    /**
     * Handle the production of a development card.
     * @param cardSlot the index of the card slot.
     */
    public void normalProductionAction(int cardSlot){
        try {
            getPlayerCM().developmentProduce(cardSlot);
        } catch (Exception e) {
            sendError(e.getMessage());
        }
    }

    /**
     * Handle the production of the base production.
     */
    public void baseProduction(){
        try {
            getPlayerCM().baseProductionProduce();
        } catch (Exception e) {
            sendError(e.getMessage());
        }
    }

    /**
     * Handle the production of a leader.
     * @param leaderIndex the index of the leader.
     */
    public void leaderProductionAction(int leaderIndex){
        CardManager cardManager = getPlayerCM();
        if (cardManager.howManyProductionEffects() <= 0){
            sendError("You don't have leader with production effect");
        }else{
            try {
                cardManager.activateLeaderEffect(leaderIndex, getPlayerState());
            } catch (Exception e) {
                sendError(e.getMessage());
            }
        }
    }

    /**
     * Handle the stop of the production.
     */
    public void stopProductionCardSelection(){
        try {
            getPlayerRM().stopProduction();
        } catch (InvalidStateActionException e) {
            sendError(e.getMessage());
        }
    }

    //ANY

    /**
     * Handle the request of a any resource conversion.
     * @param resources the resources to convert into.
     */
    public void anyConversion(ArrayList<Resource> resources){
        PlayerState state = getPlayerState();
        try {
            if (state == PlayerState.ANY_PRODUCE_COST_CONVERSION){
                getPlayerRM().convertAnyRequirement(resources, false);
            }else if (state == PlayerState.ANY_PRODUCE_PROFIT_CONVERSION){
                getPlayerRM().convertAnyProductionProfit(resources);
            }else if(state == PlayerState.ANY_BUY_DEV_CONVERSION){
                getPlayerRM().convertAnyRequirement(resources, true);
            }else{
                sendError(ErrorType.INVALID_ACTION.getMessage());
            }
        }catch (Exception e){
            sendError(e.getMessage());
        }
    }

    //WAREHOUSE

    /**
     * Control the status of the buffer.
     */
    private void controlBufferStatus(){
        ResourceManager resourceManager = getPlayerRM();
        if(resourceManager.getBufferSize() == 0){
            switch (getPlayerState()){
                case MARKET_RESOURCE_POSITIONING:
                    resourceManager.applyFaithPoints();
                    break;
                case BUY_DEV_RESOURCE_REMOVING:
                    getPlayerCM().emptyCardSlotBuffer();
                    break;
                case PRODUCTION_RESOURCE_REMOVING:
                    resourceManager.doProduction();
                    resourceManager.applyFaithPoints();
                    getPlayerCM().restoreCM();
                    break;
                default:
                    return;
            }
            resourceManager.restoreRM();
            gameMaster.onPlayerStateChange(PlayerState.LEADER_MANAGE_AFTER);
        }
    }

    /**
     * Handle the request to subtract a resource to the strongbox.
     * @param resource the resource to subtract.
     */
    public void subToStrongbox(Resource resource){
        ResourceManager resourceManager = getPlayerRM();
        try {
            resourceManager.subToBuffer(resource);
        } catch (Exception e) {
            sendError(e.getMessage());
            return;
        }
        try {
            resourceManager.subToStrongbox(resource);
        } catch (Exception e) {
            resourceManager.addToBuffer(resource);
            sendError(e.getMessage());
        }
        controlBufferStatus();
    }

    /**
     * Handle the request of adding a resource in a depot.
     * @param resource the resource to add.
     * @param index the index of the depot.
     * @param isNormalDepot true if is a normal depot.
     */
    public void depotModify(Resource resource, int index, boolean isNormalDepot){
        ResourceManager resourceManager = getPlayerRM();
        try {
            resourceManager.subToBuffer(resource);
        } catch (Exception e) {
            sendError(e.getMessage());
            return;
        }
        try{
            switch (getPlayerState()){
                case MARKET_RESOURCE_POSITIONING:
                    resourceManager.addToWarehouse(isNormalDepot, index, resource);
                    break;
                case BUY_DEV_RESOURCE_REMOVING:
                case PRODUCTION_RESOURCE_REMOVING:
                    resourceManager.subToWarehouse(isNormalDepot, index, resource);
                    break;
                default:
                    sendError(ErrorType.INVALID_ACTION.getMessage());
                    break;
            }
        }catch (Exception e){
            resourceManager.addToBuffer(resource);
            sendError(e.getMessage());
        }
        controlBufferStatus();
    }

    /**
     * Handle the request of a switch between two depot.
     * @param from the index of the starting depot
     * @param isFromLeaderDepot true if is a leader depot.
     * @param to the index of the ending depot.
     * @param isToLeaderDepot true if is a leader depot.
     */
    public void switchDepots(int from, boolean isFromLeaderDepot, int to, boolean isToLeaderDepot){
        try {
            getPlayerRM().switchResourceFromDepotToDepot(from, isFromLeaderDepot, to, isToLeaderDepot);
        } catch (Exception e) {
            sendError(e.getMessage());
        }
    }

    //SETUP
    /**
     * Return true the player has finished leader setUp.
     * @param username the username of the player.
     * @return true if the player has finished leader setUp.
     */
    private boolean hasFinishedLeaderSetUp(String username){
        CardManager cardManager = gameMaster.getPlayerPersonalBoard(username).getCardManager();
        return cardManager.getLeaders().size() == 2;
    }

    /**
     * Automatically discard a leader during setUp.
     * @param username the username of the player.
     */
    public void autoDiscardLeaderSetUp( String username){
        while (!hasFinishedLeaderSetUp(username)){
            discardLeaderSetUp(0,username);
        }
        autoInsertSetUpResources(username);
    }

    /**
     * Handle the discard of leader during setUp.
     * @param leaderIndex the index of the leader to discard.
     * @param username the username of the player.
     */
    public void discardLeaderSetUp(int leaderIndex, String username){
        CardManager playerCardManager = gameMaster.getPlayerPersonalBoard(username).getCardManager();
        try {
            playerCardManager.discardLeaderSetUp(leaderIndex);
            if (hasFinishedLeaderSetUp(username)){
                FaithTrack playerFaithTrack = gameMaster.getPlayerPersonalBoard(username).getFaithTrack();
                switch (gameMaster.getPlayerPosition(username)){
                    case 0:
                        match.sendSinglePlayer(username, new AnyConversionRequest(0));
                        match.getPlayer(username).ifPresent(x->x.getClient().setState(HandlerState.WAITING_TO_BE_IN_MATCH));
                        if(isFinishedSetup()){
                            match.getAllPlayers().forEach(x -> x.getClient().setState(HandlerState.IN_MATCH));
                            match.sendAllPlayers(new MatchStart());
                            nextTurn();
                        }
                        break;
                    case 1:
                        match.sendSinglePlayer(username, new AnyConversionRequest(1));
                        match.getPlayer(username).ifPresent(x->x.getClient().setState(HandlerState.RESOURCE_SETUP));
                        break;
                    case 2:
                        match.sendSinglePlayer(username, new AnyConversionRequest(1));
                        playerFaithTrack.movePlayer(1);
                        match.getPlayer(username).ifPresent(x->x.getClient().setState(HandlerState.RESOURCE_SETUP));
                        break;
                    case 3:
                        match.sendSinglePlayer(username,new AnyConversionRequest(2));
                        playerFaithTrack.movePlayer(1);
                        match.getPlayer(username).ifPresent(x->x.getClient().setState(HandlerState.RESOURCE_SETUP));
                        break;
                }
            }

        }catch (Exception e) {
            sendErrorTo(e.getMessage(), username);
        }
    }

    /**
     * Return true if all players have finished setUp.
     * @return true if all players have finished setUp.
     */
    private boolean isFinishedSetup(){
        for(VirtualClient player : match.getAllPlayers()){
            if(player.getClient().getState()!= HandlerState.WAITING_TO_BE_IN_MATCH)
                return false;
        }
        return true;
    }

    /**
     * Automatically insert the resources during setUp.
     * @param username the username of the player.
     */
    public void autoInsertSetUpResources(String username){
        ArrayList<Resource> resources = new ArrayList<>();
        switch (gameMaster.getPlayerPosition(username)){
            case 1:
            case 2:
                resources.add(ResourceFactory.createResource(ResourceType.COIN,1));
                insertSetUpResources(resources,username);
                break;
            case 3:
                resources.add(ResourceFactory.createResource(ResourceType.COIN,2));
                insertSetUpResources(resources,username);
                break;
        }
    }

    /**
     * Handle the insertion of resources during setUp.
     * @param resources the resources to add.
     * @param username the username of the player.
     */
    public void insertSetUpResources(ArrayList<Resource> resources, String username){
        ResourceManager resourceManager = gameMaster.getPlayerPersonalBoard(username).getResourceManager();
        int sizeResponse = resources.stream().mapToInt(Resource::getValue).sum();
        try{
            switch (gameMaster.getPlayerPosition(username)){
                case 1:
                case 2:
                    if (sizeResponse == 1){
                        resourceManager.addToWarehouse(true, 0, resources.get(0));
                    }else{
                        sendErrorTo("Too many resources sent", username);
                        return;
                    }
                    break;
                case 3:
                    if (sizeResponse == 2 && resources.size() == 1){
                        resourceManager.addToWarehouse(true, 1, resources.get(0));
                    }else if(sizeResponse == 2 && resources.size() == 2){
                        resourceManager.addToWarehouse(true, 0, resources.get(0));
                        resourceManager.addToWarehouse(true, 1, resources.get(1));
                    }else{
                        sendErrorTo("Too many resources sent", username);
                        return;
                    }
                    break;
            }
            match.getPlayer(username).ifPresent(y -> y.getClient().setState(HandlerState.WAITING_TO_BE_IN_MATCH));
            if(isFinishedSetup()){
                match.getAllPlayers().forEach(x -> x.getClient().setState(HandlerState.IN_MATCH));
                match.sendAllPlayers(new MatchStart());
                nextTurn();
            }
        }catch (Exception e){
            sendErrorTo(e.getMessage(), username);
        }
    }

    //--SAVE GAME
    /**
     * Save the current state of the match.
     */
    public void saveMatchState(){
        if (!Files.isDirectory(Paths.get(Server.MATCH_SAVING_PATH))) {
            try {
                Files.createDirectories(Paths.get(Server.MATCH_SAVING_PATH));
            } catch (IOException e) {
                System.out.println("Error creating match saving directory");
                e.printStackTrace();
            }
        }

        String fileName = Server.MATCH_SAVING_PATH +"/"+ match.getMatchID()+ ".txt";
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
            mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
            MatchData matchSave = new MatchData(match, gameMaster);

            FileWriter file = new FileWriter(fileName);
            file.write(mapper.writeValueAsString(matchSave));
            file.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //--cheat
    /**
     * Add 20 of all resources to the strongbox of all players.
     */
    public void cheat(){
        ArrayList<Resource> res= new ArrayList<>();
        res.add(ResourceFactory.createResource(ResourceType.SHIELD, 20));
        res.add(ResourceFactory.createResource(ResourceType.STONE, 20));
        res.add(ResourceFactory.createResource(ResourceType.COIN, 20));
        res.add(ResourceFactory.createResource(ResourceType.SERVANT, 20));
        for(PersonalBoard pb :gameMaster.getAllPersonalBoard()){
            ResourceManager rm = pb.getResourceManager();
            res.forEach(rm::addToStrongbox);
            rm.notifyAllObservers(x -> x.strongboxUpdate(rm.getStrongbox().getResources()));
            rm.restoreRM();
        }
    }
}
