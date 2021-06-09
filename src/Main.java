import java.io.*;
import java.text.DecimalFormat;
import java.util.*;

class Cache{
    private String[][] blocks = null; //[number of sets][associativity]
    private boolean[][] isDirty = null;
    private int blockCount; //number of cache rows/blocks
    private int blockSize;
    private int setCount;
    private String unity = "Unified";
    private int associativity; //x-way associative 1=direct-mapped n=fully-associative
    private String writePolicy = "WRITE BACK";
    private String writeMissPolicy = "WRITE ALLOCATE";
    private String cacheType; //data or instruction
    private String commandInHand = null;
    private String requestAddress;
    private Long requestBlockInRAM;
    private int requestType;
    private int misses;
    private int Imisses;
    private double missRate;
    private double ImissRate;
    private int accesses;
    private int Iaccesses;
    private int replace;
    private int Ireplace;
    private int demandFetch;
    private int copiesBack;

    public Cache(String information, String cacheSize, String caheType){
        String [] cacheInfo = information.split(" - ");
        this.blockSize = Integer.parseInt(cacheInfo[0]);
        if (caheType.equals("data")) {
            this.blockCount = (Integer.parseInt(cacheSize.split(" - ")[0])/blockSize);
        }
        else {
            this.blockCount = (Integer.parseInt(cacheSize.split(" - ")[1])/blockSize);
        }
        if(Integer.parseInt(cacheInfo[1])==1) //harvard
        {
            unity = "Split";
        }
        this.associativity = Integer.parseInt(cacheInfo[2]);
        this.setCount = blockCount/associativity;
        if(cacheInfo[3].contains("wt")) {
            writePolicy = "WRITE THROUGH";
        }
        if(cacheInfo[4].contains("nw")) {
            writeMissPolicy = "NO WRITE ALLOCATE";
        }
        this.cacheType = caheType;
        blocks = new String[setCount][associativity];
        isDirty = new boolean[setCount][associativity];
        for (int i = 0 ; i < blocks.length ; i++) {
            for (int j = 0 ; j < associativity ; j++) {
                blocks[i][j] = "n";
                isDirty[i][j] = false;
            }
        }
        misses = 0;
        Imisses = 0;
        missRate = 0;
        ImissRate = 0;
        accesses = 0;
        Iaccesses = 0;
        replace = 0;
        Ireplace = 0;
        demandFetch = 0;
        copiesBack = 0;
    }

    public int getBlockCount() {
        return blockCount;
    }

    public int getBlockSize() {
        return blockSize;
    }

    public String getUnity() {
        return unity;
    }

    public int getAssociativity() {
        return associativity;
    }

    public String getWritePolicy() {
        return writePolicy;
    }

    public String getWriteMissPolicy() {
        return writeMissPolicy;
    }

    public int getMisses() {
        return misses;
    }

    public int getIMisses() {
        return Imisses;
    }

    public double getMissRate() {
        DecimalFormat db = new DecimalFormat("#.####");
        if (accesses == 0)
            return 0;
        missRate = Double.valueOf(misses) / accesses;
        return Double.valueOf(db.format(missRate));
    }

    public double getIMissRate() {
        DecimalFormat db = new DecimalFormat("#.####");
        if (Iaccesses == 0)
            return 0;
        ImissRate = Double.valueOf(Imisses) / Iaccesses;
        return Double.valueOf(db.format(ImissRate));
    }

    public double getHitRate(){
        if (accesses == 0)
            return 0;
        else return (1.0 - getMissRate());
    }

    public double getIHitRate(){
        if (Iaccesses == 0)
            return 0;
        else return (1.0 - getIMissRate());
    }

    public int getAccesses() {
        return accesses;
    }

    public int getIAccesses(){
        return Iaccesses;
    }

    public int getReplace() {
        return replace;
    }

    public int getIReplace() {
        return Ireplace;
    }

    public int getDemandFetch() {
        return demandFetch;
    }

    public int getCopiesBack() {
        return copiesBack;
    }

