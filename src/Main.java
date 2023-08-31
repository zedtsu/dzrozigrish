import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Collection;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;
class FileHandler {

    List<String> readToys(String filepath) {
        try {
            return Files.readAllLines(Paths.get(filepath));

        } catch(IOException e) {
            e.printStackTrace();
        }
        return List.of();
    }

    void writeToys(String mapState) {
        try {
            BufferedWriter wr = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream("./toys_after.txt"), StandardCharsets.UTF_8));

            wr.write(mapState);
            wr.close();
        } catch(IOException e) {
            e.printStackTrace();
        }

    }

    public static BufferedWriter raffleLog() throws IOException{

        BufferedWriter bw = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream("./raffle_winners.txt",true), StandardCharsets.UTF_8)
        );
        bw.write(LocalDateTime.now().toString()+"\n");
        return bw;
    }

}

class Participant implements Comparable<Participant>{
    String name;
    int id;
    static int totalCount = 0;

    public Participant(String name) {
        this.name = name;
        this.id = totalCount++;
    }

    public Participant() {
        this("Ребёнок"+ totalCount);
    }

    @Override
    public String toString() {
        return String.format("Участник %s, под номером %d", this.name, this.id);
    }

    @Override
    public int compareTo(Participant o) {
        if(o.id == this.id){
            return 0;
        }
        return this.id < o.id ? -1 : 1;
    }
}

class ParticipantQueue implements Iterable<Participant> {
    PriorityQueue<Participant> drawQueue;


    public ParticipantQueue(Collection<Participant> list) {
        this.drawQueue = new PriorityQueue<>(list.size());
        this.drawQueue.addAll(list);
    }

    public ParticipantQueue() {
        this.drawQueue = new PriorityQueue<>();
    }

    void addParticipant(Participant p){
        this.drawQueue.add(p);
    }


    class ParticipantIterator implements Iterator<Participant>{
        Participant current;
        public ParticipantIterator(PriorityQueue<Participant> participants) {
            this.current = participants.peek();
        }

        @Override
        public boolean hasNext() {
            return !drawQueue.isEmpty();
        }

        @Override
        public Participant next() {
            return drawQueue.poll();
        }
    }


    @Override
    public Iterator<Participant> iterator() {
        return new ParticipantIterator(drawQueue);
    }

}

class Raffle {
    ToyList currentToys;
    ParticipantQueue currentParticipants;
    double lossWeight = 0; //0 для соответствия заданию, где веса разбиваются на полную вероятность в 100%
    int lossId;

    ChanceCalc cc = new ChanceCalc();
    Raffle.QuantityCalc qc = new Raffle.QuantityCalc();

    public Raffle(ParticipantQueue kids, ToyList tl) {

        this.currentToys = cc.assignChance(tl);
        this.currentParticipants = kids;
    }

    public void runRaffle() {
        ParticipantQueue kids = this.currentParticipants;
        ToyList tl = this.currentToys;
        PriorityQueue<Toy> prizes = new PriorityQueue<>(tl.toys.values());
        try {
            BufferedWriter log = FileIO.raffleLog();

            while(kids.iterator().hasNext()){
                double winRoll = cc.doRoll();
                Participant k = kids.iterator().next();
                try {
                    Toy win = cc.checkPrize(prizes, winRoll);
                    //showRoll(k,win,winRoll);
                    prizes = qc.adjustQuantityLeft(win,tl,prizes);
                    log.write(showWin(k, win) + "\n");
                } catch(Exception e) {
                    System.out.println(e.getMessage());
                }

            }
            log.close();

        } catch(IOException e) {
            e.printStackTrace();
        }

    }


    String showWin(Participant kid, Toy prize) {
        String winLine;
        if(prize.name.equals("ничего")){
            winLine = kid.toString() + " не выиграл ничего";
        } else {
            winLine = kid.toString() + " выиграл " + prize.name;
        }
        System.out.println(winLine);
        return winLine;
    }

    void showRoll(Participant kid, Toy prize, double roll) {
        System.out.printf("%s бросает на %.3f , это ниже шанса %.2f у %s%n", kid.name, roll, prize.getChance(), prize.name);
    }

    public void setLossWeight(double lossWeight) {
        this.lossWeight = lossWeight;
        this.lossId = this.currentToys.addToy(new Toy(lossWeight, "ничего", -1));
        cc.assignChance(currentToys);
    }

    class QuantityCalc {

        PriorityQueue<Toy> adjustQuantityLeft(Toy t, ToyList tl,PriorityQueue<Toy> currentQueue) {
            if(t.quantity > 0){
                t.quantity -= 1;
            }
            if(t.quantity == 0){
                removeStock(t.id, tl);
                Raffle.this.cc.assignChance(tl);
                currentQueue = new PriorityQueue<>(tl.toys.values());
            }
            return currentQueue;
        }

        void removeStock(int idNum, ToyList toys) {
            toys.removeToy(idNum);
        }


    }

}

class ChanceCalc {
    Random r = new Random();
    double maxChance;
    double totalWeight;

    double doRoll(){
        return r.nextDouble()*maxChance;
    }

    Toy checkPrize(PriorityQueue<Toy> prizes, double roll) throws Exception {
        // делаем копию на итерацию, чтобы очередь шансов оставалась нетронутой
        PriorityQueue<Toy> onePoll = new PriorityQueue<>(prizes);

        while(!onePoll.isEmpty()){
            Toy p = onePoll.poll();
            if(roll <= p.getChance()){
                return checkTies(onePoll,p);
            }
        }
        throw new Exception("Приз с такой вероятностью не найден");
    }

