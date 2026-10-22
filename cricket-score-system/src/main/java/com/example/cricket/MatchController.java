package com.example.cricket;

import com.example.cricket.Models.*;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class MatchController {
    private final MatchService svc;
    public MatchController(MatchService svc) { this.svc = svc; }

    @GetMapping("/matches")
    public List<Match> all() { return svc.all(); }

    @GetMapping("/matches/{id}")
    public Match one(@PathVariable long id) { return svc.get(id); }

    @GetMapping("/matches/{id}/scorecard")
    public List<Innings> scorecard(@PathVariable long id) { return svc.get(id).innings; }

    @GetMapping("/matches/{id}/summary")
    public Map<String, Object> summary(@PathVariable long id) { return svc.summary(id); }

    @PostMapping("/matches")
    @ResponseStatus(HttpStatus.CREATED)
    public Match create(@RequestParam String teamA, @RequestParam String teamB,
                        @RequestParam(defaultValue = "5") int overs) {
        if (teamA.isBlank() || teamB.isBlank() || teamA.equalsIgnoreCase(teamB) || overs < 1 || overs > 20)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Need two different team names and 1-20 overs");
        return svc.create(teamA.trim(), teamB.trim(), overs);
    }

    /** Manual score update: POST /api/matches/1/ball?outcome=4 */
    @PostMapping("/matches/{id}/ball")
    public Match ball(@PathVariable long id, @RequestParam String outcome) { return svc.manualBall(id, outcome); }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() { return svc.subscribe(); }
}