    public void setCommandInHand(String command){
        this.commandInHand = command;
        String[] cm = command.split("\\s+", 3);
        requestAddress = Long.toString(Long.parseLong(cm[1], 16), 10);
        requestBlockInRAM = Long.parseLong(requestAddress) / blockSize;
        requestType = Integer.parseInt(cm[0]);
        if (requestType == 2)
            Iaccesses++;
        else
            accesses++;
        if(requestType == 1){ //data store (write)
            cacheWrite();
        }
        else { //instruction load or data load
            this.cacheRead();
        }
    }

    public void cacheWrite(){
        int i = requestBlockInRAM.intValue() % setCount;
        if (associativity == blockCount)
            i = 0;
        int j;
        //write back
        if (writePolicy.equalsIgnoreCase("write back")){
            j = isHit();
            if(j >= 0) {
                isDirty[i][j] = true;
                return;
            }
            //isMissed wb wa
            if (writeMissPolicy.equalsIgnoreCase("write allocate")){
                j = locate(i); //if associativity is greater than 1 then the policy must be LRU
                writeBackWriteAllocateReadAndWrite(i,j);
                isDirty[i][j] = true;
                return;
            }
            //isMissed wb nw
            else{
                if (requestType == 2)
                    Imisses++;
                else
                    misses++;
                copiesBack += 1;
                return;
            }
        }
        //write through (write)
        else {
            j = isHit();
            if (j >= 0){
                copiesBack++;
                return;
            }
            //write allocate
            j = locate(i);
            if (requestType == 2)
                Imisses++;
            else
                misses++;
            if (!blocks[i][j].equalsIgnoreCase("n")) {
                if (requestType == 2) {
                    Ireplace++;
                }
                else {
                    replace++;
                }
            }
            copiesBack++;
            if (writeMissPolicy.equalsIgnoreCase("write allocate")){
                blocks[i][j] = requestBlockInRAM.toString();
                demandFetch += blockSize/4;
            }
        }
    }

    public void cacheRead(){
        int i = requestBlockInRAM.intValue() % setCount;
        if( associativity == blockCount )
            i = 0;
        int j;
        //write back
        if (writePolicy.equalsIgnoreCase("write back")){
            if (isHit() >= 0){ //isHit
                return;
            }
            //isMissed wb wa or wb nw (no difference in read)
            j = locate(i); //if associativity is greater than 1 then the policy must be LRU
            writeBackWriteAllocateReadAndWrite(i,j);
            isDirty[i][j] = false;
            return;
        }
        //write through (read) hit or miss on read is the same for wa or nw
        else{
            j = isHit();
            if ( j >= 0 )
                return;
            //isMissed
            j = locate(i);
            if (requestType == 2)
                Imisses++;
            else
                misses++;
            if (!blocks[i][j].equalsIgnoreCase("n")) {
                if (requestType == 2) {
                    Ireplace++;
                }
                else {
                    replace++;
                }
            }
            blocks[i][j] = requestBlockInRAM.toString();
            demandFetch += blockSize/4;
        }
    }


    public void writeBackWriteAllocateReadAndWrite(int i, int j){
        if (requestType == 2)
            Imisses++;
        else
            misses++;
        demandFetch += blockSize/4;
        if (!blocks[i][j].equalsIgnoreCase("n")) {
            if (requestType == 2)
                Ireplace++;
            else
                replace++;
        }
        if (isDirty[i][j]) {
            copiesBack += blockSize/4;
        }
        blocks[i][j] = requestBlockInRAM.toString();
    }

    public int isHit(){
        int i = requestBlockInRAM.intValue() % setCount;
        if (associativity == blockCount)
            i = 0;
        for (int j = 0 ; j < blocks[i].length ; j++){
            if (blocks[i][j].equalsIgnoreCase(Long.toString(requestBlockInRAM))){ //isHit
                if (associativity == 1) { //direct mapped
                    return j;
                }
                //LRU
                shift(i,j);
                return 0;
            }
        }
        return -1 ; //isMissed (Maybe empty or not. it doesn't concern us. it concerns locate)
    }

