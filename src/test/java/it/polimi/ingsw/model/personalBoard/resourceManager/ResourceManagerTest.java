package it.polimi.ingsw.model.personalBoard.resourceManager;

import it.polimi.ingsw.exception.*;
import it.polimi.ingsw.model.resource.Resource;
import it.polimi.ingsw.model.resource.ResourceFactory;
import it.polimi.ingsw.model.resource.ResourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class ResourceManagerTest {

    ResourceManager rs = new ResourceManager();

    @BeforeEach
    void Init() {
        rs.addToStrongbox(ResourceFactory.createResource(ResourceType.COIN, 5));
        rs.addToStrongbox(ResourceFactory.createResource(ResourceType.SERVANT, 3));
        rs.addToStrongbox(ResourceFactory.createResource(ResourceType.STONE, 2));

        assertDoesNotThrow(() -> rs.addToWarehouse(true, 0, ResourceFactory.createResource(ResourceType.COIN, 1)));
        assertDoesNotThrow(() -> rs.addToWarehouse(true, 1, ResourceFactory.createResource(ResourceType.SHIELD, 1)));

        rs.restoreRM();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void addToWarehouse(int index) {
        switch (index) {
            case 0:
                //TooMuchResourceDepotException
                Resource resourceTooBig = ResourceFactory.createResource(ResourceType.SHIELD, 4);
                assertThrows(TooMuchResourceDepotException.class, () -> rs.addToWarehouse(true, 1, resourceTooBig));
                break;
            case 1:
                //InvalidOrganizationWarehouseException
                assertThrows(InvalidOrganizationWarehouseException.class, () -> rs.addToWarehouse(true, 2, ResourceFactory.createResource(ResourceType.COIN, 1)));
                break;
            case 2:
                assertDoesNotThrow(() -> rs.addToWarehouse(true, 1, ResourceFactory.createResource(ResourceType.SHIELD, 1)));
                assertDoesNotThrow(() -> rs.addToWarehouse(true, 2, ResourceFactory.createResource(ResourceType.SERVANT, 2)));
                Resource r1 = ResourceFactory.createResource(ResourceType.SERVANT, 2);

                ArrayList<Depot> depots = new ArrayList<>();
                depots.add(new Depot(r1, 10));

                rs.addLeaderDepot(depots);
                assertDoesNotThrow(() -> rs.addToWarehouse(false, 0, ResourceFactory.createResource(ResourceType.SERVANT, 2)));
                break;
        }
    }

    @Test
    void doProduction() {
        ArrayList<Resource> arrRes = new ArrayList<>();
        arrRes.add(ResourceFactory.createResource(ResourceType.COIN, 5));
        rs.addToResourcesToProduce(arrRes);
        rs.doProduction();
        assertEquals(10, rs.getStrongbox().howManyDoIHave(ResourceType.COIN));
    }

    @Test
    void addToStrongbox() {
        rs.addToStrongbox(ResourceFactory.createResource(ResourceType.COIN, 5));
        assertEquals(10, rs.getStrongbox().howManyDoIHave(ResourceType.COIN));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void subToWarehouse(int index) {
        switch (index) {
            case 0:
                //TooMuchResourceDepotException
                Resource resourceTooBig = ResourceFactory.createResource(ResourceType.SHIELD, 4);
                assertThrows(NegativeResourceException.class, () -> rs.subToWarehouse(true, 1, resourceTooBig));
                break;
            case 1:
                //InvalidOrganizationWarehouseException
                assertThrows(InvalidOrganizationWarehouseException.class, () -> rs.subToWarehouse(true, 1, ResourceFactory.createResource(ResourceType.COIN, 1)));
                break;
            case 2:
                assertDoesNotThrow(() -> rs.subToWarehouse(true, 1, ResourceFactory.createResource(ResourceType.SHIELD, 1)));

                Resource r1 = ResourceFactory.createResource(ResourceType.SERVANT, 2);

                ArrayList<Depot> depots = new ArrayList<>();
                depots.add(new Depot(r1, 10));

                rs.addLeaderDepot(depots);
                assertDoesNotThrow(() -> rs.subToWarehouse(false, 0, ResourceFactory.createResource(ResourceType.SERVANT, 1)));
                break;
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void subtractToStrongbox(int index) {
        switch (index) {
            case 0:
                Resource resourceTooBig = ResourceFactory.createResource(ResourceType.SHIELD, 4);
                assertThrows(NegativeResourceException.class, () -> rs.subToStrongbox(resourceTooBig));
                break;
            case 1:
                assertDoesNotThrow(() -> rs.subToStrongbox(ResourceFactory.createResource(ResourceType.COIN, 3)));
                assertEquals(2, rs.getStrongbox().howManyDoIHave(ResourceType.COIN));
                break;
        }

    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void canIAfford(int index) {
        ArrayList<Resource> disc = new ArrayList<>();
        disc.add(ResourceFactory.createResource(ResourceType.COIN, 1));
        rs.addDiscount(disc);
        rs.restoreRM();
        switch (index) {
            case 0:
                ArrayList<Resource> resourcesCosts1 = new ArrayList<>();
                resourcesCosts1.add(ResourceFactory.createResource(ResourceType.COIN, 7));
                resourcesCosts1.add(ResourceFactory.createResource(ResourceType.SERVANT, 3));
                resourcesCosts1.add(ResourceFactory.createResource(ResourceType.STONE, 1));

                assertDoesNotThrow(() -> rs.canIAfford(resourcesCosts1, true));
                break;
            case 1:
                ArrayList<Resource> resourcesCosts2 = new ArrayList<>();
                resourcesCosts2.add(ResourceFactory.createResource(ResourceType.STONE, 1));
                resourcesCosts2.add(ResourceFactory.createResource(ResourceType.ANY, 2));
                assertDoesNotThrow(() -> rs.canIAfford(resourcesCosts2, false));
                break;
            case 2:
                ArrayList<Resource> resourcesCosts3 = new ArrayList<>();
                resourcesCosts3.add(ResourceFactory.createResource(ResourceType.STONE, 1));
                resourcesCosts3.add(ResourceFactory.createResource(ResourceType.COIN, 10));
                assertThrows(NotEnoughRequirementException.class, () -> rs.canIAfford(resourcesCosts3, false));
                break;
        }
    }

    @Test
    void switchResourceFromDepotToDepot() {
        assertDoesNotThrow(() -> rs.switchResourceFromDepotToDepot(0, true, 1, true));
    }

    @Test
    void switchLeaderDepot() {
        ArrayList<Depot> depots = new ArrayList<>();
        depots.add(new Depot(ResourceFactory.createResource(ResourceType.SHIELD, 2), 4));
        rs.addLeaderDepot(depots);

        assertDoesNotThrow(() -> rs.switchResourceFromDepotToDepot(0, false, 1, true));
    }

    @Test
    void convertAnyRequirement() {
        ArrayList<Resource> anyOrFaith = new ArrayList<>() {{
            add(ResourceFactory.createResource(ResourceType.ANY, 2));
        }};

        ArrayList<Resource> normalResources = new ArrayList<>() {{
            add(ResourceFactory.createResource(ResourceType.COIN, 1));
            add(ResourceFactory.createResource(ResourceType.STONE, 1));
        }};


        assertThrows(AnyConversionNotPossible.class, () -> rs.convertAnyRequirement(anyOrFaith, true));
        assertThrows(AnyConversionNotPossible.class, () -> rs.convertAnyRequirement(normalResources, true));
        assertDoesNotThrow(() -> rs.canIAfford(anyOrFaith, false));
        assertDoesNotThrow(() -> rs.convertAnyRequirement(normalResources, false));
        normalResources.get(1).addValue(3);
        anyOrFaith.add(ResourceFactory.createResource(ResourceType.ANY, 5));
        assertDoesNotThrow(() -> rs.canIAfford(anyOrFaith, false));
        assertThrows(AnyConversionNotPossible.class, () -> rs.convertAnyRequirement(normalResources, false));
    }

    @Test
    void convertAnyProductionProfit() {
        ArrayList<Resource> anyOrFaith = new ArrayList<>() {{
            add(ResourceFactory.createResource(ResourceType.ANY, 2));
        }};

        ArrayList<Resource> normalResources = new ArrayList<>() {{
            add(ResourceFactory.createResource(ResourceType.COIN, 1));
            add(ResourceFactory.createResource(ResourceType.STONE, 1));
        }};


        assertThrows(AnyConversionNotPossible.class, () -> rs.convertAnyProductionProfit(anyOrFaith));
        assertThrows(AnyConversionNotPossible.class, () -> rs.convertAnyProductionProfit(normalResources));
        assertDoesNotThrow(() -> rs.addToResourcesToProduce(anyOrFaith));
        assertDoesNotThrow(() -> rs.convertAnyProductionProfit(normalResources));
    }

    @Test
    void applyFaith() {
        assertDoesNotThrow(() -> rs.applyFaithPoints());
    }

    @Test
    void buffer() {
        assertDoesNotThrow(() -> rs.getBufferSize());
        assertThrows(Exception.class, () -> rs.subToBuffer(ResourceFactory.createResource(ResourceType.STONE, 1)));
        rs.addToBuffer(ResourceFactory.createResource(ResourceType.STONE, 1));
        assertDoesNotThrow(() -> rs.subToBuffer(ResourceFactory.createResource(ResourceType.STONE, 1)));
    }

    @Test
    void fromMarket() {
        assertDoesNotThrow(() -> rs.discardResourcesFromMarket());
    }

    @Test
    void deprecated(){

        ArrayList<Resource> normalResources = new ArrayList<>() {{
            add(ResourceFactory.createResource(ResourceType.COIN, 1));
            add(ResourceFactory.createResource(ResourceType.STONE, 1));
        }};

        ArrayList<Depot> depots = new ArrayList<>() {{
            add(new Depot(1));
            add(new Depot(2));
        }};

        assertDoesNotThrow(()->rs.getFaithPoint());
        assertDoesNotThrow(()->rs.removeDiscount(normalResources));
        assertDoesNotThrow(()->rs.removeLeaderDepot(depots));
    }

}