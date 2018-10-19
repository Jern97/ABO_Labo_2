package be.kul.gantry.domain;



import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * Created by Wim on 27/04/2015.
 */
public class Problem {

    private final int minX, maxX, minY, maxY;
    private final int maxLevels;
    private final List<Item> items;
    private final List<Job> inputJobSequence;
    private final List<Job> outputJobSequence;

    private final List<Gantry> gantries;
    private final List<Slot> slots;
    private final int safetyDistance;
    private final int pickupPlaceDuration;
    private HashMap<Integer, HashMap<Integer, Slot>> bottomSlots;
    private HashMap<Integer, Slot> itemSlotMap;

    public Problem(int minX, int maxX, int minY, int maxY, int maxLevels, List<Item> items, List<Job> inputJobSequence, List<Job> outputJobSequence, List<Gantry> gantries, List<Slot> slots, int safetyDistance, int pickupPlaceDuration, HashMap<Integer, HashMap<Integer, Slot>> bottomSlots, HashMap<Integer, Slot> itemSlotMap) {
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
        this.maxLevels = maxLevels;
        this.items = items;
        this.inputJobSequence = inputJobSequence;
        this.outputJobSequence = outputJobSequence;
        this.gantries = gantries;
        this.slots = slots;
        this.safetyDistance = safetyDistance;
        this.pickupPlaceDuration = pickupPlaceDuration;
        this.bottomSlots = bottomSlots;
        this.itemSlotMap = itemSlotMap;
    }