    ToyList assignChance(ToyList tl){
        this.totalWeight = 0;
        this.maxChance = 0;
        for (Toy t:tl.toys.values()){
            this.totalWeight += t.chanceWeight;
        }

        for (Toy t:tl.toys.values()){
            double ch = t.chanceWeight/totalWeight;
            t.setChance(ch);
            if(maxChance < ch ){
                maxChance = ch;
            }
        }
        return tl;
    }

    Toy checkTies(PriorityQueue<Toy> leftovers, Toy drawn  ){
        PriorityQueue<Toy> tiePoll = new PriorityQueue<>(leftovers);
        ArrayList<Toy> sameChance = new ArrayList<>();
        while(!tiePoll.isEmpty()){
            if(drawn.getChance() == tiePoll.peek().getChance()){
                sameChance.add(tiePoll.poll());
            }else {break;}
        }
        sameChance.add(drawn);
        int pickRandom = r.nextInt(sameChance.size());
        return sameChance.get(pickRandom);
    }
}

class Toy implements Comparable<Toy>{
    int id;
    static int idCount;
    double chanceWeight;
    String name;
    int quantity;

    private double chance;

    public Toy(int id, double chanceWeight, String name, int quantity) {
        this.id = id;
        this.chanceWeight = chanceWeight;
        this.name = name;
        this.quantity = quantity;
        this.chance = 0;
    }

    public Toy(double chanceWeight, String name, int quantity) {
        this(idCount++,chanceWeight,name,quantity);
    }

    public Toy(double chanceWeight, String name) {
        this(idCount++,chanceWeight,name,-1);
    }

    public void setId(int id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return  String.format("id:%d %s, вероятность %.1f, кол-во %d, категория редкости %.2f",this.id, this.name,this.chanceWeight,this.quantity,this.chance );
    }

    public double getChance() {
        return chance;
    }

    public void setChance(double chance) {
        this.chance = chance;
    }

    @Override
    public int compareTo(Toy o) {
        if(o.chance == this.chance){
            return 0;
        }
        return this.chance < o.chance ? -1 : 1;
    }
}

class ToyList {
    HashMap<Integer,Toy> toys = new HashMap<>();
    String toyFilepath;
    FileIO f = new FileIO();
    protected int maxKey;


    public ToyList(String filepath) {
        this.readFromFile(filepath);
        this.maxKey = Collections.max(toys.keySet());
    }
    public ToyList() {
        this.readFromFile("./toylist.txt");
        this.maxKey = Collections.max(toys.keySet());
    }

    void addToyList(Collection<Toy> newtoys){
        for (Toy t: newtoys){
            this.addToy(t);
        }
    }

    int addToy(Toy t){
        int finalId = t.id;
        if(toys.containsKey(t.id)){
            finalId = ++maxKey;
            t.setId(finalId);
        }
        toys.put(t.id,t);
        return finalId;
    }
    void removeToy(int idNum){
        toys.remove(idNum);
    }

    void readFromFile(String filepath){
        this.toyFilepath=filepath;
        for (String line:f.readToys(filepath)){
            String[] toyParams = line.split(" ",4);
            int toyId = Integer.parseInt(toyParams[0]);
            toys.put(toyId,new Toy(toyId,
                    Double.parseDouble(toyParams[1]),
                    toyParams[3],
                    Integer.parseInt(toyParams[2])));
        }

    }

    void saveToFile(){
        f.writeToys(this.toString());
    }

    @Override
    public String toString() {
        StringBuilder sb =new StringBuilder();
        for (Toy t: toys.values()){
            sb.append(t.toString());
            sb.append("\n");
        }
        return sb.toString();
    }
}

public class Main {
    public static void main(String[] args) {
        //инициализация списка игрушек разными методами
        ToyList toys1 = new ToyList();
        toys1.addToy(new Toy(25,"Плюшевый мишка",3));
        toys1.addToyList(List.of(
                new Toy(5,"Велосипед",1),
                new Toy(10,"Паззл",2),
                new Toy(10,"Слинки",2)
        ));
        System.out.println(toys1);
        //инициализация очереди участников
        ParticipantQueue pq = new ParticipantQueue(List.of(
                new Participant("Женя"),
                new Participant("Петя"),
                new Participant("Света"),
                new Participant("Галя"),
                new Participant("Женя"),
                new Participant("Вася"),
                new Participant("Данила"),
                new Participant("Денис"),
                new Participant("Катя"),
                new Participant("Оля")
        ));
        //сам розыгрыш
        //Вывод бросков для наглядности расчета можно сделать, раскомментировав 42-ю строчку в Raffle
        Raffle raf = new Raffle(pq,toys1);
        System.out.println(raf.currentToys.toString());
        raf.runRaffle();

        System.out.println("\nРозыгрыш с вероятностью проиграть\n");
        ParticipantQueue pqloss = new ParticipantQueue();
        for (int i = 1; i <= 10 ; i++){
            pqloss.addParticipant(new Participant());
        }
        Raffle raf2 = new Raffle(pqloss,toys1);
        raf2.setLossWeight(30);
        System.out.println(raf2.currentToys.toString());
        raf2.runRaffle();

        //Можно записать финальное состояние призов для склада
        toys1.saveToFile();
    }

}