package com.example.cricket;

import com.example.cricket.Models.*;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class MatchService {
    private static final String[] FIRST = {"Arjun","Rohan","Vikram","Karan","Aman","Sahil","Nikhil","Dev","Rahul","Ishan","Manav","Tarun"};
    private static final String[] LAST  = {"Sharma","Verma","Patil","Nair","Reddy","Khan","Iyer","Singh","Das","Joshi","Kulkarni","Mehta"};
    // outcome label -> value in [0,100) used by the simulator's probability table
    private static final Map<String, Integer> OUTCOMES =
            Map.of("Wd", 0, "W", 5, "0", 10, "1", 50, "2", 70, "3", 78, "4", 85, "6", 95);

    private final Map<Long, Match> matches = new ConcurrentSkipListMap<>();
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final AtomicLong seq = new AtomicLong();
    private final Random rnd = new Random();

    @PostConstruct
    void seed() {
        create("Pune Panthers", "Delhi Dynamos", 10);
        create("Chennai Challengers", "Kolkata Knights", 10);
        create("Jaipur Jaguars", "Mumbai Mariners", 5);
    }

    // ---------- queries ----------
    public List<Match> all() { return new ArrayList<>(matches.values()); }

    public Match get(long id) {
        Match m = matches.get(id);
        if (m == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Match " + id + " not found");
        return m;
    }

    public Map<String, Object> summary(long id) {
        Match m = get(id);
        synchronized (m) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("title", m.title);
            s.put("status", m.status);
            s.put("result", m.result);
            List<String> scores = new ArrayList<>();
            Batter topBat = null; Bowler topBowl = null;
            for (Innings in : m.innings) {
                scores.add(in.battingTeam + " " + in.runs + "/" + in.wickets + " (" + in.getOvers() + " ov)");
                for (Batter b : in.batters) if (topBat == null || b.runs > topBat.runs) topBat = b;
                for (Bowler b : in.bowlers)
                    if (topBowl == null || b.wickets > topBowl.wickets
                            || (b.wickets == topBowl.wickets && b.balls > 0 && b.runs < topBowl.runs)) topBowl = b;
            }
            s.put("scores", scores);
            if (topBat != null) s.put("topBatter", topBat.name + " " + topBat.runs + " (" + topBat.balls + ")");
            if (topBowl != null) s.put("bestBowler", topBowl.name + " " + topBowl.wickets + "/" + topBowl.runs + " (" + topBowl.getOvers() + " ov)");
            return s;
        }
    }

    // ---------- commands ----------
    public Match create(String a, String b, int overs) {
        Match m = new Match();
        m.id = seq.incrementAndGet();
        m.teamA = a; m.teamB = b; m.title = a + " vs " + b; m.totalOvers = overs;
        m.innings.add(newInnings(a, b));
        m.commentary.add("Match started. " + a + " bat first.");
        matches.put(m.id, m);
        return m;
    }

    /** Manual scoring: outcome is one of Wd, W, 0, 1, 2, 3, 4, 6. */
    public Match manualBall(long id, String outcome) {
        Integer r = OUTCOMES.get(outcome);
        if (r == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "outcome must be one of " + OUTCOMES.keySet());
        Match m = get(id);
        if (!"LIVE".equals(m.status)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Match is completed");
        ball(m, r);
        return m;
    }

    // ---------- live simulation ----------
    @Scheduled(fixedRate = 1500)
    public void tick() {
        for (Match m : matches.values()) if ("LIVE".equals(m.status)) ball(m, rnd.nextInt(100));
    }

    private void ball(Match m, int r) {
        synchronized (m) {
            if (!"LIVE".equals(m.status)) return;
            Innings in = m.innings.get(m.currentInnings);
            Batter bat = in.batters.get(in.striker);
            Bowler bow = in.bowlers.get(in.bowler);
            String sym, text;
            boolean legal = true;

            if (r < 3) { // wide
                legal = false;
                in.runs++; in.extras++; bow.runs++;
                sym = "Wd"; text = "Wide";
            } else {
                in.balls++; bow.balls++; bat.balls++;
                if (r < 9) { // wicket
                    sym = "W"; text = "OUT! " + bat.name + " b " + bow.name;
                    bat.status = "out"; bat.dismissal = "b " + bow.name;
                    bow.wickets++; in.wickets++;
                    if (in.wickets < 10) { in.striker = in.next++; in.batters.get(in.striker).status = "batting"; }
                } else {
                    int run = r < 42 ? 0 : r < 68 ? 1 : r < 77 ? 2 : r < 79 ? 3 : r < 91 ? 4 : 6;
                    in.runs += run; bat.runs += run; bow.runs += run;
                    if (run == 4) bat.fours++;
                    if (run == 6) bat.sixes++;
                    sym = String.valueOf(run);
                    text = run == 0 ? "Dot ball" : run == 4 ? "FOUR!" : run == 6 ? "SIX!" : run + (run == 1 ? " run" : " runs");
                    if (run % 2 == 1) swap(in);
                }
            }
            String label = in.getOvers() + "  " + bow.name + " to " + bat.name + ": " + text;
            if (legal && in.balls % 6 == 0) {
                swap(in);
                in.bowler = (in.bowler + 1) % in.bowlers.size();
                label += "  | End of over " + in.balls / 6;
            }
            m.recent.add(sym);
            if (m.recent.size() > 12) m.recent.remove(0);
            m.commentary.add(0, label);
            if (m.commentary.size() > 40) m.commentary.remove(m.commentary.size() - 1);

            boolean inningsOver = in.wickets >= 10 || in.balls >= m.totalOvers * 6
                    || (m.currentInnings == 1 && in.runs >= m.target);
            if (inningsOver) {
                if (m.currentInnings == 0) {
                    m.target = in.runs + 1;
                    m.currentInnings = 1;
                    m.innings.add(newInnings(m.teamB, m.teamA));
                    m.recent.clear();
                    m.commentary.add(0, "Innings break. " + m.teamB + " need " + m.target + " to win.");
                } else {
                    finish(m, in);
                }
            }
            broadcast(m);
        }
    }

    private void finish(Match m, Innings in) {
        m.status = "COMPLETED";
        int first = m.target - 1;
        if (in.runs >= m.target) m.result = in.battingTeam + " won by " + (10 - in.wickets) + " wickets";
        else if (in.runs == first) m.result = "Match tied";
        else m.result = in.bowlingTeam + " won by " + (first - in.runs) + " runs";
        m.commentary.add(0, "Result: " + m.result);
    }

    private void swap(Innings in) { int t = in.striker; in.striker = in.nonStriker; in.nonStriker = t; }

    private Innings newInnings(String bat, String bowl) {
        Innings in = new Innings();
        in.battingTeam = bat; in.bowlingTeam = bowl;
        for (String p : players(bat)) in.batters.add(new Batter(p));
        List<String> bp = players(bowl);
        for (int i = 6; i < 11; i++) in.bowlers.add(new Bowler(bp.get(i)));
        in.batters.get(0).status = "batting";
        in.batters.get(1).status = "batting";
        return in;
    }

    private List<String> players(String team) {
        int t = Math.abs(team.hashCode());
        List<String> l = new ArrayList<>();
        for (int i = 0; i < 11; i++) l.add(FIRST[(t + i * 5) % 12] + " " + LAST[(t / 7 + i * 7) % 12]);
        return l;
    }

    // ---------- server-sent events ----------
    public SseEmitter subscribe() {
        SseEmitter e = new SseEmitter(0L);
        emitters.add(e);
        e.onCompletion(() -> emitters.remove(e));
        e.onTimeout(() -> emitters.remove(e));
        e.onError(x -> emitters.remove(e));
        return e;
    }

    private void broadcast(Match m) {
        for (SseEmitter e : emitters) {
            try { e.send(SseEmitter.event().name("update").data(m, MediaType.APPLICATION_JSON)); }
            catch (Exception ex) { emitters.remove(e); }
        }
    }
}
