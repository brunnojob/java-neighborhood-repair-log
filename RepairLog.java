import java.nio.file.*;
import java.time.Instant;
import java.util.*;

public class RepairLog {
    public static void main(String[] args) throws Exception {
        if(args.length<2){System.err.println("usage: java RepairLog <file> add <area> <request> | list");System.exit(2);}
        Path file=Path.of(args[0]);
        if(args[1].equals("add")) {
            if(args.length<4)throw new IllegalArgumentException("area and request required");
            String area=clean(args[2]), request=clean(String.join(" ",Arrays.copyOfRange(args,3,args.length)));
            String id=UUID.randomUUID().toString();
            Files.writeString(file,id+"\tOPEN\t"+Instant.now()+"\t"+area+"\t"+request+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
            System.out.println(id);
        } else if(args[1].equals("list")) {
            if(Files.exists(file))Files.lines(file).forEach(System.out::println);
        } else throw new IllegalArgumentException("unknown command");
    }
    static String clean(String s){return s.replace("\\","\\\\").replace("\t"," ").replace("\n"," ").trim();}
}