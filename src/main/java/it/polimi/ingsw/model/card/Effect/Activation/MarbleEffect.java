package it.polimi.ingsw.model.card.Effect.Activation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import it.polimi.ingsw.client.data.EffectData;
import it.polimi.ingsw.client.data.EffectType;
import it.polimi.ingsw.client.data.ResourceData;
import it.polimi.ingsw.model.PlayerState;
import it.polimi.ingsw.model.card.Effect.Effect;
import it.polimi.ingsw.model.personalBoard.market.Market;
import it.polimi.ingsw.model.personalBoard.resourceManager.ResourceManager;
import it.polimi.ingsw.model.resource.Resource;
import it.polimi.ingsw.model.resource.ResourceFactory;
import it.polimi.ingsw.model.resource.ResourceType;

import java.util.ArrayList;
import java.util.stream.Collectors;


/**
 * MarbleEffect class defines the effect that concern marbles
 */
public class MarbleEffect implements Effect {
    private final ArrayList<Resource> transformIn;
    private Market market = null;

    /**
     * Constructor MarbleEffect creates a new MarbleEffect instance
     * @param transformIn of type ArrayList - the resources which we will transform each white marble drew
     */
    @JsonCreator
    public MarbleEffect(@JsonProperty("transformIn") ArrayList<Resource> transformIn) {
        this.transformIn = transformIn;
    }

    /**
     * Method doEffect is in charge of pass all the resources to
     * the market based on how many white marble the user haw drawn
     * @param playerState of type State - defines the state of the turn, in this case must be MARKET_STATE
     */
    @Override
    public void doEffect(PlayerState playerState) {
        if (playerState == PlayerState.WHITE_MARBLE_CONVERSION){
            int whiteMarble = market.getWhiteMarbleToTransform();
            market.insertLeaderResources(transformIn.stream()
                    .map(x -> ResourceFactory.createResource(x.getType(), x.getValue()*whiteMarble))
                    .collect(Collectors.toCollection(ArrayList::new)));
        }
    }

    @Override
    public void discardEffect() {

    }

    /**
     * Method attachMarket attach the market
     * @param market of type Market is the instance of the market of the game
     */
    @Override
    public void attachMarket(Market market) {
        this.market = market;
    }

    /**
     * Method attachResourceManager does nothing because MarbleEffect doesn't need
     * any reference to it
     * @param resourceManager of type ResourceManager is an instance of the resource manager of the player
     */
    @Override
    public void attachResourceManager(ResourceManager resourceManager) {}

    public ArrayList<Resource> getTransformIn() {
        return transformIn;
    }

    @Override
    public EffectData toEffectData() {
        String description = "Marble effect: ";
        ArrayList<ResourceData> whiteMarble = new ArrayList<>();
        whiteMarble.add(new ResourceData(ResourceType.ANY));

        ArrayList<ResourceData> transformInto = transformIn.stream().map(Resource::toClient).collect(Collectors.toCollection(ArrayList::new));
        return new EffectData(EffectType.MARBLE,description,whiteMarble,transformInto);
    }



}