    public void shift(int row, int location){
        String temp = blocks[row][location];
        boolean dirt = isDirty[row][location];
        for (int k = location ; k > 0 ; k--) {
            blocks[row][k] = blocks[row][k - 1];
            isDirty[row][k] = isDirty[row][k - 1];
        }
        blocks[row][0] = temp;
        isDirty[row][0] = dirt;
    }

    public int locate(int r){
        if (associativity == 1)
            return 0;
        int c ;
        for (c = 0 ; c < blocks[r].length ; c++){
            if (blocks[r][c].contains("n")){ //isNotfull
                break;
            }
        }
        //isFull
        if (c == blocks[r].length)
            c--;
        shift(r, c);
        return 0;
    }

    public void emptyCache(){
        if (writePolicy.equalsIgnoreCase("write back")){
            int count = 0;
            for (int i = 0 ; i < setCount ; i++)
                for (int j = 0 ; j < associativity ; j++)
                    if (isDirty[i][j])
                        count++;
            copiesBack += (blockSize/4) * count;
        }
    }

}

public class Main {
    public static void main(String[] args) {
        Scanner scan = new Scanner(System.in);
        String command = null;
        String cacheInformation = scan.nextLine();
        String cacheSize = scan.nextLine();
        Cache cache = new Cache(cacheInformation, cacheSize, "data");
        Cache Icache = null;
        if(Integer.parseInt(cacheInformation.split(" - ")[1])==1)
            Icache = new Cache(cacheInformation, cacheSize, "inst");
        while( !(command = scan.nextLine()).isEmpty()) {
            if (Icache != null)
                if (command.charAt(0) == '2'){
                    Icache.setCommandInHand(command);
                    continue;
                }
            cache.setCommandInHand(command);
        }
        cache.emptyCache();
        System.out.println("***CACHE SETTINGS***");
        System.out.println(cache.getUnity() + " I- D-cache");
        if (Icache == null)
            System.out.println("Size: " + cache.getBlockCount()*cache.getBlockSize());
        else
            System.out.println("I-cache size: " + cache.getBlockCount()*cache.getBlockSize() +"\n" + "D-cache size: " + Icache.getBlockCount()*Icache.getBlockSize());
        System.out.println("Associativity: " + cache.getAssociativity());
        System.out.println("Block size: " + cache.getBlockSize());
        System.out.println("Write policy: " + cache.getWritePolicy());
        System.out.println("Allocation policy: " + cache.getWriteMissPolicy());
        System.out.println();
        System.out.println("***CACHE STATISTICS***");
        System.out.println("INSTRUCTIONS");
        boolean isNull = false;
        if (Icache == null) {
            Icache = cache;
            isNull = true;
        }
        double mr = Icache.getIMissRate();
        double hr = Icache.getIHitRate();
        if (isNull){
            mr = 0.0;
            hr = 0.0;
        }
        System.out.println("accesses: " + Icache.getIAccesses());
        System.out.println("misses: " + Icache.getIMisses());
        System.out.printf("miss rate: %.4f (hit rate %.4f)\n", mr, hr);
        System.out.println("replace: " + Icache.getIReplace());
        System.out.println("DATA");
        System.out.println("accesses: " + cache.getAccesses());
        System.out.println("misses: " + cache.getMisses());
        mr = cache.getMissRate();
        hr = cache.getHitRate();
        System.out.printf("miss rate: %.4f (hit rate %.4f)\n", mr, hr);
        System.out.println("replace: " + cache.getReplace());
        int d = Icache.getDemandFetch() + cache.getDemandFetch();
        int c = Icache.getCopiesBack() + cache.getCopiesBack();
        if (isNull){
            d /= 2;
            c /= 2;
        }
        System.out.println("TRAFFIC (in words)");
        System.out.println("demand fetch: " + d);
        System.out.println("copies back: " + c);
    }
}
