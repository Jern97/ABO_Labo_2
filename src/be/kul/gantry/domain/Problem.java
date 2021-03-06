package be.kul.gantry.domain;



import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import sun.awt.image.ImageWatched;

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
    private Set<Item> outputItems;

    public static Map<Integer, Double> rowLockedTill= new HashMap<>();
    public static List<Gantry> gantries;
    private final List<Slot> slots;
    public static int safetyDistance;
    public static int pickupPlaceDuration;
    private Slot inputSlot;
    private Slot outputSlot;
    private HashMap<Integer, HashMap<Integer, Slot>> bottomSlots;
    private HashMap<Integer, Slot> itemSlotMap;
    private List<Integer> filledLevelList;
    private Random r = new Random(0);

    public Problem(int minX, int maxX, int minY, int maxY, int maxLevels, List<Item> items, List<Job> inputJobSequence, List<Job> outputJobSequence, List<Gantry> gantries, List<Slot> slots, int safetyDistance, int pickupPlaceDuration, HashMap<Integer, HashMap<Integer, Slot>> bottomSlots, HashMap<Integer, Slot> itemSlotMap) {
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
        this.maxLevels = maxLevels;
        this.items = items;
        this.inputJobSequence = inputJobSequence;
        this.outputJobSequence = outputJobSequence;
        outputItems=new HashSet<>();
        for (Job job : outputJobSequence) {
            outputItems.add(job.getItem());
        }
        this.gantries = gantries;
        this.slots = slots;
        this.safetyDistance = safetyDistance;
        this.pickupPlaceDuration = pickupPlaceDuration;
        this.bottomSlots = bottomSlots;
        this.itemSlotMap = itemSlotMap;

        //We maken een lijst aan met initiele max heights van alle rijen (nodig om te bepalen waar een item geplaatst wordt)
        filledLevelList = new ArrayList<>();
        for(HashMap<Integer, Slot> row : bottomSlots.values()){
            filledLevelList.add(findFullLevel(new HashSet<>(row.values())));
        }
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

    public static Problem fromJsonNotStaggered(File file) throws IOException, ParseException {


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
                    //Enkel toevoegen als het geen input of output slot is;
                    if(!s.getType().name().equals("INPUT") && !s.getType().name().equals("OUTPUT")){
                        bodemSlots.get((int) cy/10).put((int) cx/10, s);

                    }
                }
                else{
                    //Beginnen bij onderste slot, halen uit bottomSlots lijst
                    Slot child = bodemSlots.get((int) cy/10).get((int) cx/10);
                    //Child updaten naar gelang waarde van z, we zoeken de child van nieuwe node S
                    for(int i = 1; i<z; i++){
                        child = child.getParents().get(0);
                    }
                    //Een keer we de child gevonden hebben updaten we de relaties;
                    s.addChild(child);
                    child.addParent(s);
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

    public static Problem fromJsonStaggered(File file) throws IOException, ParseException {

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
                    bodemSlots.putIfAbsent((int) cy/10, new HashMap<>());
                    //Enkel toevoegen als het geen input of output slot is;
                    if(!s.getType().name().equals("INPUT") && !s.getType().name().equals("OUTPUT")){
                        bodemSlots.get((int) cy/10).put((int) cx/10, s);

                    }
                }
                else{
                    //Beginnen bij onderste slot, halen uit bottomSlots lijst
                    boolean oneParent;
                    Slot child = null;
                    Slot child1 = null;
                    Slot child2 = null;
                    if(z % 2 == 0){
                        child = bodemSlots.get((int) cy/10).get((int) (cx)/10);
                        oneParent = false;
                    }
                    else {
                        child1 = bodemSlots.get((int) cy / 10).get((int) (cx - 5) / 10);
                        child2 = bodemSlots.get((int) cy / 10).get((int) (cx + 5) / 10);
                        oneParent = true;
                        //Child updaten naar gelang waarde van z, we zoeken de child van nieuwe node S
                    }
                    for(int i = 1; i<z; i++){
                        if(!oneParent) {
                            child1 = child.getParents().get(0);
                            child2 = child.getParents().get(1);
                            oneParent = true;
                        }
                        else{
                            Set<Slot> parentSet = new HashSet<>();
                            List<Slot> parentList = new ArrayList<>();
                            parentList.addAll(child1.getParents());
                            parentList.addAll(child2.getParents());
                            for(Slot parent: parentList){
                                if(!parentSet.add(parent)){
                                    child = parent;
                                    break;
                                }
                            }
                            oneParent = false;
                        }
                    }
                    //Een keer we de child gevonden hebben updaten we de relaties;
                    s.addChild(child1);
                    s.addChild(child2);
                    child1.addParent(s);
                    child2.addParent(s);
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
    /**
     * Deze methode lost het probleem op door eerst de beginnen met de outputjobs af te werken.
     * Als een item die nodig is voor een outputjob nog niet aanwezig is in de opslagplaats
     * dan zullen eerst x aantal inputjobs worden afgewerkt, totdat het benodigde item zich in
     * de opslagplaats bevindt.
     * Daarna worden de overige inputjobs afgewerkt.
     * @return een list van moves die nodig zijn om alle jobs af te werken
     */

    public void solveTwoGantries()
    {
        LinkedList<Job> inputJobSequenceCopy= new LinkedList<>(inputJobSequence);
        LinkedList<Job> outputJobSequenceCopy= new LinkedList<>(outputJobSequence);

        // kan aan input
        Gantry gantry0= gantries.get(0);
        // kan aan output
        Gantry gantry1= gantries.get(1);


        Gantry currentGantry=gantry1;
        LinkedList<Job> currentJobSequence= outputJobSequenceCopy;

        Gantry otherGantry= gantry0;
        LinkedList<Job> otherJobSequence= inputJobSequenceCopy;

        while(inputJobSequenceCopy.size()>0 || outputJobSequenceCopy.size()>0){


            // vraag current en other gantry op
            if((gantry0.getTime()<gantry1.getTime() || outputJobSequenceCopy.isEmpty() )&& !inputJobSequenceCopy.isEmpty()){
                currentGantry=gantry0;
                otherGantry=gantry1;
                currentJobSequence=inputJobSequenceCopy;
            }
            else if ((gantry0.getTime()>gantry1.getTime() || inputJobSequenceCopy.isEmpty()) && !outputJobSequenceCopy.isEmpty()){
                currentGantry=gantry1;
                otherGantry=gantry0;
                currentJobSequence=outputJobSequenceCopy;
            }

            Job jobToExecute=currentJobSequence.get(0);
            if(currentJobSequence==outputJobSequenceCopy){
                // probeer outputJob
                Slot s= itemSlotMap.get(jobToExecute.getItem().getId());
                // zal niet lukken als tijd van kraan < Item timestamp
                if(currentGantry.getTime()<jobToExecute.getItem().getTimestamp() || s==null){
                    // maak wacht move voor gantry tot time van andere kraan
                    MoveGenerator.getInstance().pauseGantry(currentGantry,otherGantry.getTime() - currentGantry.getTime()+5);


                    // tijd zal gelijk staan dus wissel hier al van kranen
                    // we zitten bij output dus current = 1
                    /*currentGantry=gantry0;
                    otherGantry=gantry1;*/
                }
                else{


                    // doe effectief de outputJob
                    //Als het item dat verwijderd moet worden parents heeft met items moeten deze eerst verplaatst worden;
                    if(s.getParents().get(0) != null && s.getParents().get(0).getItem() != null){
                        clearTop(s.getParents().get(0), currentGantry, currentGantry.getTime()>=otherGantry.getTime());
                    }
                    if(s.getParents().get(1) != null && s.getParents().get(1).getItem() != null){
                        clearTop(s.getParents().get(1), currentGantry, currentGantry.getTime()>=otherGantry.getTime());
                    }


                    //Het item effectief verplaatsen door de moves te berekenen en de data aan te passen
                    MoveGenerator.getInstance().createMoves(currentGantry, s, jobToExecute.getPlace().getSlot());
                    rowLockedTill.put(s.getCenterY()/10,currentGantry.getTime());
                    updateData(s, jobToExecute.getPlace().getSlot(), -1);
                    currentJobSequence.removeFirst();
                }

            }
            else{

                // doe inputJob
                //We gaan opzoek naar de rij met het laagste "vulniveau" (volledig gevuld niveau)
                int lowestHeight = -1;
                for(int j : filledLevelList){
                    if(j > lowestHeight){
                        lowestHeight = j;
                    }
                }
                //Eerste rij zoeken die de laagste vulniveau heeft, deze is het dichtst bij de kraan
                int lowestRow = filledLevelList.indexOf(lowestHeight);

                if(rowLockedTill.get(lowestRow)!=null && rowLockedTill.get(lowestRow)>=currentGantry.getTime()){
                    lowestRow++;
                }


                Slot destination=null;
                //In deze rij wordt een vrije plaats gezocht voor het item
                if(outputItems.contains(jobToExecute.getItem())) {
                    destination = searchViableSlot(new ArrayList<>(bottomSlots.get(lowestRow).values()), bottomSlots.get(lowestRow).size() / 2, false, true);
                }
                else{
                    destination=  searchViableSlot(new ArrayList<>(bottomSlots.get(lowestRow).values()), bottomSlots.get(lowestRow).size() / 2, true, false);
                }

                //Het item effectief verplaatsen door de moves te berekenen en de data aan te passen
                jobToExecute.getPickup().getSlot().setItem(jobToExecute.getItem());

                // effectief de move uitvoeren en toekennen aan een kraan, in de movegenerator wordt feasibility gegarandeerd
                MoveGenerator.getInstance().createMoves(currentGantry,jobToExecute.getPickup().getSlot(), destination);
                updateData(jobToExecute.getPickup().getSlot(), destination, 1);

                //De job uit de lijst verwijderen
                currentJobSequence.removeFirst();


            }
        }

    }

    public void solveOneGantry()
    {
        //Linkedlist maken van inputJobSequence om gemakkelijk jobs er te kunnen uithalen (removeFirst en getFirst())
        LinkedList<Job> inputJobSequenceCopy = new LinkedList<>(inputJobSequence);

        //We beginnen met het uitvoeren van de outputjobs
        for(Job j_out: outputJobSequence){
            Slot s = itemSlotMap.get(j_out.getItem().getId());

            //Als we het item nog niet kunnen vinden wordt eerst een deel van de inputsequence afgewerkt.
            while(s == null){
                Job j_in = inputJobSequenceCopy.getFirst();
                //We gaan opzoek naar de rij met de laagste "vulniveau" (volledig gevuld niveau)
                int lowestHeight = -1;
                for(int j : filledLevelList){
                    if(j > lowestHeight){
                        lowestHeight = j;
                    }
                }
                //Eerste rij zoeken die de laagste vulniveau heeft, deze is het dichtst bij de kraan
                int lowestRow = filledLevelList.indexOf(lowestHeight);

                //In deze rij wordt een vrije plaats gezocht voor het item
                Slot destination = searchViableSlot(new ArrayList<>(bottomSlots.get(lowestRow).values()), bottomSlots.get(lowestRow).size()/2, true, true);

                //Het item effectief verplaatsen door de moves te berekenen en de data aan te passen
                j_in.getPickup().getSlot().setItem(j_in.getItem());
                MoveGenerator.getInstance().createMoves(gantries.get(0),j_in.getPickup().getSlot(), destination);
                updateData(j_in.getPickup().getSlot(), destination, 1);

                //De job uit de lijst verwijderen
                inputJobSequenceCopy.removeFirst();

                //Opnieuw zoeken voor het Slot van de outputjob
                s = itemSlotMap.get(j_out.getItem().getId());
            }

            //Als het item dat verwijderd moet worden parents heeft met items moeten deze eerst verplaatst worden;
            if(s.getParents().get(0) != null && s.getParents().get(0).getItem() != null){
                clearTop(s.getParents().get(0), gantries.get(0), false);
            }
            if(s.getParents().get(1) != null && s.getParents().get(1).getItem() != null){
                clearTop(s.getParents().get(1), gantries.get(0), false);
            }

            //Het item effectief verplaatsen door de moves te berekenen en de data aan te passen
            MoveGenerator.getInstance().createMoves(gantries.get(0), s, j_out.getPlace().getSlot());
            updateData(s, j_out.getPlace().getSlot(), -1);
        }

        //Als alle outputsjobs klaar zijn werken we de overige input jobs af
        for (Job j_in: inputJobSequenceCopy){
            int lowestHeight = -1;
            for(int j : filledLevelList){
                if(j > lowestHeight){
                    lowestHeight = j;
                }
            }
            int lowestRow = filledLevelList.indexOf(lowestHeight);
            Slot destination = searchViableSlot(new ArrayList<>(bottomSlots.get(lowestRow).values()),0, true, true);

            //Het item effectief verplaatsen door de moves te berekenen en de data aan te passen
            j_in.getPickup().getSlot().setItem(j_in.getItem());
            MoveGenerator.getInstance().createMoves(gantries.get(0),j_in.getPickup().getSlot(), destination);
            updateData(j_in.getPickup().getSlot(), destination, 1);
        }
    }

    /**
     * Deze methode verplaatst alle items boven een bepaald slot (uitgraven) naar een zo dicht mogelijke rij.
     * @param s Item dat weg moet (1ste keer: de parent van het item dat we nodig hebben voor de job)
     * @param currentGantry Kraan die dit moet uitvoeren
     * @return een list van moves die nodig zijn voor de actie
     */

    public void clearTop(Slot s, Gantry currentGantry, boolean helpFromOther){
        if(gantries.size() == 2) {
            //helpFromOther=false;
            Gantry otherGantry = currentGantry.getId() == 0 ? gantries.get(1) : gantries.get(0);

            Gantry nextExecutingGantry = helpFromOther ? otherGantry : currentGantry;
            // pause other gantry?
            if (helpFromOther) {
                if (currentGantry.getTime() >= otherGantry.getTime()) {
                    MoveGenerator.getInstance().pauseGantry(otherGantry, currentGantry.getTime() - otherGantry.getTime());
                }
            }
            //Recursief naar boven gaan in de stapel, deze moeten eerst verplaatst worden
            if (s.getParents().get(0) != null && s.getParents().get(0).getItem() != null) {

                clearTop(s.getParents().get(0), nextExecutingGantry, currentGantry.getTime() - otherGantry.getTime() >= 0);

            }
            if (s.getParents().get(1) != null && s.getParents().get(1).getItem() != null) {
                clearTop(s.getParents().get(1), nextExecutingGantry, currentGantry.getTime() - otherGantry.getTime() >= 0);
            }

            //Nieuwe locatie zoeken voor item (in een zo dicht mogelijke rij)
            Slot newLocation = null;
            int magnitude = 1;
            //We kiezen willekeurig een richting om in te zoeken (vooruit of achteruit);
            int direction = r.nextInt(2) < 0.5 ? -1 : 1;

            while (newLocation == null) {
                int offset = magnitude * direction;
                //Als we in een bepaalde richting aan het einde van de opslagruimte komen:
                if (bottomSlots.get(((int) s.getCenterY() / 10) + offset) == null) {
                    //Grootte resetten en richting omdraaien
                    magnitude = 1;
                    direction *= -1;
                    continue;
                }
                //Alle sloten in de onderste rij vastnemen en beginnen zoeken voor een vrije plaats.
                ArrayList<Slot> bottomRow = new ArrayList<>(bottomSlots.get(((int) s.getCenterY() / 10) + offset).values());
                newLocation = searchViableSlot(bottomRow, (int) s.getCenterX() / 10, currentGantry.getX() < otherGantry.getX(), currentGantry.getX() > otherGantry.getX());
                //newLocation = searchViableSlot(bottomRow, (int) s.getCenterX()/10, true, true);

                if (newLocation == null) {
                    System.out.println("stop");
                }

                magnitude += 1;
            }

            //Bovenstaande containers uitgraven is voltooid, nu kunnen we het gewenste item effectief verplaatsen
            MoveGenerator.getInstance().createMoves(currentGantry, s, newLocation);
            //Slots en hashmap updaten
            updateData(s, newLocation, 0);
            rowLockedTill.put(s.getCenterY() / 10, currentGantry.getTime());


            // pause other gantry?
            if (helpFromOther) {
                if (currentGantry.getTime() >= otherGantry.getTime()) {
                    MoveGenerator.getInstance().pauseGantry(otherGantry, currentGantry.getTime() - otherGantry.getTime());
                }
            }
        }
        else{
            //Recursief naar boven gaan in de stapel, deze moeten eerst verplaatst worden
            if(s.getParents().get(0) != null && s.getParents().get(0).getItem() != null){
                clearTop(s.getParents().get(0), currentGantry, helpFromOther);
            }
            if(s.getParents().get(1) != null && s.getParents().get(1).getItem() != null){
                clearTop(s.getParents().get(1), currentGantry, helpFromOther);
            }

            //Nieuwe locatie zoeken voor item (in een zo dicht mogelijke rij)
            Slot newLocation = null;
            int magnitude = 1;
            //We kiezen willekeurig een richting om in te zoeken (vooruit of achteruit);
            int direction = Math.random() < 0.5 ? -1 : 1;

            while(newLocation == null){
                int offset = magnitude * direction;
                //Als we in een bepaalde richting aan het einde van de opslagruimte komen:
                if(bottomSlots.get(((int) s.getCenterY()/10) + offset) == null){
                    //Grootte resetten en richting omdraaien
                    magnitude = 1;
                    direction *= -1;
                    continue;
                }
                //Alle sloten in de onderste rij vastnemen en beginnen zoeken voor een vrije plaats.
                ArrayList<Slot> bottomRow = new ArrayList<>(bottomSlots.get(((int) s.getCenterY()/10) + offset).values());
                newLocation = searchViableSlot(bottomRow, (int) s.getCenterX()/10, true, true);

                magnitude += 1;
            }

            //Item effectief verplaatsen
            MoveGenerator.getInstance().createMoves(currentGantry, s, newLocation);
            //Slots en hashmap updaten
            updateData(s, newLocation, 0);
        }
    }

    /**
     * Deze methode zoekt in een bepaalde rij een zo laag mogelijke vrij slot en returnt dit.
     * @param toCheck set van Slots die overlopen moeten worden (beginnend bij de bodemrij)
     * @param prefferedX de x snelheid van de kraan is traag, daarom is het beter om een plekje te vinden dat gewoon naast de orignele plek ligt
     * @return 1 vrij slot
     */

    public Slot searchViableSlot(List<Slot> toCheck, int prefferedX, boolean searchLeft, boolean searchRight) {
        if(!searchLeft || !searchRight){
            System.out.println("stop");
        }


        //hoogste niveau bereikt;
        if (toCheck.isEmpty()) {
            return null;
        }
        List<Slot> nextLevel = new ArrayList<>();


        //Checken voor een vrij plaats op dit niveau, beginnend bij prefferedX
        if(toCheck.get(prefferedX).getItem() == null) return toCheck.get(prefferedX);

        if(toCheck.get(prefferedX).getParents().get(0) != null) {
            nextLevel.add(toCheck.get(prefferedX).getParents().get(0));
        }
        if(toCheck.get(prefferedX).getParents().get(1) != null){
            nextLevel.add(toCheck.get(prefferedX).getParents().get(1));
        }


        //verder zoeken in de buurt van X;
        int offsetLeft = prefferedX-1;
        int offsetRight = prefferedX+1;
        while((offsetLeft != -1 && searchLeft) || (offsetRight != toCheck.size() && searchRight)) {
            if (offsetLeft != -1 && searchLeft){
                Slot left = toCheck.get(offsetLeft);
                offsetLeft--;
                if(left.getItem() == null) return left;
                if(left.getParents().get(0)!=null && !nextLevel.contains(left.getParents().get(0))) nextLevel.add(left.getParents().get(0));
                if(left.getParents().get(1)!=null && !nextLevel.contains(left.getParents().get(1))) nextLevel.add(left.getParents().get(1));

            }
            if(offsetRight != toCheck.size() && searchRight){
                Slot right = toCheck.get(offsetRight);
                offsetRight++;
                if(right.getItem() == null) return right;
                if(right.getParents().get(0)!=null && !nextLevel.contains(right.getParents().get(0))) nextLevel.add(right.getParents().get(0));
                if(right.getParents().get(1)!=null && !nextLevel.contains(right.getParents().get(1))) nextLevel.add(right.getParents().get(1));
            }
        }

        //We hebben geen oplossing gevonden, dus alle parents toevoegen aan lijst en niveau hoger gaan zoeken
        return searchViableSlot(nextLevel, 0, true, true);
    }

    public void updateData(Slot from, Slot to, int mode){
        //Items in slots worden aangepast en de hoogte van de rij(en) worden aangepast;

        //-1 betekent een item naar outputslot brengen
        if(mode == -1){
            itemSlotMap.remove(from.getItem().getId());
            from.setItem(null);
            filledLevelList.set((int) from.getCenterY()/10, findFullLevel(new HashSet<>(bottomSlots.get((int) from.getCenterY()/10).values())));
        }
        //0 betekent een item intern verplaatsen
        if(mode == 0){
            to.setItem(from.getItem());
            from.setItem(null);
            itemSlotMap.put(to.getItem().getId(), to);

            filledLevelList.set((int) from.getCenterY()/10, findFullLevel(new HashSet<>(bottomSlots.get((int) from.getCenterY()/10).values())));
            filledLevelList.set((int) to.getCenterY()/10, findFullLevel(new HashSet<>(bottomSlots.get((int) to.getCenterY()/10).values())));
        }
        //1 betekent een item toevoegen aan de opslagplaats
        if(mode == 1){
            to.setItem(from.getItem());
            from.setItem(null);
            itemSlotMap.put(to.getItem().getId(), to);
            filledLevelList.set((int) to.getCenterY()/10, findFullLevel(new HashSet<>(bottomSlots.get((int) to.getCenterY()/10).values())));

        }
    }

    /**
     * Deze methode berekent het aantal volledig bezette niveaus van een bepaalde rij
     *
     * @param toCheck set van Slots (1ste keer onderste rij)
     * @return geeft het aantal volledig volzette niveaus terug
     */
    public static int findFullLevel(Set<Slot> toCheck){
        Set<Slot> nextLevel = new HashSet<>();
        //Voor de slots waar items in zitten zullen de parents moeten gecheckt worden;
        for(Slot s: toCheck){
            if(s == null || s.getItem() == null){
                //niveau is niet helemaal vol
                return 0;
            }
            else{
                nextLevel.addAll(s.getParents());
            }
        }
        //Geen lege slots op dit niveau, we gaan een niveau omhoog
        return 1 + findFullLevel(nextLevel);
    }
}
