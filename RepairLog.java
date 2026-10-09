import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

public final class RepairLog {
  enum State {
    OPEN,
    ASSIGNED,
    IN_PROGRESS,
    RESOLVED,
    CLOSED
  }

  record Ticket(
      String id,
      int revision,
      Instant created,
      Instant updated,
      String area,
      String description,
      int priority,
      String assignee,
      State state,
      String note) {}

  record Replay(Map<String, Ticket> tickets, String hash, long events) {}

  static String encode(String value) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  static String decode(String value) {
    return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
  }

  static String text(String value, int max) {
    if (value == null
        || value.isBlank()
        || value.length() > max
        || value.chars().anyMatch(Character::isISOControl))
      throw new IllegalArgumentException("invalid text field");
    return value.trim();
  }

  static String digest(String value) throws Exception {
    return HexFormat.of()
        .formatHex(
            MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
  }

  static Replay replay(FileChannel channel) throws Exception {
    if (channel.size() > 64 * 1024 * 1024)
      throw new IllegalStateException("event log exceeds 64 MiB");
    channel.position(0);
    ByteBuffer bytes = ByteBuffer.allocate((int) channel.size());
    while (bytes.hasRemaining()) if (channel.read(bytes) < 0) break;
    String content = new String(bytes.array(), StandardCharsets.UTF_8);
    if (!content.isEmpty() && !content.endsWith("\n"))
      throw new IllegalStateException("incomplete event append");
    var tickets = new LinkedHashMap<String, Ticket>();
    String prior = "0".repeat(64);
    long events = 0;
    for (String row : content.split("\n")) {
      if (row.isEmpty()) continue;
      String[] f = row.split("\\|", -1);
      if (f.length != 12) throw new IllegalStateException("invalid event frame");
      String payload = String.join("|", Arrays.copyOf(f, 11));
      if (!f[10].equals(prior) || !f[11].equals(digest(payload)))
        throw new IllegalStateException("event integrity failure");
      Ticket previous = tickets.get(f[0]);
      int revision = Integer.parseInt(f[1]);
      if (revision != (previous == null ? 1 : previous.revision() + 1))
        throw new IllegalStateException("revision discontinuity");
      Ticket ticket =
          new Ticket(
              f[0],
              revision,
              Instant.parse(f[2]),
              Instant.parse(f[3]),
              decode(f[4]),
              decode(f[5]),
              Integer.parseInt(f[6]),
              decode(f[7]),
              State.valueOf(f[8]),
              decode(f[9]));
      tickets.put(ticket.id(), ticket);
      prior = f[11];
      events++;
    }
    return new Replay(tickets, prior, events);
  }

  static void append(FileChannel channel, Ticket t, String prior) throws Exception {
    String payload =
        String.join(
            "|",
            t.id(),
            Integer.toString(t.revision()),
            t.created().toString(),
            t.updated().toString(),
            encode(t.area()),
            encode(t.description()),
            Integer.toString(t.priority()),
            encode(t.assignee()),
            t.state().name(),
            encode(t.note()),
            prior);
    byte[] bytes = (payload + "|" + digest(payload) + "\n").getBytes(StandardCharsets.UTF_8);
    channel.position(channel.size());
    ByteBuffer buffer = ByteBuffer.wrap(bytes);
    while (buffer.hasRemaining()) channel.write(buffer);
    channel.force(true);
  }

  static Ticket transition(Ticket t, int expected, State next, String note, Instant now) {
    if (t.revision() != expected) throw new IllegalStateException("revision conflict");
    boolean allowed =
        switch (t.state()) {
          case OPEN -> next == State.ASSIGNED;
          case ASSIGNED -> next == State.IN_PROGRESS || next == State.OPEN;
          case IN_PROGRESS -> next == State.RESOLVED || next == State.ASSIGNED;
          case RESOLVED -> next == State.CLOSED || next == State.IN_PROGRESS;
          case CLOSED -> next == State.OPEN;
        };
    if (!allowed) throw new IllegalStateException("invalid state transition");
    if (t.assignee().isBlank() && next == State.ASSIGNED)
      throw new IllegalStateException("assign an operator first");
    return new Ticket(
        t.id(),
        t.revision() + 1,
        t.created(),
        now,
        t.area(),
        t.description(),
        t.priority(),
        t.assignee(),
        next,
        text(note, 1000));
  }

  static String json(String value) {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
  }

  static void report(Replay replay) {
    var tickets = new ArrayList<>(replay.tickets().values());
    tickets.sort(
        Comparator.comparingInt(Ticket::priority).reversed().thenComparing(Ticket::created));
    System.out.print("{\"events\":" + replay.events() + ",\"tickets\":[");
    for (int i = 0; i < tickets.size(); i++) {
      Ticket t = tickets.get(i);
      if (i > 0) System.out.print(",");
      System.out.print(
          "{\"id\":"
              + json(t.id())
              + ",\"revision\":"
              + t.revision()
              + ",\"area\":"
              + json(t.area())
              + ",\"description\":"
              + json(t.description())
              + ",\"priority\":"
              + t.priority()
              + ",\"state\":"
              + json(t.state().name())
              + ",\"assignee\":"
              + json(t.assignee())
              + ",\"updated\":"
              + json(t.updated().toString())
              + "}");
    }
    System.out.println("]}");
  }

  public static void main(String[] args) throws Exception {
    if (args.length < 2)
      throw new IllegalArgumentException(
          "RepairLog file add area priority description | assign id revision operator | transition"
              + " id revision state note | report");
    try (FileChannel channel =
            FileChannel.open(
                Path.of(args[0]),
                StandardOpenOption.CREATE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE);
        var lock = channel.lock()) {
      if (!lock.isValid()) throw new IllegalStateException("file lock unavailable");
      Replay replay = replay(channel);
      Ticket ticket;
      Instant now = Instant.now();
      if (args[1].equals("report") || args[1].equals("list")) {
        report(replay);
        return;
      }
      if (args[1].equals("add")) {
        if (args.length != 5)
          throw new IllegalArgumentException("area, priority and description required");
        int priority = Integer.parseInt(args[3]);
        if (priority < 1 || priority > 5)
          throw new IllegalArgumentException("priority must be 1..5");
        ticket =
            new Ticket(
                UUID.randomUUID().toString(),
                1,
                now,
                now,
                text(args[2], 80),
                text(args[4], 1000),
                priority,
                "",
                State.OPEN,
                "created");
      } else {
        if (args.length < 5)
          throw new IllegalArgumentException("id and expected revision required");
        Ticket previous =
            Optional.ofNullable(replay.tickets().get(args[2]))
                .orElseThrow(() -> new IllegalArgumentException("ticket not found"));
        int revision = Integer.parseInt(args[3]);
        if (revision != previous.revision()) throw new IllegalStateException("revision conflict");
        if (args[1].equals("assign")) {
          if (previous.state() == State.CLOSED || previous.state() == State.RESOLVED)
            throw new IllegalStateException("ticket already resolved");
          ticket =
              new Ticket(
                  previous.id(),
                  revision + 1,
                  previous.created(),
                  now,
                  previous.area(),
                  previous.description(),
                  previous.priority(),
                  text(args[4], 80),
                  State.ASSIGNED,
                  "operator assigned");
        } else if (args[1].equals("transition") && args.length == 6) {
          ticket = transition(previous, revision, State.valueOf(args[4]), args[5], now);
        } else throw new IllegalArgumentException("unknown command");
      }
      append(channel, ticket, replay.hash());
      report(replay(channel));
    }
  }
}