    public int getMinX() {
        return minX;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMinY() {
        return minY;
    }

    public int getMaxY() {
        return maxY;
    }

    public int getMaxLevels() {
        return maxLevels;
    }

    public List<Item> getItems() {
        return items;
    }

    public List<Job> getInputJobSequence() {
        return inputJobSequence;
    }

    public List<Job> getOutputJobSequence() {
        return outputJobSequence;
    }

    public List<Gantry> getGantries() {
        return gantries;
    }

    public List<Slot> getSlots() {
        return slots;
    }

    public int getSafetyDistance() {
        return safetyDistance;
    }

    public int getPickupPlaceDuration() {
        return pickupPlaceDuration;
    }

    public HashMap<Integer, Slot> getItemSlotMap() {
        return itemSlotMap;
    }

    public void setItemSlotMap(HashMap<Integer, Slot> itemSlotMap) {
        this.itemSlotMap = itemSlotMap;
    }

    public void writeJsonFile(File file) throws IOException {
        JSONObject root = new JSONObject();

        JSONObject parameters = new JSONObject();
        root.put("parameters",parameters);

        parameters.put("gantrySafetyDistance",safetyDistance);
        parameters.put("maxLevels",maxLevels);
        parameters.put("pickupPlaceDuration",pickupPlaceDuration);

        JSONArray items = new JSONArray();
        root.put("items",items);

        for(Item item : this.items) {
            JSONObject jo = new JSONObject();
            jo.put("id",item.getId());

            items.add(jo);
        }


        JSONArray slots = new JSONArray();
        root.put("slots",slots);
        for(Slot slot : this.slots) {
            JSONObject jo = new JSONObject();
            jo.put("id",slot.getId());
            jo.put("cx",slot.getCenterX());
            jo.put("cy",slot.getCenterY());
            jo.put("minX",slot.getXMin());
            jo.put("maxX",slot.getXMax());
            jo.put("minY",slot.getYMin());
            jo.put("maxY",slot.getYMax());
            jo.put("z",slot.getZ());
            jo.put("type",slot.getType().name());
            jo.put("itemId",slot.getItem() == null ? null : slot.getItem().getId());

            slots.add(jo);
        }

        JSONArray gantries = new JSONArray();
        root.put("gantries",gantries);
        for(Gantry gantry : this.gantries) {
            JSONObject jo = new JSONObject();

            jo.put("id",gantry.getId());
            jo.put("xMin",gantry.getXMin());
            jo.put("xMax",gantry.getXMax());
            jo.put("startX",gantry.getStartX());
            jo.put("startY",gantry.getStartY());
            jo.put("xSpeed",gantry.getXSpeed());
            jo.put("ySpeed",gantry.getYSpeed());

            gantries.add(jo);
        }

        JSONArray inputSequence = new JSONArray();
        root.put("inputSequence",inputSequence);

        for(Job inputJ : this.inputJobSequence) {
            JSONObject jo = new JSONObject();
            jo.put("itemId",inputJ.getItem().getId());
            jo.put("fromId",inputJ.getPickup().getSlot().getId());

            inputSequence.add(jo);
        }

        JSONArray outputSequence = new JSONArray();
        root.put("outputSequence",outputSequence);

        for(Job outputJ : this.outputJobSequence) {
            JSONObject jo = new JSONObject();
            jo.put("itemId",outputJ.getItem().getId());
            jo.put("toId",outputJ.getPlace().getSlot().getId());

            outputSequence.add(jo);
        }

        try(FileWriter fw = new FileWriter(file)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();

            fw.write(gson.toJson(root));
        }

    }

    public static Problem fromJson(File file) throws IOException, ParseException {


        JSONParser parser = new JSONParser();

        try(FileReader reader = new FileReader(file)) {
            JSONObject root = (JSONObject) parser.parse(reader);

            List<Item> itemList = new ArrayList<>();
            List<Slot> slotList = new ArrayList<>();
            List<Gantry> gantryList = new ArrayList<>();
            List<Job> inputJobList = new ArrayList<>();
            List<Job> outputJobList = new ArrayList<>();

            JSONObject parameters = (JSONObject) root.get("parameters");
            int safetyDist = ((Long)parameters.get("gantrySafetyDistance")).intValue();
            int maxLevels = ((Long)parameters.get("maxLevels")).intValue();
            int pickupPlaceDuration = ((Long)parameters.get("pickupPlaceDuration")).intValue();

            JSONArray items = (JSONArray) root.get("items");
            for(Object o : items) {
                int id = ((Long)((JSONObject)o).get("id")).intValue();

                Item c = new Item(id);
                itemList.add(c);
            }


            int overallMinX = Integer.MAX_VALUE, overallMaxX = Integer.MIN_VALUE;
            int overallMinY = Integer.MAX_VALUE, overallMaxY = Integer.MIN_VALUE;

            JSONArray slots = (JSONArray) root.get("slots");

            //We maken een 2D Array aan voor alle bodem slots (z=0)
            HashMap<Integer, HashMap<Integer, Slot>> bodemSlots = new HashMap<>();
            //We vullen de array met nieuwe arrays
            HashMap<Integer, Slot> itemSlotMap = new HashMap<>();

            for(Object o : slots) {
                JSONObject slot = (JSONObject) o;

                int id = ((Long)slot.get("id")).intValue();
                int cx = ((Long)slot.get("cx")).intValue();
                int cy = ((Long)slot.get("cy")).intValue();
                int minX = ((Long)slot.get("minX")).intValue();
                int minY = ((Long)slot.get("minY")).intValue();
                int maxX = ((Long)slot.get("maxX")).intValue();
                int maxY = ((Long)slot.get("maxY")).intValue();
                int z = ((Long)slot.get("z")).intValue();

                overallMinX = Math.min(overallMinX,minX);
                overallMaxX = Math.max(overallMaxX,maxX);
                overallMinY = Math.min(overallMinY,minY);
                overallMaxY = Math.max(overallMaxY,maxY);

                Slot.SlotType type = Slot.SlotType.valueOf((String)slot.get("type"));
                Integer itemId = slot.get("itemId") == null ? null : ((Long)slot.get("itemId")).intValue();
                Item c = itemId == null ? null : itemList.get(itemId);

                Slot s = new Slot(id,cx,cy,minX,maxX,minY,maxY,z,type,c);

                //Als Z=0 is ligt het slot op de bodem en moeten we het toevoegen een de 2D Array van bodemslots;
                if(z == 0){
                    //Hashmap in hashmap steken als key nog leeg is
                    if(bodemSlots.get((int) cy/10) == null){
                        bodemSlots.put((int) cy/10, new HashMap<>());
                    }
                    bodemSlots.get((int) cy/10).put((int) cx/10, s);
                }
                else{
                    //Beginnen bij onderste slot, halen uit bottomSlots lijst
                    Slot child = bodemSlots.get((int) cy/10).get((int) cx/10);
                    //Child updaten naar gelang waarde van z, we zoeken de child van nieuwe node S
                    for(int i = 1; i<z; i++){
                        child = child.getParent();
                    }
                    //Een keer we de child gevonden hebben updaten we de relaties;
                    s.setChild(child);
                    child.setParent(s);
                }

                //toevoegen van link item -> slot aan hashmap indien het slot gevuld is;
                if(c != null){
                    itemSlotMap.put(c.getId(), s);
                }


                slotList.add(s);
            }


            JSONArray gantries = (JSONArray) root.get("gantries");
            for(Object o : gantries) {
                JSONObject gantry = (JSONObject) o;


                int id = ((Long)gantry.get("id")).intValue();
                int xMin = ((Long)gantry.get("xMin")).intValue();
                int xMax = ((Long)gantry.get("xMax")).intValue();
                int startX = ((Long)gantry.get("startX")).intValue();
                int startY = ((Long)gantry.get("startY")).intValue();
                double xSpeed = ((Double)gantry.get("xSpeed")).doubleValue();
                double ySpeed = ((Double)gantry.get("ySpeed")).doubleValue();

                Gantry g = new Gantry(id, xMin, xMax, startX, startY, xSpeed, ySpeed);
                gantryList.add(g);
            }

            JSONArray inputJobs = (JSONArray) root.get("inputSequence");
            int jid = 0;
            for(Object o : inputJobs) {
                JSONObject inputJob = (JSONObject) o;

                int iid = ((Long) inputJob.get("itemId")).intValue();
                int sid = ((Long) inputJob.get("fromId")).intValue();

                Job job = new Job(jid++,itemList.get(iid),slotList.get(sid),null);
                inputJobList.add(job);
            }

            JSONArray outputJobs = (JSONArray) root.get("outputSequence");
            for(Object o : outputJobs) {
                JSONObject outputJob = (JSONObject) o;

                int iid = ((Long) outputJob.get("itemId")).intValue();
                int sid = ((Long) outputJob.get("toId")).intValue();

                Job job = new Job(jid++,itemList.get(iid),null, slotList.get(sid));
                outputJobList.add(job);
            }


            return new Problem(
                    overallMinX,
                    overallMaxX,
                    overallMinY,
                    overallMaxY,
                    maxLevels,
                    itemList,
                    inputJobList,
                    outputJobList,
                    gantryList,
                    slotList,
                    safetyDist,
                    pickupPlaceDuration,
                    bodemSlots,
                    itemSlotMap);

        }

    }

    public List<Move> solve()
    {
        //Eerst alle output jobs afwerken
        List<Move> moves = new ArrayList<>();
        for(Job j: outputJobSequence){
            Slot s = itemSlotMap.get(j.getItem().getId());
            if(s.getParent() != null){
                clearTop(s.getParent(), gantries.get(0));
            }
        }
    }
    public List<Move> clearTop(Slot s, Gantry g){
        List<Move> moves = new ArrayList<>();
        //Recursief naar boven gaan in de stapel, deze moeten eerst verplaatst worden
        if(s.getParent().getItem() != null){
            moves.addAll(clearTop(s.getParent(), g));
        }
        //Nieuwe locatie zoeken voor item (in een zo dicht mogelijke rij)
        Slot newLocation = null;
        int magnitude = 1;
        int direction = 1;
        while(newLocation == null){
            int offset = magnitude * direction;
            //Als we in een bepaalde richting aan het einde van de opslagruimte komen:
            if(bottomSlots.get(((int) s.getCenterY()/10) + offset) == null){
                magnitude = 0;
                direction *= -1;
                continue;
            }
            //Alle sloten in de onderste rij vastnemen en beginnen zoeken voor een vrije plaats.
            Set<Slot> bottomRow = new HashSet<>(bottomSlots.get(((int) s.getCenterY()/10) + offset).values());
            newLocation = searchViableSlot(bottomRow);
            magnitude += 1;
        }

        //Item effectief verplaatsen
        moves.addAll(createMoves(g, s, newLocation));
        //Slots en hashmap updaten
        newLocation.setItem(s.getItem());
        s.setItem(null);
        itemSlotMap.put(newLocation.getItem().getId(), newLocation);

        return moves;
    }
    public Slot searchViableSlot(Set<Slot> toCheck){
        //hoogste niveau bereikt;
        if(toCheck.isEmpty()){
            return null;
        }


        //Checken voor een vrij plaats op dit niveau
        for(Slot t: toCheck){
            if (t.getItem() == null){
                return t;
            }
        }

        //Alle parents toevoegen aan lijst en niveau hoger gaan zoeken
        Set<Slot> nextLevel = new HashSet<>();
        for(Slot t: toCheck){
            nextLevel.add(t);
        };
        return searchViableSlot(nextLevel);

    }

    public List<Move> createMoves(Gantry g, Slot toMoveItem ,Slot destination){
        List<Move> moves = new ArrayList<>();
        //Een basis sequentie van moves bestaat uit 4 verschillende moves:

        //De kraan bewegen naar het item;
        moves.add(new Move(g, toMoveItem.getCenterX(), toMoveItem.getCenterY(), null, 0));
        //Item oppikken in de kraan;
        moves.add(new Move(g, g.getX(), g.getY(), toMoveItem.getId(), pickupPlaceDuration));
        //Item vervoeren naar destination;
        moves.add(new Move(g, destination.getCenterX(), destination.getCenterY(), toMoveItem.getId(), 0));
        //Item droppen op destination;
        moves.add(new Move(g, g.getX(), g.getY(), null, pickupPlaceDuration));

        return moves;
    }

    public List<Move> placeItem(Slot s, Gantry g){

    }

}
