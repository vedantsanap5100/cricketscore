package com.example.cricket;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Domain model. Only public fields/getters are serialized to JSON. */
public class Models {

    public static class Batter {
        public String name, status = "yet to bat", dismissal = "";
        public int runs, balls, fours, sixes;
        Batter(String name) { this.name = name; }
        public double getStrikeRate() { return balls == 0 ? 0 : Math.round(runs * 10000.0 / balls) / 100.0; }
    }

    public static class Bowler {
        public String name;
        public int balls, runs, wickets;
        Bowler(String name) { this.name = name; }
        public String getOvers() { return balls / 6 + "." + balls % 6; }
        public double getEconomy() { return balls == 0 ? 0 : Math.round(runs * 600.0 / balls) / 100.0; }
    }

    public static class Innings {
        public String battingTeam, bowlingTeam;
        public int runs, wickets, balls, extras;
        public List<Batter> batters = new CopyOnWriteArrayList<>();
        public List<Bowler> bowlers = new CopyOnWriteArrayList<>();
        int striker = 0, nonStriker = 1, next = 2, bowler = 0; // internal state, not serialized
        public String getOvers() { return balls / 6 + "." + balls % 6; }
        public double getRunRate() { return balls == 0 ? 0 : Math.round(runs * 600.0 / balls) / 100.0; }
        public String getStriker() { return batters.get(striker).name; }
        public String getBowler() { return bowlers.get(bowler).name; }
    }

    public static class Match {
        public long id;
        public String title, teamA, teamB, status = "LIVE", result = "";
        public int totalOvers, currentInnings = 0;
        public Integer target;
        public List<Innings> innings = new CopyOnWriteArrayList<>();
        public List<String> recent = new CopyOnWriteArrayList<>();      // last 12 deliveries
        public List<String> commentary = new CopyOnWriteArrayList<>();  // newest first
    }
}
